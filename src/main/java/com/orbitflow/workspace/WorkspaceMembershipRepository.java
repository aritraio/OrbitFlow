package com.orbitflow.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {
    Optional<WorkspaceMembership> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
    List<WorkspaceMembership> findByWorkspaceId(UUID workspaceId);
    List<WorkspaceMembership> findByUserId(UUID userId);
    boolean existsByWorkspaceIdAndUserIdAndStatus(UUID workspaceId, UUID userId, String status);
    long countByWorkspaceIdAndRoleAndStatus(UUID workspaceId, String role, String status);

    @Query("select m from WorkspaceMembership m join fetch m.user where m.workspace.id = :workspaceId and m.status = 'ACTIVE'")
    List<WorkspaceMembership> findActiveWithUser(UUID workspaceId);
}
