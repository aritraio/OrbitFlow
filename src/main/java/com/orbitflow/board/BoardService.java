package com.orbitflow.board;

import com.orbitflow.common.exception.*;
import com.orbitflow.common.util.LexoRank;
import com.orbitflow.project.Project;
import com.orbitflow.project.ProjectRepository;
import com.orbitflow.project.ProjectService;
import com.orbitflow.task.TaskRepository;
import com.orbitflow.workspace.WorkspaceSecurityPolicy;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoardService {
    private final BoardRepository boards;
    private final BoardColumnRepository columns;
    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final TaskRepository tasks;
    private final WorkspaceSecurityPolicy policy;

    public record ColumnDto(UUID id, UUID boardId, String name, String category, String rank, Integer wipLimit, long version, int taskCount) {}
    public record BoardDto(UUID id, UUID projectId, String name, long revision, List<ColumnDto> columns) {}

    @Transactional(readOnly = true)
    public BoardDto getBoard(UUID requesterId, UUID boardId) {
        Board b = boards.findById(boardId).orElseThrow(() -> new ResourceNotFoundException("Board not found"));
        projectService.requireProjectAccess(requesterId, b.getProject());
        return toDto(b);
    }

    @Transactional(readOnly = true)
    public BoardDto getBoardByProject(UUID requesterId, UUID projectId) {
        Project p = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectService.requireProjectAccess(requesterId, p);
        Board b = boards.findByProjectId(projectId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Board not found"));
        return toDto(b);
    }

    @Transactional
    public ColumnDto createColumn(UUID requesterId, UUID boardId, String name, String category, Integer wipLimit) {
        Board b = boards.findById(boardId).orElseThrow(() -> new ResourceNotFoundException("Board not found"));
        Project p = b.getProject();
        var wm = projectService.requireWorkspaceMember(requesterId, p.getWorkspace().getId());
        if (!policy.canConfigureWorkflow(wm.getRole(), projectService.projectRole(requesterId, p.getId())))
            throw new ForbiddenOperationException("Only managers can configure workflow");
        if ("ARCHIVED".equals(p.getStatus())) throw new BadRequestException("Project is archived");
        List<BoardColumn> existing = columns.findByBoardIdOrderByRankAsc(boardId);
        String rank = existing.isEmpty() ? LexoRank.initial()
                : LexoRank.increment(existing.get(existing.size() - 1).getRank());
        BoardColumn c = BoardColumn.builder().board(b)
                .name(name).category(category != null ? category : "ACTIVE")
                .rank(rank).wipLimit(wipLimit).build();
        columns.save(c);
        bumpRevision(b);
        return toColumnDto(c);
    }

    @Transactional
    public ColumnDto updateColumn(UUID requesterId, UUID boardId, UUID columnId, String name, Integer wipLimit, String category) {
        Board b = boards.findById(boardId).orElseThrow(() -> new ResourceNotFoundException("Board not found"));
        Project p = b.getProject();
        var wm = projectService.requireWorkspaceMember(requesterId, p.getWorkspace().getId());
        if (!policy.canConfigureWorkflow(wm.getRole(), projectService.projectRole(requesterId, p.getId())))
            throw new ForbiddenOperationException("Only managers can configure workflow");
        BoardColumn c = columns.findById(columnId).orElseThrow(() -> new ResourceNotFoundException("Column not found"));
        if (!c.getBoard().getId().equals(boardId)) throw new BadRequestException("Column does not belong to board");
        if (name != null && !name.isBlank()) c.setName(name);
        if (wipLimit != null) {
            if (wipLimit < 0) throw new BadRequestException("WIP limit must be >= 0");
            c.setWipLimit(wipLimit);
        }
        if (category != null) c.setCategory(category);
        columns.save(c);
        bumpRevision(b);
        return toColumnDto(c);
    }

    /** Enforces WIP limit; throws BadRequestException when STRICT and full. Returns warning flag otherwise. */
    public void checkWipLimit(Board board, BoardColumn column) {
        if (column.getWipLimit() == null) return;
        long count = tasks.countByColumnIdAndArchivedFalse(column.getId());
        if (count >= column.getWipLimit()) {
            if ("STRICT".equals(board.getWipEnforcement())) {
                throw new BadRequestException("WIP limit reached for column '" + column.getName() + "' (" + column.getWipLimit() + ")");
            }
        }
    }

    private void bumpRevision(Board b) {
        b.setRevision(b.getRevision() + 1);
        boards.save(b);
    }

    public BoardDto toDto(Board b) {
        List<ColumnDto> cols = columns.findByBoardIdOrderByRankAsc(b.getId()).stream().map(this::toColumnDto).toList();
        return new BoardDto(b.getId(), b.getProject().getId(), b.getName(), b.getRevision(), cols);
    }

    public ColumnDto toColumnDto(BoardColumn c) {
        long count = 0;
        try { count = tasks.countByColumnIdAndArchivedFalse(c.getId()); } catch (Exception ignored) {}
        return new ColumnDto(c.getId(), c.getBoard().getId(), c.getName(), c.getCategory(),
                c.getRank(), c.getWipLimit(), c.getVersion() == null ? 0 : c.getVersion(), (int) count);
    }
}
