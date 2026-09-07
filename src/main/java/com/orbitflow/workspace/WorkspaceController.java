package com.orbitflow.workspace;

import com.orbitflow.common.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class WorkspaceController {
    private final WorkspaceService service;

    @PostMapping("/api/v1/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceService.WorkspaceDto create(@AuthenticationPrincipal UserPrincipal p,
                                                @Valid @RequestBody Map<String, String> body) {
        return service.createWorkspace(p.getId(), new WorkspaceService.CreateWorkspaceRequest(
                body.get("name"), body.get("slug"), body.getOrDefault("timezone", "UTC")));
    }

    @GetMapping("/api/v1/workspaces")
    public List<WorkspaceService.WorkspaceDto> mine(@AuthenticationPrincipal UserPrincipal p) {
        return service.listMyWorkspaces(p.getId());
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}")
    public WorkspaceService.WorkspaceDto get(@AuthenticationPrincipal UserPrincipal p,
                                             @PathVariable UUID workspaceId) {
        return service.getWorkspace(p.getId(), workspaceId);
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/members")
    public List<WorkspaceService.MemberDto> members(@AuthenticationPrincipal UserPrincipal p,
                                                    @PathVariable UUID workspaceId) {
        return service.listMembers(p.getId(), workspaceId);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> invite(@AuthenticationPrincipal UserPrincipal p,
                                      @PathVariable UUID workspaceId,
                                      @RequestBody Map<String, String> body) {
        return service.inviteMember(p.getId(), workspaceId,
                new WorkspaceService.InviteRequest(body.get("email"), body.getOrDefault("role", "MEMBER")));
    }

    @PostMapping("/api/v1/invitations/{token}/accept")
    public WorkspaceService.WorkspaceDto accept(@AuthenticationPrincipal UserPrincipal p,
                                                @PathVariable String token) {
        return service.acceptInvitation(p.getId(), token);
    }

    @PatchMapping("/api/v1/workspaces/{workspaceId}/members/{userId}")
    public WorkspaceService.MemberDto changeRole(@AuthenticationPrincipal UserPrincipal p,
                                                 @PathVariable UUID workspaceId,
                                                 @PathVariable UUID userId,
                                                 @RequestBody Map<String, String> body) {
        return service.changeRole(p.getId(), workspaceId, userId, body.getOrDefault("role", "MEMBER"));
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal UserPrincipal p,
                           @PathVariable UUID workspaceId,
                           @PathVariable UUID userId) {
        service.deactivateMember(p.getId(), workspaceId, userId);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/transfer")
    public WorkspaceService.WorkspaceDto transfer(@AuthenticationPrincipal UserPrincipal p,
                                                  @PathVariable UUID workspaceId,
                                                  @RequestBody Map<String, String> body) {
        return service.transferOwnership(p.getId(), workspaceId, UUID.fromString(body.get("newOwnerId")));
    }
}
