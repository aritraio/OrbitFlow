package com.orbitflow.document;

import com.orbitflow.activity.ActivityService;
import com.orbitflow.common.exception.*;
import com.orbitflow.identity.UserRepository;
import com.orbitflow.outbox.OutboxService;
import com.orbitflow.project.Project;
import com.orbitflow.project.ProjectRepository;
import com.orbitflow.project.ProjectService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository documents;
    private final DocumentRevisionRepository revisions;
    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final UserRepository users;
    private final OutboxService outbox;
    private final ActivityService activity;

    public record DocumentDto(UUID id, UUID projectId, UUID parentId, String title,
                              String body, String draft, long version, String updatedAt) {}
    public record RevisionDto(UUID id, long revisionNumber, String title, String body, String createdAt) {}

    @Transactional
    public DocumentDto createDocument(UUID requesterId, UUID projectId, String title, String body, UUID parentId) {
        Project p = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectService.requireProjectAccess(requesterId, p);
        if (title == null || title.isBlank()) throw new BadRequestException("Title required");
        Document parent = null;
        if (parentId != null) {
            parent = documents.findById(parentId).orElseThrow(() -> new ResourceNotFoundException("Parent not found"));
            if (!parent.getProject().getId().equals(projectId)) throw new BadRequestException("Parent must be in same project");
        }
        Document d = Document.builder()
                .workspace(p.getWorkspace()).project(p).parent(parent)
                .title(title.trim()).currentBodyMarkdown(body).draftBodyMarkdown(body)
                .createdBy(users.getReferenceById(requesterId))
                .updatedBy(users.getReferenceById(requesterId))
                .build();
        documents.save(d);
        revisions.save(DocumentRevision.builder().document(d).revisionNumber(1)
                .title(d.getTitle()).bodyMarkdown(body)
                .createdBy(users.getReferenceById(requesterId)).build());
        outbox.emit("Document", d.getId(), "DocumentCreated",
                "{\"documentId\":\"" + d.getId() + "\",\"projectId\":\"" + projectId + "\"}");
        return toDto(d);
    }

    @Transactional
    public DocumentDto updateDocument(UUID requesterId, UUID documentId, String title, String body, boolean autosave) {
        Document d = documents.findById(documentId).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        projectService.requireProjectAccess(requesterId, d.getProject());
        if (autosave) {
            d.setDraftBodyMarkdown(body);
            if (title != null && !title.isBlank()) d.setTitle(title.trim());
            d.setUpdatedBy(users.getReferenceById(requesterId));
            documents.save(d);
            return toDto(d);
        }
        if (title != null && !title.isBlank()) d.setTitle(title.trim());
        d.setCurrentBodyMarkdown(body);
        d.setDraftBodyMarkdown(body);
        d.setUpdatedBy(users.getReferenceById(requesterId));
        documents.save(d);
        long next = revisions.countByDocumentId(documentId) + 1;
        revisions.save(DocumentRevision.builder().document(d).revisionNumber(next)
                .title(d.getTitle()).bodyMarkdown(body)
                .createdBy(users.getReferenceById(requesterId)).build());
        activity.record(d.getWorkspace(), d.getProject(), users.getReferenceById(requesterId),
                "DOCUMENT_REVISION_PUBLISHED", "Document", d.getId(), "{\"revision\":" + next + "}");
        outbox.emit("Document", d.getId(), "DocumentUpdated",
                "{\"documentId\":\"" + d.getId() + "\",\"revision\":" + next + "}");
        return toDto(documents.findById(documentId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> listDocuments(UUID requesterId, UUID projectId) {
        Project p = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectService.requireProjectAccess(requesterId, p);
        return documents.findByProjectId(projectId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<RevisionDto> listRevisions(UUID requesterId, UUID documentId) {
        Document d = documents.findById(documentId).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        projectService.requireProjectAccess(requesterId, d.getProject());
        return revisions.findByDocumentIdOrderByRevisionNumberAsc(documentId).stream()
                .map(r -> new RevisionDto(r.getId(), r.getRevisionNumber(), r.getTitle(), r.getBodyMarkdown(), r.getCreatedAt().toString()))
                .toList();
    }

    @Transactional
    public DocumentDto restoreRevision(UUID requesterId, UUID documentId, long revisionNumber) {
        Document d = documents.findById(documentId).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        projectService.requireProjectAccess(requesterId, d.getProject());
        DocumentRevision rev = revisions.findByDocumentIdOrderByRevisionNumberAsc(documentId).stream()
                .filter(r -> r.getRevisionNumber() == revisionNumber).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Revision not found"));
        return updateDocument(requesterId, documentId, rev.getTitle(), rev.getBodyMarkdown(), false);
    }

    private DocumentDto toDto(Document d) {
        return new DocumentDto(d.getId(), d.getProject().getId(),
                d.getParent() != null ? d.getParent().getId() : null,
                d.getTitle(), d.getCurrentBodyMarkdown(), d.getDraftBodyMarkdown(),
                d.getVersion() == null ? 0 : d.getVersion(),
                d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null);
    }
}
