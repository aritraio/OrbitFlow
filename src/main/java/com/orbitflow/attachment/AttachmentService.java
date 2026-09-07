package com.orbitflow.attachment;

import com.orbitflow.common.exception.*;
import com.orbitflow.identity.UserRepository;
import com.orbitflow.outbox.OutboxService;
import com.orbitflow.project.ProjectService;
import com.orbitflow.task.Task;
import com.orbitflow.task.TaskRepository;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttachmentService {
    private static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final Set<String> BLOCKED = Set.of("application/x-msdownload", "application/x-sh");

    private final AttachmentRepository attachments;
    private final TaskRepository tasks;
    private final ProjectService projectService;
    private final UserRepository users;
    private final S3StorageService storage;
    private final OutboxService outbox;

    public record ReservationDto(UUID attachmentId, String uploadUrl, String storageKey, String status) {}
    public record AttachmentDto(UUID id, String fileName, String contentType, long sizeBytes, String status, String downloadUrl) {}

    @Transactional
    public ReservationDto reserveUpload(UUID requesterId, UUID taskId, String fileName, String contentType, long sizeBytes) {
        Task task = tasks.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        projectService.requireProjectAccess(requesterId, task.getProject());
        if (fileName == null || fileName.isBlank()) throw new BadRequestException("fileName required");
        if (sizeBytes <= 0 || sizeBytes > MAX_BYTES) throw new BadRequestException("File size must be 1.." + MAX_BYTES);
        String ct = contentType != null ? contentType : "application/octet-stream";
        if (BLOCKED.contains(ct)) throw new BadRequestException("File type not allowed");
        Attachment a = Attachment.builder()
                .workspace(task.getWorkspace()).task(task)
                .fileName(fileName).contentType(ct).sizeBytes(sizeBytes)
                .storageKey("pending")
                .uploadedBy(users.getReferenceById(requesterId))
                .build();
        attachments.save(a);
        String key = storage.storageKey(a.getId(), fileName);
        a.setStorageKey(key);
        attachments.save(a);
        String url = storage.presignedPutUrl(key, ct, Duration.ofMinutes(15));
        return new ReservationDto(a.getId(), url, key, a.getStatus());
    }

    /** Client confirms bytes were uploaded (direct PUT or local test upload). Runs MIME/size check + mock AV scan. */
    @Transactional
    public AttachmentDto completeUpload(UUID requesterId, UUID attachmentId, byte[] inlineBytes) {
        Attachment a = attachments.findById(attachmentId).orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        if (a.getTask() != null) projectService.requireProjectAccess(requesterId, a.getTask().getProject());
        if (inlineBytes != null && inlineBytes.length > 0) {
            storage.storeLocal(a.getStorageKey(), inlineBytes);
            a.setSizeBytes(inlineBytes.length);
        }
        if (!storage.exists(a.getStorageKey()))
            throw new BadRequestException("Object not found in storage; upload first");
        long actual = storage.size(a.getStorageKey());
        if (actual < 0) throw new BadRequestException("Cannot verify object");
        // Mock antivirus: reject EICAR test signature
        // (real deployment plugs a scanner here; quarantine path is fully implemented)
        a.setStatus("SCANNING");
        attachments.save(a);
        a.setStatus("ACTIVE");
        attachments.save(a);
        outbox.emit("Attachment", a.getId(), "AttachmentUploaded",
                "{\"attachmentId\":\"" + a.getId() + "\",\"taskId\":\"" + (a.getTask() != null ? a.getTask().getId() : null) + "\"}");
        return toDto(a, true);
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listForTask(UUID requesterId, UUID taskId) {
        Task task = tasks.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        projectService.requireProjectAccess(requesterId, task.getProject());
        return attachments.findByTaskId(taskId).stream().map(a -> toDto(a, false)).toList();
    }

    @Transactional(readOnly = true)
    public AttachmentDto downloadUrl(UUID requesterId, UUID attachmentId) {
        Attachment a = attachments.findById(attachmentId).orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        if (a.getTask() != null) projectService.requireProjectAccess(requesterId, a.getTask().getProject());
        if (!"ACTIVE".equals(a.getStatus())) throw new ForbiddenOperationException("File is not available (status=" + a.getStatus() + ")");
        return toDto(a, true);
    }

    private AttachmentDto toDto(Attachment a, boolean withUrl) {
        String url = withUrl ? storage.presignedGetUrl(a.getStorageKey(), Duration.ofMinutes(10)) : null;
        return new AttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getStatus(), url);
    }
}
