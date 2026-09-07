import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { getTokens } from '../api/client';

export interface RealtimeEvent {
  eventId: string;
  eventType: string;
  aggregateId: string;
  payload: string;
  revision: number;
  timestamp: string;
}

export function useWebSocket(boardId: string | null, onEvent: (e: RealtimeEvent) => void) {
  const [status, setStatus] = useState<'disconnected' | 'connecting' | 'synced'>('disconnected');
  const [lastRevision, setLastRevision] = useState(0);
  const handler = useRef(onEvent);
  handler.current = onEvent;
  const beatRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (!boardId) return;
    const tokens = getTokens();
    if (!tokens) return;
    setStatus('connecting');
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws') as unknown as WebSocket,
      connectHeaders: { Authorization: `Bearer ${tokens.accessToken}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setStatus('synced');
        client.subscribe(`/topic/boards/${boardId}`, (msg) => {
          try {
            const evt = JSON.parse(msg.body) as RealtimeEvent;
            setLastRevision((prev) => {
              if (evt.revision <= prev) return prev; // dedupe
              if (evt.revision > prev + 1) {
                // revision gap: caller should refetch snapshot
                window.dispatchEvent(new CustomEvent('orbitflow:resync'));
              }
              return evt.revision;
            });
            handler.current(evt);
          } catch {
            // ignore malformed frames
          }
        });
        // presence heartbeat
        beatRef.current = setInterval(() => {
          client.publish({ destination: '/app/presence/heartbeat', body: JSON.stringify({ boardId }) });
        }, 30000);
      },
      onDisconnect: () => setStatus('disconnected'),
      onStompError: () => setStatus('disconnected'),
    });
    client.activate();
    return () => {
      if (beatRef.current) {
        clearInterval(beatRef.current);
        beatRef.current = null;
      }
      void client.deactivate();
      setStatus('disconnected');
    };
  }, [boardId]);

  return { status, lastRevision };
}
