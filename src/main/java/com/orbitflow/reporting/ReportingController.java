package com.orbitflow.reporting;

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
public class ReportingController {
    private final ReportingService service;

    @GetMapping("/api/v1/projects/{projectId}/report")
    public ReportingService.ProjectReport report(@AuthenticationPrincipal UserPrincipal p,
                                                 @PathVariable UUID projectId,
                                                 @RequestParam(defaultValue = "UTC") String timezone) {
        return service.projectReport(p.getId(), projectId, timezone);
    }

    @PostMapping("/api/v1/projects/{projectId}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportingService.MilestoneDto create(@AuthenticationPrincipal UserPrincipal p,
                                                @PathVariable UUID projectId,
                                                @RequestBody Map<String, String> body) {
        return service.createMilestone(p.getId(), projectId, body.get("name"), body.get("dueDate"));
    }

    @GetMapping("/api/v1/projects/{projectId}/milestones")
    public List<ReportingService.MilestoneDto> list(@AuthenticationPrincipal UserPrincipal p,
                                                    @PathVariable UUID projectId) {
        return service.listMilestones(p.getId(), projectId);
    }

    @PostMapping("/api/v1/milestones/{milestoneId}/complete")
    public ReportingService.MilestoneDto complete(@AuthenticationPrincipal UserPrincipal p,
                                                  @PathVariable UUID milestoneId) {
        return service.completeMilestone(p.getId(), milestoneId);
    }
}
