package com.orbitflow.board;

import com.orbitflow.common.security.UserPrincipal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BoardController {
    private final BoardService service;

    @GetMapping("/api/v1/boards/{boardId}")
    public BoardService.BoardDto get(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID boardId) {
        return service.getBoard(p.getId(), boardId);
    }

    @GetMapping("/api/v1/projects/{projectId}/board")
    public BoardService.BoardDto byProject(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID projectId) {
        return service.getBoardByProject(p.getId(), projectId);
    }

    @PostMapping("/api/v1/projects/{projectId}/boards")
    @ResponseStatus(HttpStatus.CREATED)
    public BoardService.BoardDto byProjectAlias(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID projectId) {
        return service.getBoardByProject(p.getId(), projectId);
    }

    @PostMapping("/api/v1/boards/{boardId}/columns")
    @ResponseStatus(HttpStatus.CREATED)
    public BoardService.ColumnDto createColumn(@AuthenticationPrincipal UserPrincipal p,
                                               @PathVariable UUID boardId,
                                               @RequestBody Map<String, Object> body) {
        Object wip = body.get("wipLimit");
        Integer wipLimit = wip instanceof Number n ? n.intValue() : null;
        return service.createColumn(p.getId(), boardId,
                (String) body.get("name"), (String) body.getOrDefault("category", "ACTIVE"), wipLimit);
    }

    @PatchMapping("/api/v1/boards/{boardId}/columns/{columnId}")
    public BoardService.ColumnDto updateColumn(@AuthenticationPrincipal UserPrincipal p,
                                               @PathVariable UUID boardId,
                                               @PathVariable UUID columnId,
                                               @RequestBody Map<String, Object> body) {
        Object wip = body.get("wipLimit");
        Integer wipLimit = wip instanceof Number n ? n.intValue() : null;
        return service.updateColumn(p.getId(), boardId, columnId,
                (String) body.get("name"), wipLimit, (String) body.get("category"));
    }
}
