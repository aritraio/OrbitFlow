package com.orbitflow.comment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRevisionRepository extends JpaRepository<CommentRevision, UUID> {
}
