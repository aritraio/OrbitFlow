# Collaborative Project Hub

## 1. Project Summary

Build a multi-user project collaboration platform where teams organize work into workspaces, projects, Kanban boards, tasks, comments, documents, and notifications. Changes should appear in near real time, users should have role-based permissions, and every important mutation should be recorded in an activity timeline.

The project is similar in spirit to a focused combination of Trello, Jira, and lightweight team documentation. It is an advanced Java application that demonstrates authorization, concurrency control, WebSockets, search, asynchronous notifications, file handling, and complex workflows without requiring microservices.

## 2. Problem Statement

Small teams often spread plans across chat messages, spreadsheets, and disconnected documents. Context becomes difficult to find, ownership is unclear, and project status is based on manual updates. The Collaborative Project Hub provides one structured workspace in which teams can:

- Plan projects using boards and tasks.
- Assign responsibilities and deadlines.
- Discuss work in context.
- Share project documents and attachments.
- Receive relevant notifications without excessive noise.
- Search across project content.
- Review an accurate history of what changed and why.

## 3. User Roles

### Platform administrator

- Manages platform-level configuration and abuse reports.
- Can suspend workspaces or users under a documented support policy.
- Does not automatically participate in private projects.

### Workspace owner

- Controls workspace settings, billing placeholder, membership, retention, and deletion.
- Can appoint administrators.

### Workspace administrator

- Invites and removes members.
- Creates projects and manages project membership.
- Configures workspace-level labels and templates.

### Project manager

- Configures a project, board columns, workflow rules, and project members.
- Creates sprints or milestones and reviews reports.

### Member

- Creates and edits permitted tasks, comments, and documents.
- Can be assigned work.

### Guest

- Has access only to explicitly shared projects or tasks.
- Cannot discover other workspace content.

Permissions must be evaluated on the server for each operation. Hiding a button in the frontend is not authorization.

## 4. Core Objectives

1. Provide a responsive team workflow with near-real-time updates.
2. Enforce workspace and project isolation rigorously.
3. Handle concurrent edits without silently overwriting another user's work.
4. Keep project history searchable and auditable.
5. Avoid notification overload through preferences, batching, and deduplication.
6. Build clear module boundaries that could later be extracted if scale requires it.

## 5. Recommended Technology Stack

### Backend

- Java 21
- Spring Boot 3
- Spring MVC for REST APIs
- Spring Security with JWT or secure session cookies
- Spring Data JPA and PostgreSQL
- Spring WebSocket with STOMP for real-time events
- Redis for presence, short-lived caching, and distributed WebSocket coordination
- Flyway for schema migrations
- Hibernate Search with OpenSearch, or PostgreSQL full-text search for the first version
- Quartz or Spring Scheduling for reminders and digest jobs
- Object storage such as MinIO locally and S3 in production

### Frontend

- React with TypeScript, Vue, or Angular
- A drag-and-drop library for board interactions
- WebSocket client with reconnect and event-resume behavior
- Markdown editor for descriptions and documents

### Engineering tools

- Gradle or Maven
- Docker Compose
- JUnit 5, Mockito, AssertJ, Testcontainers
- REST Assured or MockMvc
- Playwright or Cypress for end-to-end testing
- OpenAPI
- Micrometer, Prometheus, and structured logs

## 6. Architecture

Use a modular monolith with domain events and an outbox for durable side effects.

```text
com.example.projecthub
├── identity
├── workspace
├── membership
├── project
├── board
├── task
├── comment
├── document
├── attachment
├── search
├── notification
├── realtime
├── activity
├── reporting
└── shared
```

### Request flow

```text
Client request
  -> Controller and DTO validation
  -> Authentication and authorization policy
  -> Application use case
  -> Domain rules
  -> Repository transaction
  -> Domain event + outbox record
  -> Asynchronous handlers
       -> WebSocket event
       -> Notification
       -> Search-index update
       -> Activity projection
```

### Design principles

- Generate tenant-aware queries using `workspaceId` and resource ID together.
- Use optimistic locking on tasks, documents, and board configuration.
- Use rank values rather than rewriting every card position during drag-and-drop.
- Treat notifications and search indexing as eventually consistent.
- Keep core task mutations strongly consistent in PostgreSQL.
- Use an outbox worker so committed changes are not lost between the database and asynchronous handlers.

