package com.orbitflow.comment;

import com.orbitflow.activity.ActivityService;
import com.orbitflow.common.exception.*;
import com.orbitflow.common.util.MarkdownSanitizer;
import com.orbitflow.identity.User;
import com.orbitflow.identity.UserRepository;
import com.orbitflow.notification.NotificationService;
import com.orbitflow.outbox.OutboxService;
import com.orbitflow.project.ProjectService;
import com.orbitflow.task.Task;
import com.orbitflow.task.TaskRepository;
import com.orbitflow.workspace.WorkspaceMembershipRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository comments;
    private final CommentRevisionRepository revisions;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final ProjectService projectService;
    private final WorkspaceMembershipRepository workspaceMembers;
    private final NotificationService notificationService;
    private final OutboxService outbox;
    private final ActivityService activity;

    public record CommentDto(UUID id, UUID taskId, UUID authorId, String username,
                             String bodyMarkdown, String bodyHtml, boolean edited, String createdAt) {}

    @Transactional
    public CommentDto addComment(UUID requesterId, UUID taskId, String markdown, UUID parentId) {
        Task task = tasks.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        projectService.requireProjectAccess(requesterId, task.getProject());
        if (markdown == null || markdown.isBlank()) throw new BadRequestException("Comment body required");
        if (markdown.length() > 20000) throw new BadRequestException("Comment too long");
        String html = MarkdownSanitizer.renderMarkdown(markdown);
        User author = users.getReferenceById(requesterId);
        Comment parent = parentId != null ? comments.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found")) : null;
        Comment c = Comment.builder().task(task).author(author)
                .bodyMarkdown(markdown).bodyHtml(html).parent(parent).build();
        comments.save(c);

        // Mentions: verify each mentioned user can access the task, dedupe, exclude self
        Set<String> handles = MentionParser.extract(markdown);
        Set<UUID> toNotify = new LinkedHashSet<>();
        for (String handle : handles) {
            Optional<User> mentioned = users.findByUsernameIgnoreCase(handle);
            if (mentioned.isEmpty()) continue;
            User mu = mentioned.get();
            if (mu.getId().equals(requesterId)) continue; // no self-notify
            try {
                projectService.requireProjectAccess(mu.getId(), task.getProject());
            } catch (ForbiddenOperationException e) {
                continue; // reject mentions of users who cannot access the task
            }
            toNotify.add(mu.getId());
        }
        // Watchers + assignees also get COMMENT_ADDED (deduped inside notifyUsers)
        if (!toNotify.isEmpty()) {
            notificationService.notifyUsers(task.getWorkspace().getId(), toNotify,
                    "MENTION", "You were mentioned in " + task.getTaskKey(),
                    "@" + users.findById(requesterId).map(User::getUsername).orElse("someone") + " mentioned you: " + snippet(markdown),
                    "Task", task.getId());
        }
        activity.record(task.getWorkspace(), task.getProject(), users.getReferenceById(requesterId),
                "COMMENT_CREATED", "Comment", c.getId(), "{\"taskKey\":\"" + task.getTaskKey() + "\"}");
        outbox.emit("Comment", c.getId(), "CommentCreated",
                "{\"commentId\":\"" + c.getId() + "\",\"taskId\":\"" + taskId + "\"}");
        return toDto(c);
    }

    @Transactional
    public CommentDto editComment(UUID requesterId, UUID commentId, String markdown) {
        Comment c = comments.findById(commentId).orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (!c.getAuthor().getId().equals(requesterId))
            throw new ForbiddenOperationException("Only the author can edit this comment");
        if (c.isDeleted()) throw new BadRequestException("Comment is deleted");
        revisions.save(CommentRevision.builder().comment(c)
                .bodyMarkdown(c.getBodyMarkdown()).bodyHtml(c.getBodyHtml())
                .createdBy(users.getReferenceById(requesterId)).build());
        // Re-run mention detection, only notify newly mentioned users (dedupe key handles it)
        Set<String> handles = MentionParser.extract(markdown);
        Set<UUID> toNotify = new LinkedHashSet<>();
        for (String handle : handles) {
            users.findByUsernameIgnoreCase(handle).ifPresent(mu -> {
                if (!mu.getId().equals(requesterId)) {
                    try {
                        projectService.requireProjectAccess(mu.getId(), c.getTask().getProject());
                        toNotify.add(mu.getId());
                    } catch (ForbiddenOperationException ignored) {}
                }
            });
        }
        c.setBodyMarkdown(markdown);
        c.setBodyHtml(MarkdownSanitizer.renderMarkdown(markdown));
        c.setEdited(true);
        comments.save(c);
        if (!toNotify.isEmpty()) {
            notificationService.notifyUsers(c.getTask().getWorkspace().getId(), toNotify,
                    "MENTION", "You were mentioned in " + c.getTask().getTaskKey(),
                    "A comment you were mentioned in was edited", "Task", c.getTask().getId());
        }
        return toDto(c);
    }

    @Transactional
    public void deleteComment(UUID requesterId, UUID commentId) {
        Comment c = comments.findById(commentId).orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (!c.getAuthor().getId().equals(requesterId))
            throw new ForbiddenOperationException("Only the author can delete this comment");
        c.setDeleted(true); // soft-delete, preserve audit history
        comments.save(c);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> listComments(UUID requesterId, UUID taskId) {
        Task task = tasks.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        projectService.requireProjectAccess(requesterId, task.getProject());
        return comments.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .filter(c -> !c.isDeleted()).map(this::toDto).toList();
    }

    private CommentDto toDto(Comment c) {
        return new CommentDto(c.getId(), c.getTask().getId(), c.getAuthor().getId(),
                c.getAuthor().getUsername(), c.getBodyMarkdown(), c.getBodyHtml(),
                c.isEdited(), c.getCreatedAt().toString());
    }

    private static String snippet(String s) {
        if (s == null) return "";
        return s.length() > 140 ? s.substring(0, 140) + "…" : s;
    }
}
