package com.orbitflow.project;

import com.orbitflow.activity.ActivityService;
import com.orbitflow.board.*;
import com.orbitflow.common.exception.*;
import com.orbitflow.identity.User;
import com.orbitflow.identity.UserRepository;
import com.orbitflow.outbox.OutboxService;
import com.orbitflow.workspace.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projects;
    private final ProjectMembershipRepository projectMembers;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository workspaceMembers;
    private final UserRepository users;
    private final BoardRepository boards;
    private final BoardColumnRepository columns;
    private final WorkspaceSecurityPolicy policy;
    private final OutboxService outbox;
    private final ActivityService activity;

    public record CreateProjectRequest(String key, String name, String description, String visibility,
                                       String startDate, String targetDate) {}
    public record ProjectDto(UUID id, UUID workspaceId, String key, String name, String visibility,
                             String status, UUID boardId, long nextTaskNumber) {}

    @Transactional
    public ProjectDto createProject(UUID requesterId, UUID workspaceId, CreateProjectRequest req) {
        WorkspaceMembership m = requireWorkspaceMember(requesterId, workspaceId);
        if (!policy.canCreateProject(m.getRole())) throw new ForbiddenOperationException("Only admins can create projects");
        Workspace ws = workspaces.findById(workspaceId).orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        if (req.key() == null || !req.key().matches("[A-Z][A-Z0-9]{1,9}"))
            throw new BadRequestException("Project key must be 2-10 uppercase alphanumeric chars starting with a letter (e.g. PH)");
        if (projects.existsByWorkspaceIdAndKey(workspaceId, req.key()))
            throw new BadRequestException("Project key already exists in workspace");
        if (req.name() == null || req.name().isBlank()) throw new BadRequestException("Project name required");
        User creator = users.getReferenceById(requesterId);
        Project p = Project.builder()
                .workspace(ws).key(req.key()).name(req.name().trim())
                .description(req.description())
                .visibility(req.visibility() != null ? req.visibility() : "WORKSPACE")
                .createdBy(creator)
                .build();
        if (req.startDate() != null && !req.startDate().isBlank()) p.setStartDate(java.time.LocalDate.parse(req.startDate()));
        if (req.targetDate() != null && !req.targetDate().isBlank()) p.setTargetDate(java.time.LocalDate.parse(req.targetDate()));
        projects.save(p);
        // Default board + columns from template
        Board board = Board.builder().project(p).name(p.getName() + " Board").build();
        boards.save(board);
        String[] names = {"Backlog", "In Progress", "Review", "Done"};
        String[] cats = {"BACKLOG", "ACTIVE", "REVIEW", "DONE"};
        String rank = "a0";
        for (int i = 0; i < names.length; i++) {
            columns.save(BoardColumn.builder().board(board).name(names[i]).category(cats[i]).rank(rank).build());
            rank = rank + "n";
        }
        projectMembers.save(ProjectMembership.builder().project(p).user(creator).role("MANAGER").build());
        outbox.emit("Project", p.getId(), "ProjectCreated",
                "{\"projectId\":\"" + p.getId() + "\",\"workspaceId\":\"" + workspaceId + "\",\"key\":\"" + p.getKey() + "\"}");
        activity.record(ws, p, creator, "PROJECT_CREATED", "Project", p.getId(),
                "{\"key\":\"" + p.getKey() + "\",\"name\":\"" + escape(p.getName()) + "\"}");
        return toDto(p, board.getId());
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> listProjects(UUID requesterId, UUID workspaceId) {
        requireWorkspaceMember(requesterId, workspaceId);
        return projects.findByWorkspaceId(workspaceId).stream()
                .filter(p -> canSeeProject(requesterId, p))
                .map(p -> {
                    UUID boardId = boards.findByProjectId(p.getId()).stream().findFirst().map(Board::getId).orElse(null);
                    return toDto(p, boardId);
                }).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDto getProject(UUID requesterId, UUID projectId) {
        Project p = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        requireProjectAccess(requesterId, p);
        UUID boardId = boards.findByProjectId(p.getId()).stream().findFirst().map(Board::getId).orElse(null);
        return toDto(p, boardId);
    }

    @Transactional
    public ProjectDto archiveProject(UUID requesterId, UUID projectId, boolean archived) {
        Project p = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        WorkspaceMembership m = requireWorkspaceMember(requesterId, p.getWorkspace().getId());
        String pmRole = projectRole(requesterId, projectId);
        if (!policy.canConfigureWorkflow(m.getRole(), pmRole))
            throw new ForbiddenOperationException("Only managers can archive projects");
        p.setStatus(archived ? "ARCHIVED" : "ACTIVE");
        projects.save(p);
        outbox.emit("Project", p.getId(), archived ? "ProjectArchived" : "ProjectRestored",
                "{\"projectId\":\"" + p.getId() + "\"}");
        UUID boardId = boards.findByProjectId(p.getId()).stream().findFirst().map(Board::getId).orElse(null);
        return toDto(p, boardId);
    }

    // ---- access helpers (shared by other services) ----
    @Transactional(readOnly = true)
    public WorkspaceMembership requireWorkspaceMember(UUID userId, UUID workspaceId) {
        WorkspaceMembership m = workspaceMembers.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ForbiddenOperationException("No access to workspace"));
        if (!"ACTIVE".equals(m.getStatus())) throw new ForbiddenOperationException("Membership is not active");
        return m;
    }

    @Transactional(readOnly = true)
    public String projectRole(UUID userId, UUID projectId) {
        return projectMembers.findByProjectIdAndUserId(projectId, userId).map(ProjectMembership::getRole).orElse(null);
    }

    @Transactional(readOnly = true)
    public void requireProjectAccess(UUID userId, Project p) {
        WorkspaceMembership wm = requireWorkspaceMember(userId, p.getWorkspace().getId());
        if ("WORKSPACE".equals(p.getVisibility()) && !"GUEST".equals(wm.getRole())) return;
        String pmRole = projectRole(userId, p.getId());
        if (pmRole == null) throw new ForbiddenOperationException("No access to private project");
    }

    @Transactional(readOnly = true)
    public boolean canSeeProject(UUID userId, Project p) {
        try {
            requireProjectAccess(userId, p);
            return true;
        } catch (ForbiddenOperationException e) {
            return false;
        }
    }

    public ProjectDto toDto(Project p, UUID boardId) {
        return new ProjectDto(p.getId(), p.getWorkspace().getId(), p.getKey(), p.getName(),
                p.getVisibility(), p.getStatus(), boardId, p.getNextTaskNumber());
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\n", " ");
    }
}
