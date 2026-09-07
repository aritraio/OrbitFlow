package com.orbitflow.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "outbox_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OutboxEvent {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING";

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;
}
