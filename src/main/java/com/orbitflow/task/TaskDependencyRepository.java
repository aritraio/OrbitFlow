package com.orbitflow.task;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {
    List<TaskDependency> findByTaskId(UUID taskId);
    List<TaskDependency> findByDependsOnId(UUID dependsOnId);
    boolean existsByTaskIdAndDependsOnId(UUID taskId, UUID dependsOnId);
}
