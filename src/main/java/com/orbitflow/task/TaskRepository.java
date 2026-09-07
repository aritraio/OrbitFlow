package com.orbitflow.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
