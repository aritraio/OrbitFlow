package com.orbitflow.board;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, UUID> {
    List<BoardColumn> findByBoardIdOrderByRankAsc(UUID boardId);
    long countByBoardIdAndArchivedFalse(UUID boardId);
}
