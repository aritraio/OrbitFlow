package com.orbitflow.notification;

import com.orbitflow.common.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    public record NotificationDto(UUID id, String eventType, String title, String body,
                                  String resourceType, UUID resourceId, String status, String createdAt) {}

    @GetMapping
    public List<NotificationDto> list(@AuthenticationPrincipal UserPrincipal p) {
        return service.listForUser(p.getId()).stream()
                .map(n -> new NotificationDto(n.getId(), n.getEventType(), n.getTitle(), n.getBody(),
                        n.getResourceType(), n.getResourceId(), n.getStatus(), n.getCreatedAt().toString()))
                .toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Object> unread(@AuthenticationPrincipal UserPrincipal p) {
        return Map.of("unread", service.unreadCount(p.getId()));
    }

    @PostMapping("/read")
    public Map<String, Object> readAll(@AuthenticationPrincipal UserPrincipal p) {
        service.markAllRead(p.getId());
        return Map.of("ok", true);
    }
}
