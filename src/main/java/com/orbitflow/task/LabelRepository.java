package com.orbitflow.task;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelRepository extends JpaRepository<Label, UUID> {
    List<Label> findByWorkspaceId(UUID workspaceId);
}
