package com.orbitflow.attachment;

import com.orbitflow.identity.User;
import com.orbitflow.task.Task;
import com.orbitflow.workspace.Workspace;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "attachments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Attachment {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 160)
    @Builder.Default
    private String contentType = "application/octet-stream";

    @Column(name = "size_bytes", nullable = false)
    @Builder.Default
    private long sizeBytes = 0L;

    @Column(name = "checksum_sha256", length = 128)
    private String checksumSha256;

    @Column(name = "storage_key", nullable = false, length = 1024)
    private String storageKey;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Version
    @Builder.Default
    private Long version = 0L;

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
