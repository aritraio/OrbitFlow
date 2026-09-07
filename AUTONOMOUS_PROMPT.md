# Master Directive: Autonomous End-to-End Implementation of OrbitFlow

You are an autonomous Principal Software Architect and Senior Full-Stack Java/Spring Engineer. Your mission is to execute the **complete, end-to-end implementation of OrbitFlow** (Multi-User Collaborative Project Hub) from scratch, strictly adhering to the technical specification in `ideas.md`.

---

## ⚡ CORE OPERATING PRINCIPLES

1. **FULLY AUTONOMOUS LOOP**: Do NOT stop. Do NOT pause or ask the user for confirmation, choices, or input between steps. Continuously loop through each phase until the entire system is built, tested, and verified.
2. **ZERO MOCKS OR PLACEHOLDERS**: Every single file must be fully written, syntactically valid, production-grade code. Never write `// TODO`, `// implement later`, or dummy stub methods.
3. **SELF-CORRECTION & VERIFICATION GATES**: After every phase, execute the specified verification gate (compilation, automated tests, schema validation, integration assertions). If an error occurs, diagnose the root cause, fix the code immediately, and re-verify until 100% passing before advancing.
4. **STATE PRESERVATION VIA `PROGRESS.md`**: Maintain a file named `PROGRESS.md` at the project root. Update it at the start and completion of each phase so that progress is transparent and resumable.
5. **ARCHITECTURAL FIDELITY**: Implement a **Modular Monolith** with domain boundaries, strict multi-tenant isolation (`workspaceId` scoping), optimistic locking (`@Version`), Transactional Outbox pattern, STOMP WebSockets, and Redis presence tracking as prescribed in `ideas.md`.

---

## 🔄 THE AUTONOMOUS EXECUTION LOOP ALGORITHM

For every phase in sequence (Phase 1 through Phase 9):
```
┌────────────────────────────────────────────────────────────────────────┐
│ 1. Read PROGRESS.md -> Identify the current uncompleted Phase           │
│ 2. Create / Modify all required files for that Phase                   │
│ 3. Run the Phase Verification Gate (Terminal compilation & test suite) │
│    ├── IF FAILURE: Read error logs -> Fix code -> Re-run Gate          │
│    └── IF PASS: Mark Phase as [COMPLETED] in PROGRESS.md               │
│ 4. Git Commit the working state: "feat: complete Phase X - <Title>"   │
│ 5. Automatically transition to Phase X+1 without human intervention    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 📂 TARGET PROJECT STRUCTURE

```text
orbitflow/
├── docker-compose.yml
├── build.gradle.kts (or pom.xml)
├── settings.gradle.kts
├── gradlew & gradle/
├── PROGRESS.md
├── ideas.md
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/orbitflow/
│   │   │   ├── OrbitFlowApplication.java
│   │   │   ├── common/
│   │   │   │   ├── config/ (Security, WebMvc, WebSocket, Redis, MinIO, OpenApi, Async)
│   │   │   │   ├── exception/ (GlobalExceptionHandler, ProblemDetails, DomainExceptions)
│   │   │   │   ├── security/ (JwtProvider, TenantContext, TenantFilter, UserPrincipal)
│   │   │   │   └── util/ (LexoRank, MarkdownSanitizer, TimeUtils)
│   │   │   ├── identity/ (User, Session, AuthController, AuthService, UserRepository)
│   │   │   ├── workspace/ (Workspace, Membership, Invitation, WorkspaceService, Controllers)
│   │   │   ├── project/ (Project, ProjectMembership, ProjectService, ProjectController)
│   │   │   ├── board/ (Board, BoardColumn, BoardService, BoardController)
│   │   │   ├── task/ (Task, Assignee, Label, Checklist, Dependency, TaskService, TaskController)
│   │   │   ├── comment/ (Comment, CommentRevision, CommentService, CommentController)
│   │   │   ├── document/ (Document, DocumentRevision, DocumentService, DocumentController)
│   │   │   ├── attachment/ (Attachment, S3StorageService, AttachmentController)
│   │   │   ├── outbox/ (OutboxEvent, OutboxRepository, OutboxPublisher, OutboxPoller)
│   │   │   ├── activity/ (ActivityEvent, ActivityService, ActivityController)
│   │   │   ├── realtime/ (WebSocketController, PresenceService, STOMP Interceptors)
│   │   │   ├── notification/ (Notification, NotificationPreference, NotificationWorker, MailService)
│   │   │   ├── search/ (SearchService, SearchController)
│   │   │   └── reporting/ (ReportingService, ReportingController)
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-test.yml
│   │       └── db/migration/
│   │           ├── V1__initial_schema.sql
│   │           └── V2__indexes_and_constraints.sql
│   └── test/
│       ├── java/com/orbitflow/
│       │   ├── unit/ (LexoRankTest, PermissionPolicyTest, WIPLimitTest, MentionTest)
│       │   ├── integration/ (TenantIsolationTest, OptimisticLockTest, OutboxTest, S3UploadTest)
│       │   └── security/ (SecurityAccessTest, WebSocketAuthTest)
└── frontend/ (Vite + React 18 + TypeScript + Tailwind CSS)
    ├── package.json
    ├── vite.config.ts
    ├── src/
    │   ├── api/ (client, auth, workspaces, projects, boards, tasks, docs)
    │   ├── components/ (KanbanBoard, TaskCard, TaskModal, MarkdownEditor, PresenceAvatars)
    │   ├── hooks/ (useWebSocket, useAuth, usePresence)
    │   └── App.tsx
