package com.orbitflow.search;

import com.orbitflow.comment.CommentRepository;
import com.orbitflow.document.DocumentRepository;
import com.orbitflow.project.ProjectService;
import com.orbitflow.task.Task;
import com.orbitflow.task.TaskRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final TaskRepository tasks;
    private final CommentRepository comments;
    private final DocumentRepository documents;
    private final ProjectService projectService;

    public record SearchHit(String resourceType, UUID resourceId, String title, String snippet, UUID projectId) {}

    @Transactional(readOnly = true)
    public List<SearchHit> search(UUID requesterId, String query, UUID projectFilter) {
        if (query == null || query.isBlank()) return List.of();
        String q = query.trim();
        List<SearchHit> out = new ArrayList<>();
        // Tasks: workspace-scoped LIKE fallback (Postgres FTS upgrade path documented in README)
        List<Task> candidates = tasks.findAll().stream()
                .filter(t -> !t.isArchived())
                .filter(t -> projectFilter == null || t.getProject().getId().equals(projectFilter))
                .filter(t -> matches(t.getTitle(), q) || matches(t.getDescription(), q))
                .toList();
        for (Task t : candidates) {
            try {
                projectService.requireProjectAccess(requesterId, t.getProject());
            } catch (Exception e) {
                continue; // strictly enforce workspace/project boundaries
            }
            out.add(new SearchHit("Task", t.getId(), t.getTaskKey() + " " + t.getTitle(),
                    snippet(t.getDescription(), q), t.getProject().getId()));
        }
        // Documents
        for (var d : documents.findAll()) {
            if (projectFilter != null && !d.getProject().getId().equals(projectFilter)) continue;
            try {
                projectService.requireProjectAccess(requesterId, d.getProject());
            } catch (Exception e) {
                continue;
            }
            if (matches(d.getTitle(), q) || matches(d.getCurrentBodyMarkdown(), q)) {
                out.add(new SearchHit("Document", d.getId(), d.getTitle(), snippet(d.getCurrentBodyMarkdown(), q), d.getProject().getId()));
            }
        }
        return out.stream().limit(50).toList();
    }

    private static boolean matches(String haystack, String q) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT));
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
