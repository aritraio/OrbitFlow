package com.orbitflow.notification;

import com.orbitflow.identity.User;
import com.orbitflow.identity.UserRepository;
import com.orbitflow.workspace.Workspace;
import com.orbitflow.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preference-aware, deduplicated notification fan-out.
 * Dedupe key: eventType + resourceId + recipientId (+ mention/user scope).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final UserRepository users;
    private final WorkspaceRepository workspaces;
    private final MailService mailService;

    @Transactional
    public List<Notification> notifyUsers(UUID workspaceId, Set<UUID> recipientIds,
                                          String eventType, String title, String body,
                                          String resourceType, UUID resourceId) {
        List<Notification> created = new ArrayList<>();
        Workspace ws = workspaces.getReferenceById(workspaceId);
        // Deduplicate across mention+assignee+watcher reasons: one row per (event, resource, recipient)
        Set<UUID> unique = new LinkedHashSet<>(recipientIds);
        for (UUID recipientId : unique) {
            String dedupe = eventType + ":" + resourceId + ":" + recipientId;
            if (notifications.findByDedupeKey(dedupe).isPresent()) continue;
            if (!wantsInApp(recipientId, workspaceId, eventType) && !wantsEmail(recipientId, workspaceId, eventType))
                continue;
            User recipient = users.getReferenceById(recipientId);
            Notification n = Notification.builder()
                    .workspace(ws).recipient(recipient)
                    .eventType(eventType).title(title).body(body)
                    .resourceType(resourceType).resourceId(resourceId)
                    .dedupeKey(dedupe).status("DELIVERED").deliveredAt(Instant.now())
                    .build();
            notifications.save(n);
            created.add(n);
            if (wantsEmail(recipientId, workspaceId, eventType)) {
                try {
                    User u = users.findById(recipientId).orElse(null);
                    if (u != null) mailService.sendEmail(u.getEmail(), "[OrbitFlow] " + title, body == null ? title : body);
                } catch (Exception ignored) {}
            }
        }
        return created;
    }

    public boolean wantsInApp(UUID userId, UUID workspaceId, String eventType) {
        return preferences.findByUserIdAndWorkspaceIdAndEventType(userId, workspaceId, eventType)
                .map(NotificationPreference::isInAppEnabled).orElse(true);
    }

    public boolean wantsEmail(UUID userId, UUID workspaceId, String eventType) {
        return preferences.findByUserIdAndWorkspaceIdAndEventType(userId, workspaceId, eventType)
                .map(NotificationPreference::isEmailEnabled).orElse(true);
    }

    @Transactional(readOnly = true)
    public List<Notification> listForUser(UUID userId) {
        return notifications.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        for (Notification n : notifications.findByRecipientIdAndStatus(userId, "DELIVERED")) {
            n.setStatus("READ");
            n.setReadAt(Instant.now());
            notifications.save(n);
        }
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByRecipientIdAndReadAtIsNull(userId);
    }
}
