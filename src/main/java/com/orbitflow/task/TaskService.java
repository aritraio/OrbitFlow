package com.orbitflow.task;

import com.orbitflow.activity.ActivityService;
import com.orbitflow.board.*;
import com.orbitflow.common.exception.*;
import com.orbitflow.common.util.LexoRank;
import com.orbitflow.identity.User;
import com.orbitflow.identity.UserRepository;
import com.orbitflow.outbox.OutboxService;
import com.orbitflow.project.*;
import com.orbitflow.workspace.WorkspaceSecurityPolicy;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository tasks;
    private final BoardRepository boards;
    private final BoardColumnRepository columns;
    private final BoardService boardService;
    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final UserRepository users;
    private final WatcherRepository watchers;
    private final LabelRepository labels;
    private final ChecklistRepository checklists;
    private final ChecklistItemRepository checklistItems;
    private final TaskDependencyRepository dependencies;
    private final OutboxService outbox;
    private final ActivityService activity;
    private final EntityManager em;

    public record CreateTaskRequest(String title, String description, String type, String priority,
                                    UUID columnId, List<UUID> assigneeIds, String dueDate, Integer estimateMinutes) {}
    public record TaskDto(UUID id, UUID workspaceId, UUID projectId, UUID columnId, String taskKey, long taskNumber,
                          String title, String description, String type, String priority, String rank,
                          List<UUID> assigneeIds, String dueDate, long version, boolean archived) {}
    public record MoveRequest(UUID destinationColumnId, UUID prevTaskId, UUID nextTaskId, long expectedVersion) {}

    @Transactional
    public TaskDto createTask(UUID requesterId, UUID projectId, CreateTaskRequest req) {
        Project p = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        var wm = projectService.requireWorkspaceMember(requesterId, p.getWorkspace().getId());
        projectService.requireProjectAccess(requesterId, p);
        if ("ARCHIVED".equals(p.getStatus())) throw new BadRequestException("Project is archived");
        if (!projectService.canSeeProject(requesterId, p)) throw new ForbiddenOperationException("No access");
        String pmRole = projectService.projectRole(requesterId, projectId);
        // Guests need explicit project membership
        if ("GUEST".equals(wm.getRole()) && pmRole == null)
            throw new ForbiddenOperationException("Guests need explicit project access");
        if (req.title() == null || req.title().isBlank()) throw new BadRequestException("Title required");

        Board board = boards.findByProjectId(projectId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Board not found"));
        BoardColumn column;
        if (req.columnId() != null) {
            column = columns.findById(req.columnId()).orElseThrow(() -> new ResourceNotFoundException("Column not found"));
            if (!column.getBoard().getId().equals(board.getId())) throw new BadRequestException("Column not in project board");
        } else {
            column = columns.findByBoardIdOrderByRankAsc(board.getId()).stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No columns"));
        }
        boardService.checkWipLimit(board, column);

        // Safe project-scoped task number allocation (pessimistic lock on project row)
        Project locked = em.find(Project.class, projectId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        long number = locked.getNextTaskNumber();
        locked.setNextTaskNumber(number + 1);
        projects.save(locked);

        // Rank: append at end of column
        List<Task> siblings = tasks.findByColumnIdOrderByRankAsc(column.getId());
        String rank = siblings.isEmpty() ? LexoRank.initial() : LexoRank.increment(siblings.get(siblings.size() - 1).getRank());

        User reporter = users.getReferenceById(requesterId);
        Task t = Task.builder()
                .workspace(p.getWorkspace()).project(p).column(column)
                .taskNumber(number).taskKey(p.getKey() + "-" + number)
                .title(req.title().trim()).description(req.description())
                .type(req.type() != null ? req.type() : "TASK")
                .priority(req.priority() != null ? req.priority() : "MEDIUM")
                .reporter(reporter).rank(rank)
                .estimateMinutes(req.estimateMinutes())
                .build();
        if (req.dueDate() != null && !req.dueDate().isBlank()) t.setDueDate(Instant.parse(req.dueDate()));
        tasks.save(t);
        if (req.assigneeIds() != null) {
            for (UUID aid : req.assigneeIds()) {
                User u = users.findById(aid).orElseThrow(() -> new BadRequestException("Assignee not found: " + aid));
                // assignee must be workspace member
                projectService.requireWorkspaceMember(aid, p.getWorkspace().getId());
                t.getAssignees().add(TaskAssignee.builder().task(t).user(u).build());
            }
        }
        tasks.save(t);
        board.setRevision(board.getRevision() + 1);
        boards.save(board);

        User actor = users.getReferenceById(requesterId);
        activity.record(p.getWorkspace(), p, actor, "TASK_CREATED", "Task", t.getId(),
                "{\"taskKey\":\"" + t.getTaskKey() + "\",\"title\":\"" + escape(t.getTitle()) + "\"}");
        outbox.emit("Task", t.getId(), "TaskCreated",
                "{\"taskId\":\"" + t.getId() + "\",\"projectId\":\"" + projectId + "\",\"boardId\":\"" + board.getId() + "\",\"taskKey\":\"" + t.getTaskKey() + "\",\"version\":0}");
        return toDto(t);
    }

    @Transactional(readOnly = true)
    public TaskDto getTask(UUID requesterId, UUID taskId) {
        Task t = tasks.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        projectService.requireProjectAccess(requesterId, t.getProject());
        // workspace scoping: task.workspace must equal project.workspace
        return toDto(t);
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listProjectTasks(UUID requesterId, UUID projectId) {
        Project p = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectService.requireProjectAccess(requesterId, p);
        return tasks.findByProjectIdAndArchivedFalse(projectId).stream().map(this::toDto).toList();
    }

    @Transactional
    public TaskDto updateTask(UUID requesterId, UUID taskId, Map<String, Object> patch, long expectedVersion) {
        Task t = tasks.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        projectService.requireProjectAccess(requesterId, t.getProject());
        if (t.isArchived()) throw new BadRequestException("Task is archived");
        if (!Objects.equals(t.getVersion(), expectedVersion)) {
            throw new ConflictException("Stale version: expected " + expectedVersion + " but was " + t.getVersion(), toDto(t));
        }
        Map<String, String> changes = new LinkedHashMap<>();
        if (patch.containsKey("title")) {
            String v = (String) patch.get("title");
            if (v == null || v.isBlank()) throw new BadRequestException("Title cannot be blank");
            changes.put("title", t.getTitle() + " -> " + v);
            t.setTitle(v.trim());
        }
        if (patch.containsKey("description")) {
            t.setDescription((String) patch.get("description"));
            changes.put("description", "updated");
        }
        if (patch.containsKey("priority")) {
            String v = (String) patch.get("priority");
            changes.put("priority", t.getPriority() + " -> " + v);
            t.setPriority(v);
        }
        if (patch.containsKey("type")) {
            String v = (String) patch.get("type");
            t.setType(v);
            changes.put("type", v);
        }
        if (patch.containsKey("dueDate")) {
            Object v = patch.get("dueDate");
            t.setDueDate(v == null || v.toString().isBlank() ? null : Instant.parse(v.toString()));
            changes.put("dueDate", String.valueOf(v));
        }
        if (patch.containsKey("estimateMinutes")) {
            Object v = patch.get("estimateMinutes");
            t.setEstimateMinutes(v instanceof Number n ? n.intValue() : null);
            changes.put("estimateMinutes", String.valueOf(v));
        }
        try {
            tasks.saveAndFlush(t);
        } catch (jakarta.persistence.OptimisticLockException | org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            Task latest = tasks.findById(taskId).orElseThrow();
            throw new ConflictException("Concurrent modification detected", toDto(latest));
        }
        User actor = users.getReferenceById(requesterId);
        activity.record(t.getWorkspace(), t.getProject(), actor, "TASK_UPDATED", "Task", t.getId(), changes.toString());
        outbox.emit("Task", t.getId(), "TaskUpdated",
                "{\"taskId\":\"" + t.getId() + "\",\"version\":" + t.getVersion() + ",\"changes\":\"" + escape(changes.toString()) + "\"}");
        return toDto(tasks.findById(taskId).orElseThrow());
    }

    @Transactional
    public TaskDto moveTask(UUID requesterId, UUID taskId, MoveRequest req) {
        Task t = tasks.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        Project p = t.getProject();
        projectService.requireProjectAccess(requesterId, p);
        if (t.isArchived() || "ARCHIVED".equals(p.getStatus())) throw new BadRequestException("Task or project is archived");
        if (!Objects.equals(t.getVersion(), req.expectedVersion())) {
            throw new ConflictException("Stale version: expected " + req.expectedVersion() + " but was " + t.getVersion(), toDto(t));
        }
        Board board = boards.findByProjectId(p.getId()).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Board not found"));
        BoardColumn dest = columns.findById(req.destinationColumnId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination column not found"));
        if (!dest.getBoard().getId().equals(board.getId())) throw new BadRequestException("Column not in same board");
        if (dest.isArchived()) throw new BadRequestException("Destination column is archived");
        // Blocked dependencies: cannot move to DONE while BLOCKED_BY deps incomplete (simplified: any dependency blocks DONE)
        if ("DONE".equals(dest.getCategory())) {
            List<TaskDependency> deps = dependencies.findByTaskId(taskId);
            if (!deps.isEmpty()) {
                // Consider blocked if any dependsOn task is not in DONE column
                for (TaskDependency d : deps) {
                    if ("BLOCKED_BY".equals(d.getKind())) {
                        Task dep = d.getDependsOn();
                        if (dep.getColumn() == null || !"DONE".equals(dep.getColumn().getCategory())) {
                            throw new BadRequestException("Task is blocked by " + dep.getTaskKey());
                        }
                    }
                }
            }
        }
        // Workflow rule: require assignee before In Progress (ACTIVE beyond backlog)
        if ("ACTIVE".equals(dest.getCategory()) && t.getAssignees().isEmpty()
                && columns.findByBoardIdOrderByRankAsc(board.getId()).stream()
                    .filter(c -> "BACKLOG".equals(c.getCategory())).map(BoardColumn::getId)
                    .anyMatch(id -> id.equals(t.getColumn() != null ? t.getColumn().getId() : null))) {
            throw new BadRequestException("Assign the task before moving to In Progress");
        }
        // WIP limit (skip if staying in same column)
        boolean sameColumn = t.getColumn() != null && t.getColumn().getId().equals(dest.getId());
        if (!sameColumn) boardService.checkWipLimit(board, dest);

        // Rank between neighbors
        String prevRank = null, nextRank = null;
        if (req.prevTaskId() != null) {
            Task prev = tasks.findById(req.prevTaskId()).orElseThrow(() -> new BadRequestException("prev task not found"));
            if (!dest.getId().equals(prev.getColumn() != null ? prev.getColumn().getId() : null))
                throw new BadRequestException("Neighbor tasks must be in destination column");
            prevRank = prev.getRank();
        }
        if (req.nextTaskId() != null) {
            Task next = tasks.findById(req.nextTaskId()).orElseThrow(() -> new BadRequestException("next task not found"));
            if (!dest.getId().equals(next.getColumn() != null ? next.getColumn().getId() : null))
                throw new BadRequestException("Neighbor tasks must be in destination column");
            nextRank = next.getRank();
        }
        String newRank = LexoRank.between(prevRank, nextRank);
        UUID fromCol = t.getColumn() != null ? t.getColumn().getId() : null;
        t.setColumn(dest);
        t.setRank(newRank);
        try {
            tasks.saveAndFlush(t);
        } catch (jakarta.persistence.OptimisticLockException | org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            Task latest = tasks.findById(taskId).orElseThrow();
            throw new ConflictException("Concurrent modification detected", toDto(latest));
        }
        board.setRevision(board.getRevision() + 1);
        boards.save(board);

        // Rebalance detection
        List<Task> colTasks = tasks.findByColumnIdOrderByRankAsc(dest.getId());
        if (LexoRank.needsRebalance(colTasks.stream().map(Task::getRank).toList())) {
            List<String> fresh = LexoRank.rebalance(colTasks.size());
            for (int i = 0; i < colTasks.size(); i++) {
                colTasks.get(i).setRank(fresh.get(i));
                tasks.save(colTasks.get(i));
            }
        }

        User actor = users.getReferenceById(requesterId);
        activity.record(t.getWorkspace(), p, actor, "TASK_MOVED", "Task", t.getId(),
                "{\"from\":\"" + fromCol + "\",\"to\":\"" + dest.getId() + "\",\"taskKey\":\"" + t.getTaskKey() + "\"}");
        outbox.emit("Task", t.getId(), "TaskMoved",
                "{\"taskId\":\"" + t.getId() + "\",\"boardId\":\"" + board.getId() + "\",\"columnId\":\"" + dest.getId() + "\",\"version\":" + t.getVersion() + ",\"rank\":\"" + newRank + "\"}");
        return toDto(tasks.findById(taskId).orElseThrow());
    }

    @Transactional
    public void archiveTask(UUID requesterId, UUID taskId, boolean archived) {
        Task t = tasks.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        var wm = projectService.requireWorkspaceMember(requesterId, t.getWorkspace().getId());
        projectService.requireProjectAccess(requesterId, t.getProject());
        String pmRole = projectService.projectRole(requesterId, t.getProject().getId());
        boolean own = t.getReporter() != null && t.getReporter().getId().equals(requesterId);
        WorkspaceSecurityPolicy pol = new WorkspaceSecurityPolicy();
        if (!pol.canDeleteTask(wm.getRole(), pmRole, own)) throw new ForbiddenOperationException("Cannot archive task");
        t.setArchived(archived);
        tasks.save(t);
        outbox.emit("Task", t.getId(), archived ? "TaskArchived" : "TaskRestored",
                "{\"taskId\":\"" + t.getId() + "\"}");
    }

    @Transactional
    public Map<String, Object> addDependency(UUID requesterId, UUID taskId, UUID dependsOnId, String kind) {
        if (taskId.equals(dependsOnId)) throw new BadRequestException("A task cannot depend on itself");
        Task t = tasks.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        Task dep = tasks.findById(dependsOnId).orElseThrow(() -> new ResourceNotFoundException("Dependency task not found"));
        projectService.requireProjectAccess(requesterId, t.getProject());
        if (!t.getProject().getId().equals(dep.getProject().getId()))
            throw new BadRequestException("Dependencies must be within the same project");
        if (dependencies.existsByTaskIdAndDependsOnId(taskId, dependsOnId))
            throw new BadRequestException("Dependency already exists");
        // Cycle detection (BFS from dependsOn following its dependencies)
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>(List.of(dependsOnId));
        while (!stack.isEmpty()) {
            UUID cur = stack.pop();
            if (cur.equals(taskId)) throw new BadRequestException("Dependency cycle detected");
            if (!visited.add(cur)) continue;
            for (TaskDependency d : dependencies.findByTaskId(cur)) stack.push(d.getDependsOn().getId());
        }
        TaskDependency d = dependencies.save(TaskDependency.builder().task(t).dependsOn(dep)
                .kind(kind != null ? kind : "BLOCKED_BY").build());
        return Map.of("id", d.getId().toString(), "taskId", taskId.toString(), "dependsOn", dependsOnId.toString());
    }

    @Transactional
    public void watch(UUID requesterId, UUID taskId) {
        Task t = tasks.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        projectService.requireProjectAccess(requesterId, t.getProject());
        Watcher.WatcherId id = new Watcher.WatcherId(t.getId(), requesterId);
        if (!watchers.existsById(id)) {
            watchers.save(Watcher.builder().task(t).user(users.getReferenceById(requesterId)).build());
        }
    }

    @Transactional
    public void unwatch(UUID requesterId, UUID taskId) {
        watchers.deleteById(new Watcher.WatcherId(taskId, requesterId));
    }

    public TaskDto toDto(Task t) {
        List<UUID> aids = t.getAssignees().stream().map(a -> a.getUser().getId()).toList();
        return new TaskDto(t.getId(),
                t.getWorkspace() != null ? t.getWorkspace().getId() : null,
                t.getProject() != null ? t.getProject().getId() : null,
                t.getColumn() != null ? t.getColumn().getId() : null,
                t.getTaskKey(), t.getTaskNumber(), t.getTitle(), t.getDescription(),
                t.getType(), t.getPriority(), t.getRank(), aids,
                t.getDueDate() != null ? t.getDueDate().toString() : null,
                t.getVersion() == null ? 0 : t.getVersion(), t.isArchived());
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\n", " ");
    }
}
