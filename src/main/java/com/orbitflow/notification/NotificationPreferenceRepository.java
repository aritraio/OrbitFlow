package com.orbitflow.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    List<NotificationPreference> findByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);
    Optional<NotificationPreference> findByUserIdAndWorkspaceIdAndEventType(UUID userId, UUID workspaceId, String eventType);
}
