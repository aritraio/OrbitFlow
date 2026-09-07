package com.orbitflow.task;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByProjectIdAndArchivedFalse(UUID projectId);
    List<Task> findByColumnIdOrderByRankAsc(UUID columnId);
    long countByColumnIdAndArchivedFalse(UUID columnId);
    Optional<Task> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    List<Task> findByProjectId(UUID projectId);

    @Query("select t from Task t where t.workspace.id = :workspaceId and t.archived = false and (lower(t.title) like lower(concat('%', :q, '%')) or lower(t.description) like lower(concat('%', :q, '%')))")
    List<Task> searchInWorkspace(UUID workspaceId, String q);

    @Query("select t from Task t where t.project.id = :projectId and t.column.id = :columnId and t.archived = false order by t.rank asc")
    List<Task> findColumnTasks(UUID projectId, UUID columnId);

    @Query("SELECT t FROM Task t WHERE t.workspace.id = :wsId AND t.archived = false AND " +
           "(:projectId IS NULL OR t.project.id = :projectId) AND " +
           "(LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           " LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Task> searchTasks(@Param("wsId") UUID wsId, @Param("projectId") UUID projectId, @Param("q") String q, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.archived = false AND t.dueDate IS NOT NULL AND " +
           "t.dueDate >= :start AND t.dueDate <= :end")
    List<Task> findUpcomingDueTasks(@Param("start") Instant start, @Param("end") Instant end, Pageable pageable);
}
