package com.orbitflow.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox: services call {@link #emit} inside the same DB transaction
 * as the domain mutation. {@link #poll} dispatches PENDING rows asynchronously
 * (WebSocket broadcast, notifications, search indexing hooks via Spring events).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {
    private final OutboxRepository repo;
    private final ApplicationEventPublisher events;

    public record DispatchedEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, String payloadJson) {}

    @Transactional
    public OutboxEvent emit(String aggregateType, UUID aggregateId, String eventType, String payloadJson) {
        OutboxEvent e = OutboxEvent.builder()
                .aggregateType(aggregateType).aggregateId(aggregateId)
                .eventType(eventType).payloadJson(payloadJson == null ? "{}" : payloadJson)
                .build();
        return repo.save(e);
    }

    /** Poller: SELECT FOR UPDATE SKIP LOCKED semantics via repository; H2 falls back gracefully. */
    @Scheduled(fixedDelayString = "${orbitflow.outbox.poll-ms:2000}")
    @Transactional
    public void poll() {
        List<OutboxEvent> batch;
        try {
            batch = repo.claimPending(50);
        } catch (Exception ex) {
            // H2 does not support SKIP LOCKED in all modes — fallback to simple query
            batch = repo.findByStatusOrderByCreatedAtAsc("PENDING").stream().limit(50).toList();
        }
        for (OutboxEvent e : batch) {
            try {
                events.publishEvent(new DispatchedEvent(e.getId(), e.getAggregateType(), e.getAggregateId(), e.getEventType(), e.getPayloadJson()));
                e.setStatus("PROCESSED");
                e.setProcessedAt(Instant.now());
                repo.save(e);
            } catch (Exception ex) {
                log.warn("Outbox dispatch failed for {}: {}", e.getId(), ex.toString());
                e.setAttempts(e.getAttempts() + 1);
                e.setNextAttemptAt(Instant.now().plusSeconds(Math.min(300, (long) Math.pow(2, e.getAttempts()) * 5)));
                if (e.getAttempts() >= 10) e.setStatus("DEAD_LETTER");
                else e.setStatus("PENDING");
                repo.save(e);
            }
        }
    }

    /** Synchronous drain used by integration tests (deterministic, no waiting on scheduler). */
    @Transactional
    public int drain() {
        List<OutboxEvent> pending = repo.findByStatusOrderByCreatedAtAsc("PENDING");
        for (OutboxEvent e : pending) {
            try {
                events.publishEvent(new DispatchedEvent(e.getId(), e.getAggregateType(), e.getAggregateId(), e.getEventType(), e.getPayloadJson()));
                e.setStatus("PROCESSED");
                e.setProcessedAt(Instant.now());
            } catch (Exception ex) {
                e.setAttempts(e.getAttempts() + 1);
                if (e.getAttempts() >= 10) e.setStatus("DEAD_LETTER");
            }
            repo.save(e);
        }
        return pending.size();
    }
}
