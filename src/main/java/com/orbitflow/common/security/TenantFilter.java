package com.orbitflow.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the active workspace from header {@code X-Workspace-Id} or path variables
 * and stores it in {@link TenantContext} for tenant-aware service checks.
 */
@Component
@Order(2)
public class TenantFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String wsHeader = request.getHeader("X-Workspace-Id");
            UUID wsId = null;
            if (wsHeader != null && !wsHeader.isBlank()) {
                try { wsId = UUID.fromString(wsHeader.trim()); } catch (IllegalArgumentException ignored) {}
            }
            UUID userId = currentUserId();
            if (wsId != null || userId != null) {
                TenantContext.set(wsId, userId);
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID currentUserId() {
        try {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.getPrincipal() instanceof UserPrincipal p) return p.getId();
        } catch (Exception ignored) {}
        return null;
    }
}
