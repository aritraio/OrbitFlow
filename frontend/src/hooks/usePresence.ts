import { useEffect, useState } from 'react';
import { api } from '../api/client';

export function usePresence(boardId: string | null) {
  const [viewers, setViewers] = useState<string[]>([]);
  useEffect(() => {
    if (!boardId) return;
    let alive = true;
    const load = async () => {
      try {
        const data = await api<{ viewers: string[] }>(`/api/v1/boards/${boardId}/presence`);
        if (alive) setViewers(data.viewers);
      } catch {
        // presence is best-effort; board remains usable
      }
    };
    void load();
    const id = setInterval(load, 15000);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, [boardId]);
  return viewers;
}
