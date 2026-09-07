package com.orbitflow.task;

import com.orbitflow.common.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TaskController {
    private final TaskService service;
    private final com.orbitflow.activity.ActivityService activityService;

    @PostMapping("/api/v1/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskService.TaskDto create(@AuthenticationPrincipal UserPrincipal p,
                                      @PathVariable UUID projectId,
                                      @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> aids = (List<String>) body.getOrDefault("assigneeIds", List.of());
        return service.createTask(p.getId(), projectId, new TaskService.CreateTaskRequest(
                (String) body.get("title"), (String) body.get("description"),
                (String) body.getOrDefault("type", "TASK"), (String) body.getOrDefault("priority", "MEDIUM"),
                body.get("columnId") != null ? UUID.fromString(body.get("columnId").toString()) : null,
                aids.stream().map(UUID::fromString).toList(),
                (String) body.get("dueDate"),
                body.get("estimateMinutes") instanceof Number n ? n.intValue() : null));
    }

    @GetMapping("/api/v1/tasks/{taskId}")
    public TaskService.TaskDto get(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID taskId) {
        return service.getTask(p.getId(), taskId);
    }

    @GetMapping("/api/v1/projects/{projectId}/tasks")
    public List<TaskService.TaskDto> list(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID projectId) {
        return service.listProjectTasks(p.getId(), projectId);
    }

    @PatchMapping("/api/v1/tasks/{taskId}")
    public TaskService.TaskDto update(@AuthenticationPrincipal UserPrincipal p,
                                      @PathVariable UUID taskId,
                                      @RequestBody Map<String, Object> patch) {
        Object v = patch.get("expectedVersion");
        long expected = v instanceof Number n ? n.longValue() : 0L;
        return service.updateTask(p.getId(), taskId, patch, expected);
    }

    @PostMapping("/api/v1/tasks/{taskId}/move")
    public TaskService.TaskDto move(@AuthenticationPrincipal UserPrincipal p,
                                    @PathVariable UUID taskId,
                                    @RequestBody Map<String, Object> body) {
        Object v = body.get("expectedVersion");
        long expected = v instanceof Number n ? n.longValue() : 0L;
        UUID dest = UUID.fromString(body.get("destinationColumnId").toString());
        UUID prev = body.get("prevTaskId") != null ? UUID.fromString(body.get("prevTaskId").toString()) : null;
        UUID next = body.get("nextTaskId") != null ? UUID.fromString(body.get("nextTaskId").toString()) : null;
        return service.moveTask(p.getId(), taskId, new TaskService.MoveRequest(dest, prev, next, expected));
    }

    @PostMapping("/api/v1/tasks/{taskId}/archive")
    public Map<String, Object> archive(@AuthenticationPrincipal UserPrincipal p,
                                       @PathVariable UUID taskId,
                                       @RequestBody Map<String, Object> body) {
        service.archiveTask(p.getId(), taskId, Boolean.TRUE.equals(body.get("archived")));
        return Map.of("ok", true);
    }

    @PostMapping("/api/v1/tasks/{taskId}/dependencies")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addDep(@AuthenticationPrincipal UserPrincipal p,
                                      @PathVariable UUID taskId,
                                      @RequestBody Map<String, String> body) {
        return service.addDependency(p.getId(), taskId, UUID.fromString(body.get("dependsOnTaskId")), body.get("kind"));
    }

    @GetMapping("/api/v1/tasks/{taskId}/activity")
    public List<com.orbitflow.activity.ActivityService.ActivityDto> activity(
            @AuthenticationPrincipal UserPrincipal p, @PathVariable UUID taskId) {
        service.getTask(p.getId(), taskId);
        return activityService.timelineForResource("Task", taskId, 50);
    }

    @PostMapping("/api/v1/tasks/{taskId}/watchers/me")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> watch(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID taskId) {
        service.watch(p.getId(), taskId);
        return Map.of("ok", true);
    }

    @DeleteMapping("/api/v1/tasks/{taskId}/watchers/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unwatch(@AuthenticationPrincipal UserPrincipal p, @PathVariable UUID taskId) {
        service.unwatch(p.getId(), taskId);
    }
}