## 7. Functional Scope

### 7.1 Identity and profile

- Registration, email verification, login, logout, and password reset.
- User profile with display name, avatar, timezone, language, and working hours.
- Session/device management.
- Optional two-factor authentication as an advanced feature.

### 7.2 Workspace lifecycle

- Create and rename a workspace.
- Assign a unique slug.
- Configure default timezone, visibility, and retention settings.
- Invite users by email with expiring, single-use invitations.
- Change member roles subject to role hierarchy.
- Deactivate a member while preserving task and activity history.
- Transfer workspace ownership safely.
- Export or delete workspace data using a deliberate confirmation process.

### 7.3 Projects

- Create public-to-workspace, private, or guest-accessible projects.
- Define project key, name, description, icon, start date, and target date.
- Add selected members and project-specific roles.
- Archive and restore projects.
- Use a project template to create standard columns, labels, and starter tasks.

### 7.4 Boards and columns

- Create one or more boards per project.
- Add, rename, reorder, and archive columns.
- Configure work-in-progress limits per column.
- Mark semantic column categories such as backlog, in progress, review, and done.
- Drag tasks within and between columns.
- Apply workflow restrictions, such as requiring an assignee before entering `In Progress`.

### 7.5 Tasks

A task can include:

- Human-readable project key such as `PH-142`.
- Title and Markdown description.
- Type: task, bug, story, research, or custom type.
- Priority.
- Reporter and one or more assignees.
- Column/status.
- Start date and due date.
- Estimate and time spent.
- Labels.
- Checklist items.
- Parent task and subtasks.
- Dependencies such as blocked by or relates to.
- Attachments.
- Watchers.
- Version number for concurrent editing.

### 7.6 Comments and mentions

- Add, edit, and soft-delete Markdown comments.
- Mention workspace members using `@name`.
- Notify mentioned users once.
- Support threaded replies optionally.
- Preserve an edit marker and previous version in audit history.
- Reject mentions of users who cannot access the task.

### 7.7 Documents

- Create project-scoped Markdown documents.
- Organize documents into a simple hierarchy.
- Autosave drafts.
- Maintain revision history.
- Compare or restore prior versions.
- Link tasks using project keys.
- Resolve links only when the viewer has access to the target.

### 7.8 Attachments

- Upload task, comment, or document attachments.
- Use pre-signed object-storage upload URLs for larger files.
- Track pending, uploaded, scanned, active, and quarantined states.
- Validate file type and size.
- Scan uploaded files before making them downloadable.
- Authorize every download; never rely only on an unguessable URL.

### 7.9 Search

- Search tasks, projects, comments, and documents within accessible workspaces.
- Filter by project, assignee, reporter, label, status, priority, and date.
- Highlight matching terms.
- Offer recent searches.
- Enforce access rules both when indexing and when returning results.

### 7.10 Notifications

Notification causes include:

- Assignment.
- Mention.
- Comment on watched task.
- Task moved to a watched status.
- Approaching or missed due date.
- Invitation or membership change.
- Dependency becoming unblocked.

Delivery channels:

- In-app notification center.
- Email immediately or as a digest.
- Optional webhook integration.

Users can configure channel and event preferences at workspace or project level.

### 7.11 Activity timeline

Record events such as:

- Task created, edited, moved, assigned, archived, or restored.
- Comment created or edited.
- Project membership changed.
- Board workflow changed.
- Document revision published.

Each event includes actor, timestamp, resource, workspace, event type, and sanitized before/after details. Sensitive values and attachment internals must not appear in public activity text.

### 7.12 Reporting

- Tasks by status and priority.
- Work assigned per member.
- Created versus completed trend.
- Cycle time from start to completion.
- Overdue task report.
- Cumulative flow diagram.
- Milestone progress.

Reports must explain their date range, timezone, and treatment of archived tasks.

## 8. Domain Model

### `Workspace`

- `id: UUID`
- `name: String`
- `slug: String` unique
- `ownerId: UUID`
- `timezone: String`
- `status: ACTIVE | SUSPENDED | DELETION_PENDING`
- `version: long`

### `WorkspaceMembership`

