package com.orbitflow.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    List<Notification> findByRecipientIdAndStatus(UUID recipientId, String status);
    Optional<Notification> findByDedupeKey(String dedupeKey);
    long countByRecipientIdAndReadAtIsNull(UUID recipientId);
}
