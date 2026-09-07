package com.orbitflow.document;

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
public class DocumentController {
    private final DocumentService service;

    @PostMapping("/api/v1/projects/{projectId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentService.DocumentDto create(@AuthenticationPrincipal UserPrincipal p,
                                              @PathVariable UUID projectId,
                                              @RequestBody Map<String, String> body) {
        UUID parent = body.get("parentId") != null ? UUID.fromString(body.get("parentId")) : null;
        return service.createDocument(p.getId(), projectId, body.get("title"), body.get("body"), parent);
    }

    @GetMapping("/api/v1/projects/{projectId}/documents")
    public List<DocumentService.DocumentDto> list(@AuthenticationPrincipal UserPrincipal p,
                                                  @PathVariable UUID projectId) {
        return service.listDocuments(p.getId(), projectId);
    }

    @PatchMapping("/api/v1/documents/{documentId}")
    public DocumentService.DocumentDto update(@AuthenticationPrincipal UserPrincipal p,
                                              @PathVariable UUID documentId,
                                              @RequestBody Map<String, Object> body) {
        boolean autosave = Boolean.TRUE.equals(body.get("autosave"));
        return service.updateDocument(p.getId(), documentId,
                (String) body.get("title"), (String) body.get("body"), autosave);
    }

    @GetMapping("/api/v1/documents/{documentId}/revisions")
    public List<DocumentService.RevisionDto> revisions(@AuthenticationPrincipal UserPrincipal p,
                                                       @PathVariable UUID documentId) {
        return service.listRevisions(p.getId(), documentId);
    }

    @PostMapping("/api/v1/documents/{documentId}/restore/{revisionNumber}")
    public DocumentService.DocumentDto restore(@AuthenticationPrincipal UserPrincipal p,
                                               @PathVariable UUID documentId,
                                               @PathVariable long revisionNumber) {
        return service.restoreRevision(p.getId(), documentId, revisionNumber);
    }
}