- `workspaceId: UUID`
- `userId: UUID`
- `role: OWNER | ADMIN | MEMBER | GUEST`
- `status: INVITED | ACTIVE | DEACTIVATED`
- `joinedAt: Instant?`

Use a unique constraint on `(workspace_id, user_id)`.

### `Project`

- `id: UUID`
- `workspaceId: UUID`
- `key: String`
- `name: String`
- `visibility: WORKSPACE | PRIVATE`
- `status: ACTIVE | ARCHIVED`
- `nextTaskNumber: long`

Use a unique constraint on `(workspace_id, key)`.

### `BoardColumn`

- `id: UUID`
- `boardId: UUID`
- `name: String`
- `category: BACKLOG | ACTIVE | REVIEW | DONE`
- `rank: String` or numeric rank
- `wipLimit: Integer?`
- `archived: boolean`

### `Task`

- `id: UUID`
- `workspaceId: UUID`
- `projectId: UUID`
- `taskNumber: long`
- `columnId: UUID`
- `title: String`
- `description: String`
- `type`, `priority`
- `reporterId: UUID`
- `rank: String`
- `startDate`, `dueDate`
- `estimateMinutes`, `spentMinutes`
- `archived: boolean`
- `version: long`
- `createdAt`, `updatedAt`

### Additional entities

`User`, `Session`, `Invitation`, `ProjectMembership`, `Board`, `TaskAssignee`, `Label`, `TaskLabel`, `Checklist`, `ChecklistItem`, `TaskDependency`, `Comment`, `CommentRevision`, `Document`, `DocumentRevision`, `Attachment`, `Watcher`, `Notification`, `NotificationPreference`, `ActivityEvent`, `OutboxEvent`, and `WebhookSubscription`.

## 9. Authorization Matrix

| Action | Owner/Admin | Project Manager | Member | Guest |
|---|---:|---:|---:|---:|
| Invite workspace member | Yes | No | No | No |
| Create project | Yes | Optional policy | No | No |
| Configure project workflow | Yes | Yes | No | No |
| Create task | Yes | Yes | Yes | Configurable |
| Move task | Yes | Yes | Yes | Configurable |
| Delete/archive task | Yes | Yes | Own task if policy allows | No |
| View private project | If member | If member | If member | Only explicit access |
| Manage workspace deletion | Owner only | No | No | No |

Implement these rules as centralized authorization policies so controllers, WebSockets, and background operations apply the same decisions.

## 10. Suggested REST and WebSocket Interfaces

### REST endpoints

- `POST /api/v1/workspaces`
- `GET /api/v1/workspaces/{workspaceId}`
- `POST /api/v1/workspaces/{workspaceId}/invitations`
- `POST /api/v1/invitations/{token}/accept`
- `PATCH /api/v1/workspaces/{workspaceId}/members/{userId}`
- `POST /api/v1/workspaces/{workspaceId}/projects`
- `POST /api/v1/projects/{projectId}/boards`
- `PATCH /api/v1/boards/{boardId}/columns/{columnId}`
- `POST /api/v1/projects/{projectId}/tasks`
- `GET /api/v1/tasks/{taskId}`
- `PATCH /api/v1/tasks/{taskId}`
- `POST /api/v1/tasks/{taskId}/move`
- `POST /api/v1/tasks/{taskId}/comments`
- `POST /api/v1/tasks/{taskId}/watchers/me`
- `POST /api/v1/projects/{projectId}/documents`
- `GET /api/v1/search?q=...`
- `GET /api/v1/notifications`
- `POST /api/v1/notifications/read`

### WebSocket destinations

Clients subscribe only after authentication and authorization:

- `/topic/workspaces/{workspaceId}`
- `/topic/projects/{projectId}`
- `/topic/boards/{boardId}`
- `/user/queue/notifications`

Events include an event ID, resource ID, event type, new version, timestamp, and minimal payload. Clients refetch a resource if they detect a version gap.

## 11. Detailed Workflows

### Workflow A: Create a workspace and invite a team

