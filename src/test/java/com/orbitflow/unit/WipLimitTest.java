package com.orbitflow.unit;

import com.orbitflow.board.Board;
import com.orbitflow.board.BoardColumn;
import com.orbitflow.board.BoardService;
import com.orbitflow.common.exception.BadRequestException;
import com.orbitflow.project.ProjectService;
import com.orbitflow.task.TaskRepository;
import com.orbitflow.board.BoardRepository;
import com.orbitflow.board.BoardColumnRepository;
import com.orbitflow.project.ProjectRepository;
import com.orbitflow.workspace.WorkspaceSecurityPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WipLimitTest {
    @Mock BoardRepository boards;
    @Mock BoardColumnRepository columns;
    @Mock ProjectRepository projects;
    @Mock ProjectService projectService;
    @Mock TaskRepository tasks;
    @Mock WorkspaceSecurityPolicy policy;

    @InjectMocks BoardService boardService;

    @Test
    void strictWipRejectsWhenFull() {
        Board board = Board.builder().wipEnforcement("STRICT").build();
        BoardColumn col = BoardColumn.builder().name("In Progress").wipLimit(1).build();
        when(tasks.countByColumnIdAndArchivedFalse(any())).thenReturn(1L);
        assertThatThrownBy(() -> boardService.checkWipLimit(board, col))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("WIP limit");
    }

    @Test
    void warningWipAllowsWhenFull() {
        Board board = Board.builder().wipEnforcement("WARNING").build();
        BoardColumn col = BoardColumn.builder().name("In Progress").wipLimit(1).build();
        when(tasks.countByColumnIdAndArchivedFalse(any())).thenReturn(5L);
        assertThatCode(() -> boardService.checkWipLimit(board, col)).doesNotThrowAnyException();
    }

    @Test
    void nullWipLimitAlwaysAllows() {
        Board board = Board.builder().wipEnforcement("STRICT").build();
        BoardColumn col = BoardColumn.builder().name("Backlog").wipLimit(null).build();
        assertThatCode(() -> boardService.checkWipLimit(board, col)).doesNotThrowAnyException();
        verifyNoInteractions(tasks);
    }
}