```

---

## 🚀 STEP-BY-STEP IMPLEMENTATION ROADMAP

### Initial Setup (Before Phase 1)
- Create `PROGRESS.md` with checklist for all 9 phases.
- Initialize Gradle wrapper (`gradle wrapper`) or Maven wrapper (`mvn -N wrapper`).

---

### Phase 1: Project Scaffolding, Infrastructure & Schema Migrations
- **Tasks**:
  1. Generate `build.gradle.kts` (or `pom.xml`) with Java 21, Spring Boot 3.3+, Spring Data JPA, Spring Security, Spring WebSocket, Flyway, Testcontainers, PostgreSQL driver, Redis, AWS SDK / MinIO S3 client, `springdoc-openapi-starter-webmvc-ui`, MapStruct/Lombok, and `jjwt`.
  2. Create `docker-compose.yml` with:
     - PostgreSQL 16 on `5432`
     - Redis 7 on `6379`
     - MinIO on `9000` (API) & `9001` (Console)
     - Mailpit on `1025` (SMTP) & `8025` (Web UI)
  3. Create `src/main/resources/application.yml` and `application-test.yml`.
  4. Write `src/main/resources/db/migration/V1__initial_schema.sql` defining relational tables for workspaces, users, memberships, invitations, projects, boards, columns, tasks, comments, documents, attachments, outbox, activity, and notifications with foreign keys, checks, and unique constraints.
  5. Write `OrbitFlowApplication.java` entrypoint.
- **Verification Gate**:
  - Run `./gradlew testClasses` (or `./mvnw test-compile`).
  - Spin up test container or in-memory DB to verify Flyway executes `V1__initial_schema.sql` without syntax errors.

---

### Phase 2: Identity, Multi-Tenancy & Workspace Management
- **Tasks**:
  1. Configure Spring Security: JWT filter, password encoding (BCrypt), stateless session management, RFC-7807 `ProblemDetails` error handler.
  2. Implement `TenantContext` & `TenantFilter` ensuring every authenticated request stores the active `workspaceId`.
  3. Implement entities & repositories: `User`, `Workspace`, `WorkspaceMembership`, `Invitation`.
  4. Implement `AuthService` & `AuthController`: register, login, refresh token, get current user profile.
  5. Implement `WorkspaceService` & `WorkspaceController`:
     - Create workspace with unique slug generation and automatic `OWNER` membership.
     - Invite members with SHA-256 hashed single-use invitation tokens and email outbox event.
     - Accept invitation, verify expiration, seat policy, and role hierarchy.
     - Transfer workspace ownership and deactivate members safely.
  6. Enforce Centralized Authorization Policies (`WorkspaceSecurityPolicy`).
- **Verification Gate**:
  - Write & run `IdentityAndWorkspaceIntegrationTest`:
    - Test registration, login, and JWT generation.
    - Test workspace creation and slug uniqueness.
    - Test Tenant Isolation: assert that User B cannot read or alter User A's workspace (`403 Forbidden` or `404 Not Found`).
    - Test invitation lifecycle: create invite -> verify hash -> accept -> confirm active membership.

---

### Phase 3: Projects, Kanban Boards & LexoRank Task Engine
- **Tasks**:
  1. Implement Entities: `Project`, `ProjectMembership`, `Board`, `BoardColumn`, `Task`, `TaskAssignee`, `Label`, `TaskLabel`, `Checklist`, `ChecklistItem`, `TaskDependency`.
  2. Implement Optimistic Locking: Add `@Version Long version` on `Task` and `BoardColumn`.
  3. Implement Lexicographical Rank Utility (`LexoRank.java`):
     - Calculate midpoint ranks between neighboring cards for $O(1)$ reordering without card re-indexing.
     - Add auto-rebalance threshold detection.
  4. Implement Work-In-Progress (WIP) limit and transition policies in `BoardService`.
  5. Implement REST APIs:
     - `POST /api/v1/workspaces/{wId}/projects` (creates project, default board, columns from template).
     - `POST /api/v1/projects/{pId}/tasks` (allocates project-scoped task key `PH-xxx` safely).
     - `POST /api/v1/tasks/{tId}/move` (handles drag-and-drop column shift with optimistic lock check, neighbor IDs, and WIP validation).
     - Full CRUD for tasks, checklist items, labels, and dependencies.
- **Verification Gate**:
  - Write & run `TaskAndBoardIntegrationTest`:
    - Test task creation with sequential project key numbering.
    - Test LexoRank card reordering between arbitrary positions.
    - Test WIP limit constraint rejection when column is full.
    - Test Optimistic Concurrency Conflict: two concurrent updates with version 1 -> second request returns `409 Conflict` with latest state.

---

### Phase 4: Transactional Outbox Pattern & Activity Audit Timeline
- **Tasks**:
  1. Implement `OutboxEvent` entity (`id`, `aggregateType`, `aggregateId`, `eventType`, `payloadJson`, `createdAt`, `status: PENDING|PROCESSED|FAILED`).
  2. Implement `ActivityEvent` entity (`id`, `workspaceId`, `projectId`, `actorId`, `eventType`, `diffJson`, `createdAt`).
  3. Refactor services (Workspace, Project, Task) to write mutations and `OutboxEvent` within the exact same database `@Transactional` boundary.
  4. Implement `OutboxPoller` (`@Scheduled` with `SELECT ... FOR UPDATE SKIP LOCKED` or Spring ApplicationEvents bridge) to dispatch events asynchronously.
  5. Implement `ActivityService` and `ActivityController` to project and query audit trails for tasks and projects.
- **Verification Gate**:
  - Write & run `TransactionalOutboxIntegrationTest`:
    - Trigger a task move -> assert row written to `tasks`, row written to `outbox_events` atomically.
    - Run Outbox poller -> verify event transitions to `PROCESSED`.
    - Query `GET /api/v1/tasks/{id}/activity` and assert sanitized diff of changed fields is returned.

---

### Phase 5: Real-Time WebSockets (STOMP), Live Board Sync & Presence
- **Tasks**:
  1. Configure Spring WebSocket with STOMP message broker (`/ws`, `/topic`, `/user`, `/app`).
  2. Implement `WebSocketAuthChannelInterceptor`: validate JWT tokens and workspace/project authorization on `CONNECT` and `SUBSCRIBE`.
  3. Define destinations:
     - `/topic/workspaces/{workspaceId}`
     - `/topic/projects/{projectId}`
     - `/topic/boards/{boardId}`
     - `/user/queue/notifications`
  4. Implement Redis-backed `PresenceService`: track active users per board with heartbeat and expiration.
  5. Hook Outbox Event Dispatcher to publish real-time events (`TaskCreated`, `TaskMoved`, `PresenceChanged`) with monotonically increasing revision IDs.
- **Verification Gate**:
  - Write & run `WebSocketLiveSyncIntegrationTest`:
    - Connect test STOMP client using valid JWT.
    - Subscribe to `/topic/boards/{boardId}`.
    - Execute REST call moving a card -> verify STOMP subscriber receives live event with correct version and payload within 1 second.
    - Reject unauthorized subscriptions to foreign workspaces.

---

### Phase 6: Collaboration — Comments, Mentions, Watchers & Notifications
- **Tasks**:
  1. Implement `Comment` and `CommentRevision` entities with Markdown sanitization (strip malicious HTML/script tags).
  2. Implement Regex/Parser for `@username` mentions:
     - Verify mentioned user exists in workspace and has access to the task.
     - Deduplicate notifications so a user who is assignee + watcher + mentioned receives exactly one alert.
  3. Implement `Notification` and `NotificationPreference` entities.
  4. Implement Notification Delivery:
     - Real-time in-app delivery via `/user/queue/notifications`.
     - Email dispatch via JavaMailSender targeting Mailpit with exponential retry.
  5. Implement Scheduled Jobs:
     - Due date reminder job (scanning tasks approaching deadlines by user timezone).
     - Daily/weekly notification digest aggregator.
- **Verification Gate**:
  - Write & run `CollaborationAndNotificationIntegrationTest`:
    - Post a comment with `@john` -> assert `Comment` saved, exactly 1 notification created for John.
    - Verify self-mention does not trigger notification.
    - Verify email sent to Mailpit test server on port 1025.

---

### Phase 7: Hierarchical Documents, S3 Attachments & Search
- **Tasks**:
  1. Implement `Document` and `DocumentRevision` entities:
     - Project-scoped Markdown documents with tree hierarchy (`parentId`).
     - Version history snapshotting, autosave draft support, and restore capability.
  2. Implement Attachment Management (`Attachment` entity):
     - MinIO / AWS S3 client integration.
     - Upload reservation endpoint: returns short-lived pre-signed PUT URL.
     - Completion callback: verifies object presence, size, and triggers MIME validation / mock antivirus scan.
     - Authorized download endpoint: generates short-lived GET URL only for active files.
  3. Implement Full-Text Search Service:
     - PostgreSQL FTS or OpenSearch indexing over tasks, comments, and documents.
     - Filter by project, assignee, priority, status, and strictly enforce workspace boundaries.
- **Verification Gate**:
  - Write & run `DocumentsAttachmentsAndSearchIntegrationTest`:
    - Create a document revision, retrieve revision list, restore version 1.
    - Request pre-signed S3 upload URL -> verify payload -> confirm upload -> verify status changes to `ACTIVE`.
    - Execute search query `GET /api/v1/search?q=urgent` -> assert only accessible tasks are returned.

---

### Phase 8: Modern Single-Page Application (Frontend)
- **Tasks**:
  1. Initialize React 18 + TypeScript + Vite in `frontend/`.
  2. Configure Tailwind CSS with modern design tokens (slate/indigo palette, sleek dark/light mode).
  3. Implement Auth & Context: Login, Register, Workspace & Project Switcher, Protected Routes.
  4. Implement Kanban Board (`@hello-pangea/dnd` or `@dnd-kit`):
     - Columns (Backlog, In Progress, Review, Done) with WIP limit indicators.
     - Smooth card dragging updating LexoRank optimistically.
     - Conflict banner alerting user if another teammate moved/edited the task concurrently.
  5. Implement Real-Time STOMP Client (`@stomp/stompjs`):
     - Auto-reconnect with sync state badge.
     - Live presence avatars ("Alice and Bob are viewing this board").
  6. Implement Task Detail Modal:
     - Markdown description viewer/editor.
     - Checklists with progress bar.
     - Activity timeline stream.
     - Comments thread with `@mention` autocomplete.
     - Drag-and-drop file attachment uploader.
  7. Implement Document Wiki: hierarchical document tree with live Markdown preview.
  8. Implement In-App Notification Center with unread counter.
- **Verification Gate**:
  - Run `npm run build` inside `frontend/` to ensure zero TypeScript or bundle errors.
  - Run `npm test` for frontend unit/component tests.

---

### Phase 9: End-to-End Test Suite, Docker Compose & Final Documentation
- **Tasks**:
  1. Create End-to-End System Tests using Testcontainers (PostgreSQL + Redis):
     - Complete simulation: Register -> Create Workspace -> Invite Member -> Accept -> Create Task -> Drag Column -> Add Comment -> Search -> Complete Milestone.
  2. Configure Swagger / OpenAPI UI at `/swagger-ui.html`.
  3. Write comprehensive root `README.md`:
     - System architecture diagram (Mermaid).
     - One-command quickstart (`docker compose up` + `./gradlew bootRun`).
     - Complete API documentation summary.
     - Concurrency & LexoRank explanation.
- **Verification Gate**:
  - Run full test suite: `./gradlew test` (or `./mvnw test`) -> **100% tests pass**.
  - Verify frontend and backend build cleanly without warnings.
  - Verify `docker compose config` is valid.

---

## 🏁 FINAL COMPLETION CHECKLIST

Before concluding the loop, verify:
- [ ] `PROGRESS.md` shows all 9 phases marked `[COMPLETED]`.
- [ ] No compilation errors, lint errors, or failing tests exist in backend or frontend.
- [ ] Full test suite `./gradlew test` passes completely.
- [ ] Git log contains clear, progressive commit history for each phase.
- [ ] Print a final executive summary report with architecture details, test coverage numbers, and quickstart commands.

**COMMENCE PHASE 1 IMMEDIATELY.**