1. An authenticated user submits workspace name and preferred slug.
2. The service normalizes and checks slug availability.
3. In one transaction, it creates the workspace and an owner membership.
4. Default labels, one project template, and notification preferences are initialized.
5. The owner enters invitee emails and selected roles.
6. For each valid email, the service creates or refreshes a pending invitation without creating duplicate active memberships.
7. It stores only a hash of the invitation token and writes an email event to the outbox.
8. The email worker sends an expiring link.
9. The recipient signs in or creates an account and presents the token.
10. The service verifies token status, email match, expiration, workspace status, and seat policy.
11. It creates an active membership and consumes the invitation atomically.
12. A membership event updates the activity feed and sends a real-time update to administrators.

### Workflow B: Create a project from a template

1. An authorized user chooses a template and enters project name, key, dates, and visibility.
2. The server validates that the key format is allowed and unique within the workspace.
3. The project, default board, columns, labels, and starter documents are created in one transaction.
4. Selected project memberships are inserted only for active workspace members.
5. The service publishes `ProjectCreated` with generated resource identifiers.
6. Search indexing and activity generation occur asynchronously.
7. The project response includes the initialized board so the UI can navigate immediately.

### Workflow C: Create and assign a task

1. The member opens a project and selects `Create task`.
2. The UI provides allowed task types, labels, members, and columns.
3. The user enters title, description, priority, assignees, estimate, and due date.
4. The server checks project access and validates every referenced resource belongs to the same workspace/project.
5. A project-scoped task number is allocated safely under concurrent requests.
6. The task, assignees, labels, and initial activity event are saved atomically.
7. Assignment and mention candidates are deduplicated.
8. An outbox event triggers search indexing, WebSocket broadcast, and notifications.
9. The creator receives `201 Created` with task key and version.
10. Connected board clients add the card unless they have already processed the event ID.

### Workflow D: Drag a task to another column

1. The user drags a card and the client calculates neighboring card IDs in the destination column.
2. The client sends task ID, destination column, neighbor IDs, expected task version, and operation ID.
3. The server authorizes project access and verifies that all IDs belong to the same board.
4. Workflow policies run: WIP limit, required fields, blocked dependencies, and role restrictions.
5. A new rank is calculated between neighboring ranks.
6. The update uses optimistic locking; if the version is stale, the server returns `409 Conflict` with current state.
7. On success, task location, rank, version, and activity event commit atomically.
8. A WebSocket event is sent after commit.
9. Other clients update the card if the event version is newer than their local version.
10. If ranks become too dense, a background or controlled rebalance renumbers one column without changing visible order.

### Workflow E: Concurrent task editing

1. User A and User B both open task version 12.
2. User A changes priority and saves with expected version 12.
3. The database updates the task to version 13.
4. User B changes the description and submits expected version 12.
5. The update affects zero rows or raises an optimistic-lock exception.
6. The API returns a conflict containing the latest version and safe summary of changed fields.
7. The UI lets User B compare, merge, or discard local changes.
8. A merged request uses version 13, preventing silent loss of User A's update.

### Workflow F: Comment with mentions

1. A user submits Markdown containing one or more mentions.
2. The server sanitizes rendered output and extracts mention identifiers.
3. It verifies task access and verifies each mentioned user can access the task.
4. The comment is saved with immutable author and creation time.
5. An activity event and outbox event are saved in the same transaction.
6. Notification logic combines mention, assignment, and watcher reasons so one user does not receive duplicate messages.
7. The author is excluded from self-notification by default.
8. Online recipients receive an in-app event; email delivery follows their preferences.
9. Editing the comment reruns mention detection and only notifies newly mentioned users.

### Workflow G: Attachment upload

1. The client requests an upload reservation with file name, size, and media type.
2. The server validates permissions and limits, then creates a `PENDING` attachment record.
3. It returns a short-lived pre-signed object-storage URL.
4. The client uploads directly to object storage.
5. The client confirms completion using the attachment ID.
6. The server verifies object existence, size, checksum, and metadata.
7. The attachment becomes `SCANNING`, and a scan job is queued.
8. A clean result changes status to `ACTIVE`; a suspicious result changes it to `QUARANTINED` and alerts administrators.
9. Download requests pass authorization and return a short-lived download URL only for active files.
10. Abandoned pending uploads are removed by a scheduled cleanup job.

### Workflow H: Real-time board synchronization

