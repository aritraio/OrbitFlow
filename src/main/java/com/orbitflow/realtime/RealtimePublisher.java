package com.orbitflow.realtime;

import com.orbitflow.outbox.OutboxService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Publishes outbox domain events to STOMP topics with monotonically increasing revision IDs.
 * Also records a copy for test verification (no external broker needed).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimePublisher {
    private final SimpMessagingTemplate messaging;
    private final AtomicLong revision = new AtomicLong(0);
    private final java.util.List<Map<String, Object>> published = new java.util.concurrent.CopyOnWriteArrayList<>();

    @EventListener
    @Async("applicationTaskExecutor")
    public void onOutbox(OutboxService.DispatchedEvent event) {
        long rev = revision.incrementAndGet();
        Map<String, Object> payload = Map.of(
                "eventId", event.id().toString(),
                "aggregateType", event.aggregateType(),
                "aggregateId", event.aggregateId().toString(),
                "eventType", event.eventType(),
                "payload", event.payloadJson(),
                "revision", rev,
                "timestamp", Instant.now().toString());
        published.add(payload);
        try {
            String payloadJson = event.payloadJson();
            // Route Task events to board topic when boardId present
            if (payloadJson.contains("boardId")) {
                String boardId = extract(payloadJson, "boardId");
                if (boardId != null) messaging.convertAndSend("/topic/boards/" + boardId, payload);
            }
            if (payloadJson.contains("projectId")) {
                String projectId = extract(payloadJson, "projectId");
                if (projectId != null) messaging.convertAndSend("/topic/projects/" + projectId, payload);
            }
            if (payloadJson.contains("workspaceId")) {
                String wsId = extract(payloadJson, "workspaceId");
                if (wsId != null) messaging.convertAndSend("/topic/workspaces/" + wsId, payload);
            }
        } catch (Exception e) {
            log.debug("Realtime publish skipped (no subscribers): {}", e.toString());
        }
    }

    private static String extract(String json, String key) {
        String token = "\"" + key + "\":\"";
        int i = json.indexOf(token);
        if (i < 0) return null;
        int j = json.indexOf("\"", i + token.length());
        return j > 0 ? json.substring(i + token.length(), j) : null;
    }

    public java.util.List<Map<String, Object>> getPublished() { return java.util.List.copyOf(published); }
    public void clear() { published.clear(); }
    public long currentRevision() { return revision.get(); }
}
