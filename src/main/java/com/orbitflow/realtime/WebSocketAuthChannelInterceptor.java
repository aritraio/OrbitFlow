package com.orbitflow.realtime;

import com.orbitflow.board.Board;
import com.orbitflow.board.BoardRepository;
import com.orbitflow.common.security.JwtProvider;
import com.orbitflow.project.Project;
import com.orbitflow.project.ProjectRepository;
import com.orbitflow.project.ProjectService;
import com.orbitflow.workspace.WorkspaceMembershipRepository;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/** Validates JWT + workspace/project authorization on CONNECT and SUBSCRIBE. */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
    private final JwtProvider jwt;
    private final WorkspaceMembershipRepository workspaceMembers;
    private final BoardRepository boards;
    private final ProjectRepository projects;
    private final ProjectService projectService;

    public WebSocketAuthChannelInterceptor(
            JwtProvider jwt,
            WorkspaceMembershipRepository workspaceMembers,
            BoardRepository boards,
            ProjectRepository projects,
            @org.springframework.context.annotation.Lazy ProjectService projectService) {
        this.jwt = jwt;
        this.workspaceMembers = workspaceMembers;
        this.boards = boards;
        this.projects = projects;
        this.projectService = projectService;
    }

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
            checkProjectAccess(userId, projectId);
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

    private void checkProjectAccess(UUID userId, UUID projectId) {
        Project p = projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found"));
        try {
            projectService.requireProjectAccess(userId, p);
        } catch (Exception e) {
            throw new IllegalArgumentException("No project access");
        }
    }
}
