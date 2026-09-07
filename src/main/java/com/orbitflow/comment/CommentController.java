package com.orbitflow.comment;

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
public class CommentController {
    private final CommentService service;

    @PostMapping("/api/v1/tasks/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentService.CommentDto add(@AuthenticationPrincipal UserPrincipal p,
                                         @PathVariable UUID taskId,
                                         @RequestBody Map<String, String> body) {
        UUID parent = body.get("parentCommentId") != null ? UUID.fromString(body.get("parentCommentId")) : null;
        return service.addComment(p.getId(), taskId, body.get("body"), parent);
    }

    @GetMapping("/api/v1/tasks/{taskId}/comments")
    public List<CommentService.CommentDto> list(@AuthenticationPrincipal UserPrincipal p,
                                                @PathVariable UUID taskId) {
        return service.listComments(p.getId(), taskId);
    }

    @PatchMapping("/api/v1/comments/{commentId}")
    public CommentService.CommentDto edit(@AuthenticationPrincipal UserPrincipal p,
                                          @PathVariable UUID commentId,
                                          @RequestBody Map<String, String> body) {
        return service.editComment(p.getId(), commentId, body.get("body"));
    }

    @DeleteMapping("/api/v1/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID commentId) {
        service.deleteComment(p.getId(), commentId);
    }
}
