package com.orbitflow.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRevisionRepository extends JpaRepository<DocumentRevision, UUID> {
    List<DocumentRevision> findByDocumentIdOrderByRevisionNumberAsc(UUID documentId);
    long countByDocumentId(UUID documentId);
}
