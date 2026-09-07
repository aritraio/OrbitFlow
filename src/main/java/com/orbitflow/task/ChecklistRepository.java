package com.orbitflow.task;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistRepository extends JpaRepository<Checklist, UUID> {
    List<Checklist> findByTaskId(UUID taskId);
}
