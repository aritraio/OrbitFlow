package com.orbitflow.task;

import com.orbitflow.board.BoardColumn;
import com.orbitflow.identity.User;
import com.orbitflow.project.Project;
import com.orbitflow.workspace.Workspace;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Task {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "column_id")
    private BoardColumn column;

    @Column(name = "task_number", nullable = false)
    private long taskNumber;

    @Column(name = "task_key", nullable = false, length = 32)
    private String taskKey;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "task_type", nullable = false, length = 32)
    @Builder.Default
    private String type = "TASK";

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String priority = "MEDIUM";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Column(name = "rank_value", nullable = false, length = 64)
    @Builder.Default
    private String rank = "a0";

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "estimate_minutes")
    private Integer estimateMinutes;

    @Column(name = "spent_minutes", nullable = false)
    @Builder.Default
    private int spentMinutes = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private Task parentTask;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Version
    @Builder.Default
    private Long version = 0L;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<TaskAssignee> assignees = new HashSet<>();

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
