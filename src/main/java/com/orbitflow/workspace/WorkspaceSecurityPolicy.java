package com.orbitflow.workspace;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Centralized authorization policy for workspace operations.
 * Controllers, WebSocket interceptors and background jobs must use this.
 */
@Component
public class WorkspaceSecurityPolicy {

    public enum Role { OWNER, ADMIN, MEMBER, GUEST }

    public static int rank(String role) {
        return switch (role == null ? "" : role) {
            case "OWNER" -> 4;
            case "ADMIN" -> 3;
            case "MEMBER" -> 2;
            case "GUEST" -> 1;
            default -> 0;
        };
    }

    public boolean canInvite(String role) { return rank(role) >= rank("ADMIN"); }
    public boolean canCreateProject(String role) { return rank(role) >= rank("ADMIN"); }
    public boolean canConfigureWorkflow(String role, String projectRole) {
        return rank(role) >= rank("ADMIN") || "MANAGER".equals(projectRole);
    }
    public boolean canCreateTask(String role, String projectRole) {
        if (rank(role) >= rank("MEMBER")) return true;
        return rank(role) >= rank("GUEST") && projectRole != null;
    }
    public boolean canMoveTask(String role, String projectRole) { return canCreateTask(role, projectRole); }
    public boolean canDeleteTask(String role, String projectRole, boolean isOwn) {
        if (rank(role) >= rank("ADMIN") || "MANAGER".equals(projectRole)) return true;
        return isOwn && rank(role) >= rank("MEMBER");
    }
    public boolean canManageMembers(String role) { return rank(role) >= rank("ADMIN"); }
    public boolean canDeleteWorkspace(String role) { return "OWNER".equals(role); }

    public void requireRole(String actual, String minimum) {
        if (rank(actual) < rank(minimum)) {
            throw new com.orbitflow.common.exception.ForbiddenOperationException(
                "Requires role " + minimum + " but was " + actual);
        }
    }
}
