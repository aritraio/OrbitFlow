package com.orbitflow.workspace;

import com.orbitflow.common.exception.*;
import com.orbitflow.common.util.SlugUtils;
import com.orbitflow.identity.User;
import com.orbitflow.identity.UserRepository;
import com.orbitflow.outbox.OutboxService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final InvitationRepository invitations;
    private final UserRepository users;
    private final WorkspaceSecurityPolicy policy;
    private final OutboxService outbox;

    public record CreateWorkspaceRequest(String name, String slug, String timezone) {}
    public record WorkspaceDto(UUID id, String name, String slug, UUID ownerId, String role, String status) {}
    public record MemberDto(UUID userId, String email, String username, String role, String status) {}
    public record InviteRequest(String email, String role) {}
    public record InviteDto(UUID id, String email, String role, String status, String tokenPreview) {}

    @Transactional
    public WorkspaceDto createWorkspace(UUID ownerId, CreateWorkspaceRequest req) {
        User owner = users.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (req.name() == null || req.name().isBlank()) throw new BadRequestException("Workspace name required");
        String base = (req.slug() != null && !req.slug().isBlank()) ? req.slug() : req.name();
        String slug = SlugUtils.uniqueSlug(base, workspaces::existsBySlug);
        Workspace ws = Workspace.builder()
                .name(req.name().trim())
                .slug(slug)
                .owner(owner)
                .timezone(req.timezone() != null ? req.timezone() : "UTC")
                .build();
        workspaces.save(ws);
        WorkspaceMembership m = WorkspaceMembership.builder()
                .workspace(ws).user(owner).role("OWNER").status("ACTIVE")
                .joinedAt(Instant.now()).build();
        memberships.save(m);
        outbox.emit("Workspace", ws.getId(), "WorkspaceCreated",
                "{\"workspaceId\":\"" + ws.getId() + "\",\"slug\":\"" + slug + "\"}");
        return toDto(ws, "OWNER");
    }

    @Transactional(readOnly = true)
    public WorkspaceDto getWorkspace(UUID requesterId, UUID workspaceId) {
        WorkspaceMembership m = requireActiveMember(requesterId, workspaceId);
        Workspace ws = workspaces.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        return toDto(ws, m.getRole());
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDto> listMyWorkspaces(UUID userId) {
        return memberships.findByUserId(userId).stream()
                .filter(x -> "ACTIVE".equals(x.getStatus()))
                .map(m -> toDto(m.getWorkspace(), m.getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemberDto> listMembers(UUID requesterId, UUID workspaceId) {
        WorkspaceMembership me = requireActiveMember(requesterId, workspaceId);
        // Guests see only members sharing explicit project access — simplified: hide emails for guests
        boolean guest = "GUEST".equals(me.getRole());
        return memberships.findActiveWithUser(workspaceId).stream()
                .map(m -> new MemberDto(m.getUser().getId(),
                        guest ? "***" : m.getUser().getEmail(),
                        m.getUser().getUsername(), m.getRole(), m.getStatus()))
                .toList();
    }

    /** Returns raw token (only time it is ever exposed). Stores only SHA-256 hash. */
    @Transactional
    public Map<String, String> inviteMember(UUID requesterId, UUID workspaceId, InviteRequest req) {
        WorkspaceMembership me = requireActiveMember(requesterId, workspaceId);
        if (!policy.canInvite(me.getRole())) throw new ForbiddenOperationException("Only admins can invite");
        if (req.email() == null || !req.email().contains("@")) throw new BadRequestException("Valid email required");
        String email = req.email().trim().toLowerCase();
        String role = (req.role() == null) ? "MEMBER" : req.role().toUpperCase();
        if (!Set.of("ADMIN", "MEMBER", "GUEST").contains(role)) throw new BadRequestException("Invalid role");
        if (WorkspaceSecurityPolicy.rank(role) >= WorkspaceSecurityPolicy.rank(me.getRole())
                && !"OWNER".equals(me.getRole())) {
            throw new ForbiddenOperationException("Cannot invite role equal or above your own");
        }
        // No duplicate active membership
        Optional<User> existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            Optional<WorkspaceMembership> em = memberships.findByWorkspaceIdAndUserId(workspaceId, existing.get().getId());
            if (em.isPresent() && "ACTIVE".equals(em.get().getStatus()))
                throw new BadRequestException("User is already an active member");
        }
        String rawToken = generateToken();
        String hash = sha256(rawToken);
        // Refresh pending invite for same email if present
        Optional<Invitation> pending = invitations.findByWorkspaceIdAndEmailIgnoreCaseAndStatus(workspaceId, email, "PENDING");
        Invitation inv = pending.orElseGet(() -> Invitation.builder()
                .workspace(workspaces.getReferenceById(workspaceId))
                .email(email).build());
        inv.setRole(role);
        inv.setTokenHash(hash);
        inv.setStatus("PENDING");
        inv.setExpiresAt(Instant.now().plusSeconds(7 * 24 * 3600));
        inv.setInvitedBy(users.getReferenceById(requesterId));
        invitations.save(inv);
        outbox.emit("Invitation", inv.getId(), "InvitationCreated",
                "{\"invitationId\":\"" + inv.getId() + "\",\"workspaceId\":\"" + workspaceId + "\",\"email\":\"" + email + "\"}");
        return Map.of("token", rawToken, "invitationId", inv.getId().toString());
    }

    @Transactional
    public WorkspaceDto acceptInvitation(UUID userId, String rawToken) {
        String hash = sha256(rawToken);
        Invitation inv = invitations.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestException("Invalid invitation token"));
        if (!"PENDING".equals(inv.getStatus())) throw new BadRequestException("Invitation already used");
        if (inv.isExpired()) {
            inv.setStatus("EXPIRED");
            invitations.save(inv);
            throw new BadRequestException("Invitation expired");
        }
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!user.getEmail().equalsIgnoreCase(inv.getEmail()))
            throw new ForbiddenOperationException("Invitation email does not match your account");
        Workspace ws = inv.getWorkspace();
        if (!"ACTIVE".equals(ws.getStatus())) throw new BadRequestException("Workspace is not active");
        Optional<WorkspaceMembership> existing = memberships.findByWorkspaceIdAndUserId(ws.getId(), userId);
        WorkspaceMembership m;
        if (existing.isPresent()) {
            m = existing.get();
            m.setRole(inv.getRole());
            m.setStatus("ACTIVE");
            m.setJoinedAt(Instant.now());
        } else {
            m = WorkspaceMembership.builder()
                    .workspace(ws).user(user).role(inv.getRole()).status("ACTIVE")
                    .joinedAt(Instant.now()).build();
        }
        memberships.save(m);
        inv.setStatus("CONSUMED");
        inv.setAcceptedAt(Instant.now());
        invitations.save(inv);
        outbox.emit("Workspace", ws.getId(), "MemberJoined",
                "{\"workspaceId\":\"" + ws.getId() + "\",\"userId\":\"" + userId + "\"}");
        return toDto(ws, m.getRole());
    }

    @Transactional
    public MemberDto changeRole(UUID requesterId, UUID workspaceId, UUID targetUserId, String newRole) {
        WorkspaceMembership me = requireActiveMember(requesterId, workspaceId);
        if (!policy.canManageMembers(me.getRole())) throw new ForbiddenOperationException("Only admins can change roles");
        WorkspaceMembership target = memberships.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
        String r = newRole.toUpperCase();
        if (!Set.of("ADMIN", "MEMBER", "GUEST").contains(r)) throw new BadRequestException("Invalid role");
        if (WorkspaceSecurityPolicy.rank(r) >= WorkspaceSecurityPolicy.rank(me.getRole()) && !"OWNER".equals(me.getRole()))
            throw new ForbiddenOperationException("Cannot grant role equal or above your own");
        if ("OWNER".equals(target.getRole())) throw new BadRequestException("Transfer ownership instead of changing owner role");
        target.setRole(r);
        memberships.save(target);
        outbox.emit("Workspace", workspaceId, "MemberRoleChanged",
                "{\"workspaceId\":\"" + workspaceId + "\",\"userId\":\"" + targetUserId + "\",\"role\":\"" + r + "\"}");
        User u = target.getUser();
        return new MemberDto(u.getId(), u.getEmail(), u.getUsername(), r, target.getStatus());
    }

    @Transactional
    public void deactivateMember(UUID requesterId, UUID workspaceId, UUID targetUserId) {
        WorkspaceMembership me = requireActiveMember(requesterId, workspaceId);
        if (!policy.canManageMembers(me.getRole())) throw new ForbiddenOperationException("Only admins can remove members");
        WorkspaceMembership target = memberships.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
        if ("OWNER".equals(target.getRole())) {
            long owners = memberships.countByWorkspaceIdAndRoleAndStatus(workspaceId, "OWNER", "ACTIVE");
            if (owners <= 1) throw new BadRequestException("Cannot remove the sole owner");
        }
        if (requesterId.equals(targetUserId) && "OWNER".equals(target.getRole()))
            throw new BadRequestException("Owner cannot leave without transferring ownership");
        target.setStatus("DEACTIVATED");
        memberships.save(target);
        outbox.emit("Workspace", workspaceId, "MemberDeactivated",
                "{\"workspaceId\":\"" + workspaceId + "\",\"userId\":\"" + targetUserId + "\"}");
    }

    @Transactional
    public WorkspaceDto transferOwnership(UUID requesterId, UUID workspaceId, UUID newOwnerId) {
        Workspace ws = workspaces.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        WorkspaceMembership me = requireActiveMember(requesterId, workspaceId);
        if (!"OWNER".equals(me.getRole())) throw new ForbiddenOperationException("Only owner can transfer ownership");
        WorkspaceMembership next = memberships.findByWorkspaceIdAndUserId(workspaceId, newOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("New owner must be a member"));
        if (!"ACTIVE".equals(next.getStatus())) throw new BadRequestException("New owner must be active");
        me.setRole("ADMIN");
        next.setRole("OWNER");
        memberships.save(me);
        memberships.save(next);
        ws.setOwner(next.getUser());
        workspaces.save(ws);
        outbox.emit("Workspace", workspaceId, "OwnershipTransferred",
                "{\"workspaceId\":\"" + workspaceId + "\",\"newOwnerId\":\"" + newOwnerId + "\"}");
        return toDto(ws, "ADMIN");
    }

    @Transactional(readOnly = true)
    public WorkspaceMembership requireActiveMember(UUID userId, UUID workspaceId) {
        // Never authorize solely from resource UUID: verify membership row exists and is ACTIVE
        WorkspaceMembership m = memberships.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ForbiddenOperationException("No access to workspace"));
        if (!"ACTIVE".equals(m.getStatus()))
            throw new ForbiddenOperationException("Membership is not active");
        // Also verify workspace exists and is active
        Workspace ws = m.getWorkspace();
        if (ws == null || !"ACTIVE".equals(ws.getStatus()))
            throw new ForbiddenOperationException("Workspace is not accessible");
        return m;
    }

    private WorkspaceDto toDto(Workspace ws, String role) {
        return new WorkspaceDto(ws.getId(), ws.getName(), ws.getSlug(),
                ws.getOwner() != null ? ws.getOwner().getId() : null, role, ws.getStatus());
    }

    private static String generateToken() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
