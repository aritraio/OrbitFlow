package com.orbitflow.activity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<ActivityEvent, UUID> {
    List<ActivityEvent> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);
    List<ActivityEvent> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, UUID resourceId, Pageable pageable);
    List<ActivityEvent> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
}
