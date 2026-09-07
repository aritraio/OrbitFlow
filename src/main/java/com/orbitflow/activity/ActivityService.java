package com.orbitflow.activity;

import com.orbitflow.identity.User;
import com.orbitflow.project.Project;
import com.orbitflow.workspace.Workspace;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityService {
    private final ActivityRepository repo;

    public record ActivityDto(UUID id, UUID workspaceId, UUID projectId, UUID actorId,
                              String eventType, String resourceType, UUID resourceId,
                              String diffJson, String createdAt) {}

    @Transactional
    public void record(Workspace workspace, Project project, User actor,
                       String eventType, String resourceType, UUID resourceId, String diffJson) {
        // Sanitize: never store secrets or file internals — diffJson is caller-sanitized summary
        String safe = diffJson == null ? "{}" : diffJson.replaceAll("(?i)(password|secret|token)[^\"]*\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"[REDACTED]\"");
        repo.save(ActivityEvent.builder()
                .workspace(workspace).project(project).actor(actor)
                .eventType(eventType).resourceType(resourceType).resourceId(resourceId)
                .diffJson(safe.length() > 8000 ? safe.substring(0, 8000) : safe)
                .build());
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> timelineForResource(String resourceType, UUID resourceId, int limit) {
        return repo.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(resourceType, resourceId, PageRequest.of(0, limit))
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> timelineForWorkspace(UUID workspaceId, int limit) {
        return repo.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, PageRequest.of(0, limit))
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> timelineForProject(UUID projectId, int limit) {
        return repo.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, limit))
                .stream().map(this::toDto).toList();
    }

    private ActivityDto toDto(ActivityEvent e) {
        return new ActivityDto(e.getId(),
                e.getWorkspace() != null ? e.getWorkspace().getId() : null,
                e.getProject() != null ? e.getProject().getId() : null,
                e.getActor() != null ? e.getActor().getId() : null,
                e.getEventType(), e.getResourceType(), e.getResourceId(),
                e.getDiffJson(), e.getCreatedAt().toString());
    }
}
