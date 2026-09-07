package com.orbitflow.realtime;

import com.orbitflow.common.security.JwtProvider;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WebSocketController {
    private final PresenceService presence;
    private final JwtProvider jwt;

    @MessageMapping("/presence/heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor headers, Map<String, String> body) {
        try {
            UUID boardId = UUID.fromString(body.get("boardId"));
            UUID userId = currentUser(headers);
            if (userId != null) presence.heartbeat(boardId, userId);
        } catch (Exception ignored) {}
    }

    @MessageMapping("/presence/leave")
    public void leave(SimpMessageHeaderAccessor headers, Map<String, String> body) {
        try {
            UUID boardId = UUID.fromString(body.get("boardId"));
            UUID userId = currentUser(headers);
            if (userId != null) presence.leave(boardId, userId);
        } catch (Exception ignored) {}
    }

    private UUID currentUser(SimpMessageHeaderAccessor headers) {
        try {
            if (headers.getUser() != null) return UUID.fromString(headers.getUser().getName());
        } catch (Exception ignored) {}
        return null;
    }

    // REST accessors for tests/UI
    @org.springframework.web.bind.annotation.GetMapping("/api/v1/boards/{boardId}/presence")
    @org.springframework.web.bind.annotation.ResponseBody
    public Map<String, Object> viewers(@org.springframework.web.bind.annotation.PathVariable UUID boardId) {
        Set<UUID> ids = presence.viewers(boardId);
        return Map.of("boardId", boardId.toString(), "viewers", ids.stream().map(UUID::toString).toList(), "count", ids.size());
    }
}
