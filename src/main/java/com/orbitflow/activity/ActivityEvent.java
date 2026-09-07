package com.orbitflow.activity;

import com.orbitflow.identity.User;
import com.orbitflow.workspace.Workspace;
import com.orbitflow.project.Project;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "activity_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActivityEvent {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "diff_json", columnDefinition = "TEXT")
    private String diffJson;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