1. The client obtains initial board state over REST, including a board revision and task versions.
2. It authenticates the WebSocket connection and subscribes to an authorized board topic.
3. Each committed board mutation creates a monotonically identifiable event.
4. The outbox dispatcher publishes the event to the WebSocket infrastructure.
5. Each client tracks recently processed event IDs to ignore duplicates.
6. Events with expected versions update local state directly.
7. A missed event or revision gap causes the client to request a fresh board snapshot.
8. During reconnect, the UI shows synchronization status rather than pretending it is current.
9. Unauthorized membership changes terminate affected subscriptions promptly.

### Workflow I: Due-date reminders and notification digests

1. A scheduled job runs in controlled batches.
2. It selects incomplete tasks approaching due dates using each workspace/user timezone.
3. It checks assignment, watcher status, and notification preferences.
4. It creates deduplicated notification records keyed by task, user, reminder type, and due-date version.
5. Immediate in-app events are delivered to active users.
6. Email-digest jobs group pending items by recipient and workspace.
7. Successfully delivered notifications record channel and delivery timestamp.
8. Failed emails retry with exponential backoff and eventually move to a dead-letter state.
9. Changing a due date invalidates obsolete scheduled reminders.

### Workflow J: Search indexing and query

1. A task, comment, or document mutation commits with an outbox event.
2. The indexing worker builds a sanitized search document containing workspace and access-scope fields.
3. The index stores searchable text and filterable metadata.
4. A user submits a query with filters.
5. The search service limits candidates to workspaces and projects the user can access.
6. Returned IDs are optionally reauthorized against the database to handle recently revoked access.
7. Results include snippets and stable resource links.
8. Deleted or archived resources are removed or updated asynchronously.
9. A reconciliation job compares database update times with index versions and repairs missed events.

### Workflow K: Complete a milestone and generate reports

1. A project manager opens a milestone dashboard.
2. The reporting service computes total, completed, overdue, and blocked task counts.
3. Cycle-time calculations use activity timestamps rather than mutable current values.
4. The manager drills into incomplete work and updates scope if necessary.
5. Completing the milestone records its actual completion date and freezes a summary snapshot.
6. Remaining tasks are moved to another milestone only through an explicit bulk action.
7. The generated report states filters, timezone, and generation timestamp.
8. Historical snapshots remain stable while live project reports continue to evolve.

### Workflow L: Remove a workspace member

1. An administrator requests member deactivation.
2. The service checks role hierarchy and prevents removal of the sole owner.
3. It displays the member's assigned open tasks and asks for an optional replacement assignee.
4. In one transaction, membership is deactivated and selected tasks are reassigned or unassigned.
5. Active sessions for that workspace are invalidated or their authorization cache is evicted.
6. Future WebSocket subscriptions and API requests are rejected immediately.
7. Authored comments, activity events, and task history remain attributed to the deactivated identity.
8. Notifications and pending invitations are canceled as applicable.

## 12. Important Business Rules

- A project resource must always belong to the same workspace as its parent.
- The sole workspace owner cannot leave until ownership is transferred.
- Guests cannot enumerate workspace members beyond what their shared resources require.
- A task cannot depend on itself; dependency cycles should be rejected.
- Done tasks may be reopened only if project policy permits it.
- WIP limits may be strict or warning-only, configured per board.
- Archived projects are read-only except for restore and administrative operations.
- Timestamps are stored in UTC and rendered in the user's timezone.
- Activity events are append-only; corrections create new events.

## 13. Security Requirements

- Enforce tenant-aware authorization on REST, WebSocket subscriptions, downloads, search, and background jobs.
- Sanitize Markdown output to prevent stored XSS.
- Validate and scan file uploads.
- Rate-limit login, invitation, search, upload reservation, and webhook operations.
- Store invitation/reset token hashes, not raw tokens.
- Sign outgoing webhooks and protect against SSRF by validating destinations.
- Redact secrets, tokens, private descriptions, and file URLs from logs.
- Apply CSRF protection when cookie-based authentication is used.
- Prevent insecure direct object references by never authorizing solely from a resource UUID.
- Record security-relevant membership and role changes in an immutable audit log.

## 14. Failure Handling and Reliability

