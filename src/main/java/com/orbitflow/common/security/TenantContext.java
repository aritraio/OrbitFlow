package com.orbitflow.common.security;

public final class TenantContext {
    private static final ThreadLocal<java.util.UUID> WORKSPACE = new ThreadLocal<>();
    private static final ThreadLocal<java.util.UUID> USER = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(java.util.UUID workspaceId, java.util.UUID userId) {
        WORKSPACE.set(workspaceId);
        USER.set(userId);
    }

    public static void setWorkspaceId(java.util.UUID workspaceId) {
        WORKSPACE.set(workspaceId);
    }

    public static java.util.UUID getWorkspaceId() {
        return WORKSPACE.get();
    }

    public static java.util.UUID getUserId() {
        return USER.get();
    }

    public static void clear() {
        WORKSPACE.remove();
        USER.remove();
    }
}
