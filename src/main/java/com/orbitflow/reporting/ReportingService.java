package com.orbitflow.reporting;

import com.orbitflow.board.BoardColumnRepository;
import com.orbitflow.common.exception.*;
import com.orbitflow.project.Project;
import com.orbitflow.project.ProjectRepository;
import com.orbitflow.project.ProjectService;
import com.orbitflow.task.Task;
import com.orbitflow.task.TaskRepository;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportingService {
    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final TaskRepository tasks;
    private final BoardColumnRepository columns;
    private final MilestoneRepository milestones;

    public record ProjectReport(UUID projectId, String timezone, String generatedAt,
                                long total, long completed, long overdue, long blocked,
                                Map<String, Long> byStatus, Map<String, Long> byPriority,
                                Double avgCycleHours) {}
    public record MilestoneDto(UUID id, UUID projectId, String name, String dueDate,
                               String completedAt, String snapshot) {}

    @Transactional(readOnly = true)
    public ProjectReport projectReport(UUID requesterId, UUID projectId, String timezone) {
        Project p = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectService.requireProjectAccess(requesterId, p);
        ZoneId zone;
        try { zone = ZoneId.of(timezone != null ? timezone : "UTC"); }
        catch (Exception e) { zone = ZoneId.of("UTC"); }
        List<Task> all = tasks.findByProjectId(projectId).stream().filter(t -> !t.isArchived()).toList();
        long completed = all.stream().filter(t -> t.getColumn() != null && "DONE".equals(t.getColumn().getCategory())).count();
        Instant now = Instant.now();
        long overdue = all.stream().filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(now)
                && (t.getColumn() == null || !"DONE".equals(t.getColumn().getCategory()))).count();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (Task t : all) {
            String s = t.getColumn() != null ? t.getColumn().getName() : "No column";
            byStatus.merge(s, 1L, Long::sum);
            byPriority.merge(t.getPriority(), 1L, Long::sum);
        }
        // Cycle time from activity timestamps approximation: createdAt -> updatedAt for DONE tasks
        Double avgCycle = all.stream()
                .filter(t -> t.getColumn() != null && "DONE".equals(t.getColumn().getCategory()))
                .mapToDouble(t -> Duration.between(t.getCreatedAt(), t.getUpdatedAt()).toMinutes() / 60.0)
                .average().orElse(0.0);
        return new ProjectReport(projectId, zone.getId(), ZonedDateTime.now(zone).toString(),
                all.size(), completed, overdue, 0L, byStatus, byPriority, avgCycle);
    }

    @Transactional
    public MilestoneDto createMilestone(UUID requesterId, UUID projectId, String name, String dueDate) {
        Project p = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        var wm = projectService.requireWorkspaceMember(requesterId, p.getWorkspace().getId());
        if (!new com.orbitflow.workspace.WorkspaceSecurityPolicy().canConfigureWorkflow(wm.getRole(), projectService.projectRole(requesterId, projectId)))
            throw new ForbiddenOperationException("Only managers can manage milestones");
        Milestone m = Milestone.builder().project(p).name(name).build();
        if (dueDate != null && !dueDate.isBlank()) m.setDueDate(java.time.LocalDate.parse(dueDate));
        milestones.save(m);
        return toDto(m);
    }

    @Transactional
    public MilestoneDto completeMilestone(UUID requesterId, UUID milestoneId) {
        Milestone m = milestones.findById(milestoneId).orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));
        var report = projectReport(requesterId, m.getProject().getId(), "UTC");
        m.setCompletedAt(Instant.now());
        m.setSnapshotJson("{\"total\":" + report.total() + ",\"completed\":" + report.completed()
                + ",\"generatedAt\":\"" + report.generatedAt() + "\"}");
        milestones.save(m);
        return toDto(m);
    }

    @Transactional(readOnly = true)
    public List<MilestoneDto> listMilestones(UUID requesterId, UUID projectId) {
        Project p = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectService.requireProjectAccess(requesterId, p);
        return milestones.findByProjectId(projectId).stream().map(this::toDto).toList();
    }

    private MilestoneDto toDto(Milestone m) {
        return new MilestoneDto(m.getId(), m.getProject().getId(), m.getName(),
                m.getDueDate() != null ? m.getDueDate().toString() : null,
                m.getCompletedAt() != null ? m.getCompletedAt().toString() : null,
                m.getSnapshotJson());
    }
}
