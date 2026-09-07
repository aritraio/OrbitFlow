package com.orbitflow.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByProjectId(UUID projectId);

    @Query("SELECT d FROM Document d WHERE d.workspace.id = :wsId AND " +
           "(:projectId IS NULL OR d.project.id = :projectId) AND " +
           "(LOWER(d.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           " LOWER(COALESCE(d.currentBodyMarkdown, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Document> searchDocuments(@Param("wsId") UUID wsId, @Param("projectId") UUID projectId, @Param("q") String q, Pageable pageable);
}
