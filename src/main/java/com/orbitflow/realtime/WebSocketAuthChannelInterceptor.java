package com.orbitflow.realtime;

import com.orbitflow.board.Board;
import com.orbitflow.board.BoardRepository;
import com.orbitflow.common.security.JwtProvider;
import com.orbitflow.project.Project;
import com.orbitflow.project.ProjectService;
import com.orbitflow.workspace.WorkspaceMembershipRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/** Validates JWT + workspace/project authorization on CONNECT and SUBSCRIBE. */
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
    private final JwtProvider jwt;
    private final WorkspaceMembershipRepository workspaceMembers;
    private final BoardRepository boards;
    private final ProjectService projectService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null) return message;
        if (StompCommand.CONNECT.equals(acc.getCommand())) {
            String auth = acc.getFirstNativeHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                if (jwt.isValid(token) && jwt.isAccessToken(token)) {
                    UUID userId = jwt.userId(token);
                    acc.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null, java.util.List.of()));
                } else {
                    throw new IllegalArgumentException("Invalid JWT");
                }
            } else {
                throw new IllegalArgumentException("Missing Authorization header");
            }
        }
        if (StompCommand.SUBSCRIBE.equals(acc.getCommand())) {
            Object principal = acc.getUser() != null ? acc.getUser().getName()
                    : (acc.getSessionAttributes() != null ? acc.getSessionAttributes().get("userId") : null);
            String dest = acc.getDestination();
            if (dest != null && principal != null) {
                try {
                    UUID userId = UUID.fromString(principal.toString());
                    authorizeSubscription(userId, dest);
                } catch (IllegalArgumentException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Subscription not authorized");
                }
            }
        }
        return message;
    }

    private void authorizeSubscription(UUID userId, String dest) {
        // /topic/boards/{boardId} -> project access; /topic/projects/{id}; /topic/workspaces/{id} -> membership
        String[] parts = dest.split("/");
        if (dest.startsWith("/topic/boards/") && parts.length >= 4) {
            UUID boardId = UUID.fromString(parts[3]);
            Board b = boards.findById(boardId).orElseThrow(() -> new IllegalArgumentException("Board not found"));
            Project p = b.getProject();
            projectService.requireProjectAccess(userId, p);
            return;
        }
        if (dest.startsWith("/topic/projects/") && parts.length >= 4) {
            UUID projectId = UUID.fromString(parts[3]);
            var pm = projectService;
            // will throw if no access
            var proj = pm; // placeholder to keep check centralized
            // load via repository-free path: use workspace check through projectService helpers
            // We resolve by attempting Board lookup fallback: rely on projectService.requireProjectAccess via a lightweight fetch
            // (BoardRepository cannot load project directly here, so delegate:)
            throwIfNoProjectAccess(userId, projectId);
            return;
        }
        if (dest.startsWith("/topic/workspaces/") && parts.length >= 4) {
            UUID wsId = UUID.fromString(parts[3]);
            var m = workspaceMembers.findByWorkspaceIdAndUserId(wsId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("No workspace access"));
            if (!"ACTIVE".equals(m.getStatus())) throw new IllegalArgumentException("Membership inactive");
            return;
        }
        if (dest.startsWith("/user/queue/")) return; // user-scoped, authenticated above
        throw new IllegalArgumentException("Unknown destination: " + dest);
    }

    private void throwIfNoProjectAccess(UUID userId, UUID projectId) {
        // Resolve project through boards? Use direct check via projectService by loading minimal state.
        // projectService.requireProjectAccess needs a Project entity; fetch board-agnostically:
        // Defer to a helper bean to avoid circularity — implemented via ApplicationContext lookup:
        ProjectAccessChecker.check(userId, projectId);
    }

    @Component
    @RequiredArgsConstructor
    public static class ProjectAccessChecker {
        private static ProjectService staticService;
        private static com.orbitflow.project.ProjectRepository staticRepo;
        private final ProjectService svc;
        private final com.orbitflow.project.ProjectRepository repo;

        @jakarta.annotation.PostConstruct
        void init() {
            staticService = svc;
            staticRepo = repo;
        }

        static void check(UUID userId, UUID projectId) {
            Project p = staticRepo.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found"));
            try {
                staticService.requireProjectAccess(userId, p);
            } catch (Exception e) {
                throw new IllegalArgumentException("No project access");
            }
        }
    }
}
