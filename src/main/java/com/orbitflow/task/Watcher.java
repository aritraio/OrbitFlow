package com.orbitflow.task;

import com.orbitflow.identity.User;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "watchers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(Watcher.WatcherId.class)
public class Watcher {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class WatcherId implements Serializable {
        private UUID task;
        private UUID user;
        @Override public boolean equals(Object o) {
            if (!(o instanceof WatcherId other)) return false;
            return Objects.equals(task, other.task) && Objects.equals(user, other.user);
        }
        @Override public int hashCode() { return Objects.hash(task, user); }
    }
}
