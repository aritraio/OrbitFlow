package com.orbitflow.activity;

import com.orbitflow.common.security.UserPrincipal;
import com.orbitflow.project.ProjectService;
import com.orbitflow.task.TaskService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ActivityController {
    private final ActivityService service;
    private final ProjectService projects;
    private final TaskService tasks;

    @GetMapping("/api/v1/workspaces/{workspaceId}/activity")
    public List<ActivityService.ActivityDto> workspace(@AuthenticationPrincipal UserPrincipal p,
                                                       @PathVariable UUID workspaceId,
                                                       @RequestParam(defaultValue = "50") int limit) {
        projects.requireWorkspaceMember(p.getId(), workspaceId);
        return service.timelineForWorkspace(workspaceId, Math.min(limit, 200));
    }

    @GetMapping("/api/v1/projects/{projectId}/activity")
    public List<ActivityService.ActivityDto> project(@AuthenticationPrincipal UserPrincipal p,
                                                     @PathVariable UUID projectId) {
        var dto = projects.getProject(p.getId(), projectId);
        return service.timelineForProject(projectId, 100);
    }
}
