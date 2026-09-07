package com.orbitflow.search;

import com.orbitflow.document.Document;
import com.orbitflow.document.DocumentRepository;
import com.orbitflow.project.Project;
import com.orbitflow.project.ProjectRepository;
import com.orbitflow.project.ProjectService;
import com.orbitflow.task.Task;
import com.orbitflow.task.TaskRepository;
import com.orbitflow.workspace.WorkspaceMembershipRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final TaskRepository tasks;
    private final DocumentRepository documents;
    private final ProjectService projectService;
    private final WorkspaceMembershipRepository workspaceMembers;
    private final ProjectRepository projects;

    public record SearchHit(String resourceType, UUID resourceId, String title, String snippet, UUID projectId) {}

    @Transactional(readOnly = true)
    public List<SearchHit> search(UUID requesterId, String query, UUID projectFilter) {
        if (query == null || query.isBlank()) return List.of();
        String q = query.trim();
        if (q.length() > 200) q = q.substring(0, 200);
        List<SearchHit> out = new ArrayList<>();

        if (projectFilter != null) {
            Project p = projects.findById(projectFilter).orElse(null);
            if (p == null) return List.of();
            try {
                projectService.requireProjectAccess(requesterId, p);
            } catch (Exception e) {
                return List.of();
            }
            UUID wsId = p.getWorkspace().getId();
            for (Task t : tasks.searchTasks(wsId, projectFilter, q, PageRequest.of(0, 50))) {
                try {
                    projectService.requireProjectAccess(requesterId, t.getProject());
                } catch (Exception e) {
                    continue;
                }
                out.add(new SearchHit("Task", t.getId(), t.getTaskKey() + " " + t.getTitle(),
                        snippet(t.getDescription(), q), t.getProject().getId()));
                if (out.size() >= 50) return out;
            }
            for (Document d : documents.searchDocuments(wsId, projectFilter, q, PageRequest.of(0, 50))) {
                try {
                    projectService.requireProjectAccess(requesterId, d.getProject());
                } catch (Exception e) {
                    continue;
                }
                out.add(new SearchHit("Document", d.getId(), d.getTitle(), snippet(d.getCurrentBodyMarkdown(), q), d.getProject().getId()));
                if (out.size() >= 50) return out;
            }
            return out.stream().limit(50).toList();
        }

        List<UUID> workspaceIds = workspaceMembers.findByUserId(requesterId).stream()
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .map(m -> m.getWorkspace().getId())
                .distinct()
                .toList();
        for (UUID wsId : workspaceIds) {
            for (Task t : tasks.searchTasks(wsId, null, q, PageRequest.of(0, 50))) {
                try {
                    projectService.requireProjectAccess(requesterId, t.getProject());
                } catch (Exception e) {
                    continue; // strictly enforce workspace/project boundaries
                }
                out.add(new SearchHit("Task", t.getId(), t.getTaskKey() + " " + t.getTitle(),
                        snippet(t.getDescription(), q), t.getProject().getId()));
                if (out.size() >= 50) return out;
            }
            for (Document d : documents.searchDocuments(wsId, null, q, PageRequest.of(0, 50))) {
                try {
                    projectService.requireProjectAccess(requesterId, d.getProject());
                } catch (Exception e) {
                    continue;
                }
                out.add(new SearchHit("Document", d.getId(), d.getTitle(), snippet(d.getCurrentBodyMarkdown(), q), d.getProject().getId()));
                if (out.size() >= 50) return out;
            }
        }
        return out.stream().limit(50).toList();
    }

    private static String snippet(String text, String q) {
        if (text == null) return "";
        int i = text.toLowerCase(Locale.ROOT).indexOf(q.toLowerCase(Locale.ROOT));
        if (i < 0) return text.length() > 120 ? text.substring(0, 120) + "…" : text;
        int from = Math.max(0, i - 40);
        int to = Math.min(text.length(), i + q.length() + 40);
        return (from > 0 ? "…" : "") + text.substring(from, to) + (to < text.length() ? "…" : "");
    }
}
