package com.orbitflow.project;

import com.orbitflow.common.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService service;

    @PostMapping("/api/v1/workspaces/{workspaceId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectService.ProjectDto create(@AuthenticationPrincipal UserPrincipal p,
                                            @PathVariable UUID workspaceId,
                                            @RequestBody Map<String, String> body) {
        return service.createProject(p.getId(), workspaceId, new ProjectService.CreateProjectRequest(
                body.get("key"), body.get("name"), body.get("description"),
                body.getOrDefault("visibility", "WORKSPACE"),
                body.get("startDate"), body.get("targetDate")));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/projects")
    public List<ProjectService.ProjectDto> list(@AuthenticationPrincipal UserPrincipal p,
                                                @PathVariable UUID workspaceId) {
        return service.listProjects(p.getId(), workspaceId);
    }

    @GetMapping("/api/v1/projects/{projectId}")
    public ProjectService.ProjectDto get(@AuthenticationPrincipal UserPrincipal p,
                                         @PathVariable UUID projectId) {
        return service.getProject(p.getId(), projectId);
    }

    @PostMapping("/api/v1/projects/{projectId}/archive")
    public ProjectService.ProjectDto archive(@AuthenticationPrincipal UserPrincipal p,
                                             @PathVariable UUID projectId,
                                             @RequestBody Map<String, Object> body) {
        boolean archived = Boolean.TRUE.equals(body.get("archived"));
        return service.archiveProject(p.getId(), projectId, archived);
    }
}