- Return a consistent problem-details response with trace ID and stable error code.
- Use idempotency keys for task creation, moves, and invitation requests where retries are likely.
- Retry asynchronous delivery with bounded exponential backoff.
- Move repeatedly failing outbox events to a visible dead-letter state.
- Make event consumers idempotent using event IDs.
- Expose metrics for outbox lag, WebSocket connections, notification failures, indexing delay, and API latency.
- Degrade gracefully: if Redis or search is unavailable, core task CRUD should remain available where safe.

## 15. Testing Strategy

### Unit tests

- Permission policies and role hierarchy.
- Rank calculation and rebalance.
- WIP and transition rules.
- Mention extraction and deduplication.
- Due-date calculations across timezones and daylight-saving changes.
- Dependency-cycle detection.
- Notification preference resolution.

### Integration tests

- PostgreSQL repositories with tenant isolation.
- Optimistic-lock conflicts.
- Outbox persistence and idempotent consumption.
- Redis-backed presence behavior.
- Search indexing and access filtering.
- Object-storage upload lifecycle using MinIO.

### API and security tests

- Attempt cross-workspace access for every resource type.
- Verify guest restrictions.
- Verify archived-resource behavior.
- Test malformed Markdown, dangerous links, and attachment metadata.
- Verify WebSocket topic authorization before and after membership revocation.

### End-to-end scenarios

- Create workspace, invite member, and collaborate on a task.
- Two browsers concurrently edit and resolve a conflict.
- Drag cards while another client receives updates.
- Mention a member and receive one notification.
- Upload, scan, and download an attachment.
- Deactivate a member and verify immediate access loss.

### Performance tests

- Board with thousands of archived and hundreds of active tasks.
- Workspace search with large comment/document history.
- WebSocket fan-out to many connected members.
- Digest generation for a large workspace.

## 16. Implementation Roadmap

### Phase 1: Foundation and identity

- Bootstrap Spring Boot, PostgreSQL, Flyway, Docker Compose, and CI.
- Implement identity, session security, error format, and observability.
- Establish workspace-aware repository and authorization patterns.

### Phase 2: Workspace and project management

- Add workspaces, memberships, invitations, projects, and roles.
- Add comprehensive tenant-isolation tests before proceeding.

### Phase 3: Core boards and tasks

- Implement boards, columns, tasks, labels, assignees, and rank-based movement.
- Add optimistic locking and activity events.
- Deliver a usable Kanban UI.

### Phase 4: Collaboration

- Add comments, mentions, watchers, checklists, dependencies, and notifications.
- Add outbox dispatch and email-development tooling.

### Phase 5: Real-time behavior

- Add authenticated WebSockets, event deduplication, reconnect behavior, and presence.
- Test concurrent board usage with multiple clients.

### Phase 6: Documents, attachments, and search

- Add versioned documents and object-storage attachments.
- Add PostgreSQL full-text search first; migrate to OpenSearch only if justified.

### Phase 7: Reporting and production hardening

- Add milestone and flow reports.
- Add rate limits, audit review, backup/restore documentation, load tests, and operational dashboards.

## 17. Optional Advanced Features

- Sprint planning and velocity charts.
- Automation rules: when status changes, assign a user or add a label.
- Signed webhooks and integrations with GitHub or Slack.
- Personal API tokens with explicit scopes.
- Offline-first board editing with a synchronization queue.
- Organization-level SSO using OIDC.
- Custom task fields and workflow builders.
- Time tracking and approval workflows.

## 18. Definition of Done

The first production-quality version is complete when:

- Teams can securely create isolated workspaces and projects.
- Invited users can collaborate according to their exact roles.
- Tasks can be created, assigned, moved, discussed, and searched.
- Simultaneous edits never silently overwrite one another.
- Connected clients receive authorized real-time updates and recover from gaps.
- Notifications are preference-aware, deduplicated, and retryable.
- Cross-workspace data leakage is covered by automated tests.
- Uploaded files are private and pass through a safe lifecycle.
- Database migrations, local startup, API documentation, metrics, and CI are documented and repeatable.

## 19. Learning Outcomes

This project demonstrates sophisticated Spring Security authorization, multi-tenant data design, WebSockets, optimistic concurrency, event-driven side effects, durable outbox processing, search, object storage, real-time UI synchronization, auditability, and production-grade testing.
