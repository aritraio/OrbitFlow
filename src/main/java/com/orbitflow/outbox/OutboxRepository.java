package com.orbitflow.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);

    @Query(value = "SELECT * FROM outbox_events WHERE status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP) ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> claimPending(int limit);

    long countByStatus(String status);
}
