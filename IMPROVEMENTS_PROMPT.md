# Master Directive: OrbitFlow 10/10 Polish & Performance Upgrade

You are an autonomous Principal Software Architect and Senior Full-Stack Engineer. Your mission is to execute a surgical, production-grade upgrade on **OrbitFlow** to eliminate performance bottlenecks, fix memory leaks, refactor anti-patterns, and complete the frontend feature surface, elevating the project from **8.5/10 to a flawless 10/10**.

---

## ⚡ CORE OPERATING PRINCIPLES

1. **ZERO HUMAN INTERVENTION**: Do NOT stop or ask for user input. Execute the improvements phase by phase, run the verification gates, and proceed automatically.
2. **REGRESSION-FREE**: Every existing test (40 backend tests, all frontend tests) MUST continue to pass. All new functionality must have dedicated unit and integration tests.
3. **PRODUCTION PERFORMANCE**: Replace all in-memory table scans with indexed, database-level SQL/JPQL queries.
4. **PROGRESS TRACKING**: Maintain `IMPROVEMENTS_PROGRESS.md` to track each improvement gate.

---

## 🔄 EXECUTION LOOP ALGORITHM

For each improvement phase:
```
┌────────────────────────────────────────────────────────────────────────┐
│ 1. Read IMPROVEMENTS_PROGRESS.md -> Identify the current uncompleted task│
│ 2. Implement the refactor / feature with production-grade code         │
│ 3. Run Verification Gate:                                              │
│    - Backend: ./gradlew test                                           │
│    - Frontend: cd frontend && npm test -- --run && npm run build       │
│ 4. If failure: inspect logs -> fix -> re-verify until 100% green       │
│ 5. Mark task [COMPLETED] in IMPROVEMENTS_PROGRESS.md                   │
│ 6. Git commit: "refactor: complete improvement phase X - <title>"      │
│ 7. Automatically transition to the next phase                          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ THE 6 IMPROVEMENT PHASES

### Phase 1: Eliminate In-Memory Table Scans & Implement Indexed Queries
**Target Files:**
- `src/main/java/com/orbitflow/task/TaskRepository.java`
- `src/main/java/com/orbitflow/document/DocumentRepository.java`
- `src/main/java/com/orbitflow/search/SearchService.java`
- `src/main/java/com/orbitflow/notification/NotificationWorker.java`
- `src/test/java/com/orbitflow/integration/DocumentsAttachmentsAndSearchIntegrationTest.java`

**Requirements:**
1. In `TaskRepository`:
   - Add `@Query` for bounded search:
     ```java
     @Query("SELECT t FROM Task t WHERE t.workspace.id = :wsId AND t.archived = false AND " +
            "(:projectId IS NULL OR t.project.id = :projectId) AND " +
            "(LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            " LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
     List<Task> searchTasks(@Param("wsId") UUID wsId, @Param("projectId") UUID projectId, @Param("q") String q, org.springframework.data.domain.Pageable pageable);
     ```
   - Add query for upcoming due dates:
     ```java
     @Query("SELECT t FROM Task t WHERE t.archived = false AND t.dueDate IS NOT NULL AND " +
            "t.dueDate >= :start AND t.dueDate <= :end")
     List<Task> findUpcomingDueTasks(@Param("start") Instant start, @Param("end") Instant end, org.springframework.data.domain.Pageable pageable);
     ```
2. In `DocumentRepository`:
   - Add `@Query` for bounded search:
     ```java
     @Query("SELECT d FROM Document d WHERE d.workspace.id = :wsId AND " +
            "(:projectId IS NULL OR d.project.id = :projectId) AND " +
            "(LOWER(d.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            " LOWER(COALESCE(d.currentBodyMarkdown, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
     List<Document> searchDocuments(@Param("wsId") UUID wsId, @Param("projectId") UUID projectId, @Param("q") String q, org.springframework.data.domain.Pageable pageable);
     ```
3. In `NotificationWorker`:
   - Replace `tasks.findAll()` with `tasks.findUpcomingDueTasks(now, soon, PageRequest.of(0, 200))`.
4. In `SearchService`:
   - Obtain user accessible workspaces from `requesterId`.
   - Execute database queries through `tasks.searchTasks(...)` and `documents.searchDocuments(...)` with max 50 results. Eliminate all `tasks.findAll()` and `documents.findAll()` calls.
- **Verification Gate**:
  - Run `./gradlew test` -> All 40+ tests pass.

---

### Phase 2: Fix Frontend Interval Memory Leak & Precision LexoRank Drag-and-Drop
**Target Files:**
- `frontend/src/hooks/useWebSocket.ts`
- `frontend/src/components/KanbanBoard.tsx`
- `frontend/src/api/client.ts`
- `frontend/src/api/models.ts`

**Requirements:**
1. In `frontend/src/hooks/useWebSocket.ts`:
   - Store the heartbeat `setInterval` handle in a ref: `const beatRef = useRef<NodeJS.Timeout | null>(null)`.
   - In the `useEffect` cleanup return:
     ```ts
     return () => {
       if (beatRef.current) {
         clearInterval(beatRef.current);
         beatRef.current = null;
       }
       void client.deactivate();
       setStatus('disconnected');
     };
     ```
2. In `frontend/src/api/models.ts` & `frontend/src/api/client.ts`:
   - Update `tasksApi.move` signature:
     ```ts
     move: (taskId: string, destinationColumnId: string, expectedVersion: number, prevTaskId?: string | null, nextTaskId?: string | null) => ...
     ```
3. In `frontend/src/components/KanbanBoard.tsx`:
   - During `onDragEnd`:
     Calculate `prevTaskId` and `nextTaskId` from the destination column's sorted cards array around the drop position.
     Pass them to `tasksApi.move(taskId, destColumnId, task.version, prevTaskId, nextTaskId)`.
- **Verification Gate**:
  - `cd frontend && npm test -- --run && npm run build` -> Clean build, 0 errors.

---

### Phase 3: Eliminate Static Bean Anti-Pattern in WebSocket Security Interceptor
**Target Files:**
- `src/main/java/com/orbitflow/realtime/WebSocketAuthChannelInterceptor.java`
- `src/test/java/com/orbitflow/integration/WebSocketLiveSyncIntegrationTest.java`

**Requirements:**
1. Remove `static ProjectAccessChecker` and all static bean references (`staticService`, `staticRepo`).
2. Use `@org.springframework.context.annotation.Lazy` injection to safely break the circular bean reference:
   ```java
   @Component
   public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
       private final JwtProvider jwt;
       private final WorkspaceMembershipRepository workspaceMembers;
       private final BoardRepository boards;
       private final ProjectRepository projects;
       private final ProjectService projectService;

       public WebSocketAuthChannelInterceptor(
               JwtProvider jwt,
               WorkspaceMembershipRepository workspaceMembers,
               BoardRepository boards,
               ProjectRepository projects,
               @org.springframework.context.annotation.Lazy ProjectService projectService) {
           this.jwt = jwt;
           this.workspaceMembers = workspaceMembers;
           this.boards = boards;
           this.projects = projects;
           this.projectService = projectService;
       }
       ...
   ```
3. Replace `ProjectAccessChecker.check(userId, projectId)` with a direct, instance-based method:
   ```java
   private void checkProjectAccess(UUID userId, UUID projectId) {
       Project p = projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found"));
       projectService.requireProjectAccess(userId, p);
   }
   ```
- **Verification Gate**:
  - Run `./gradlew test --tests WebSocketLiveSyncIntegrationTest` and `./gradlew test` -> 100% green.

---

### Phase 4: Cache UserPrincipal to Optimize Stateless JWT Filter
**Target Files:**
- `src/main/java/com/orbitflow/identity/AuthService.java`
- `src/main/java/com/orbitflow/common/security/JwtAuthFilter.java`
- `src/main/java/com/orbitflow/common/config/RedisConfig.java` (or CacheConfig)

**Requirements:**
1. Configure Spring Cache with Caffeine / ConcurrentMap for `user_principals` with a 5-minute TTL.
2. Annotate `loadUserByUsername(String userId)` in `AuthService` with `@org.springframework.cache.annotation.Cacheable(value = "user_principals", key = "#userId")`.
3. Add `@org.springframework.cache.annotation.CacheEvict(value = "user_principals", key = "#userId")` to `updateProfile`, `logout`, and user deactivation methods.
- **Verification Gate**:
  - Run `./gradlew test` -> All tests pass, caching behaves transparently.

---

### Phase 5: Expand Frontend UI Surface (Documents Wiki, Search, Workspace Members)
**Target Files:**
- `frontend/src/components/DocumentWiki.tsx` (New component)
- `frontend/src/components/GlobalSearch.tsx` (New component)
- `frontend/src/components/WorkspaceMembersModal.tsx` (New component)
- `frontend/src/components/ReportsView.tsx` (New component)
- `frontend/src/App.tsx`
- `frontend/src/api/models.ts`

**Requirements:**
1. **Global Search in Top Bar**:
   - Search input in header; debounced typing queries `GET /api/v1/search?q=...`.
   - Displays dropdown with matching Tasks (badge + title + snippet) and Documents. Clicking navigates to task or document.
2. **Project Documents Wiki Tab (`/projects/:projectId/docs`)**:
   - Sidebar with hierarchical document tree.
   - Main pane: Markdown viewer/editor with live preview, "New Document" button, and version history restore button.
3. **Project Reports Tab (`/projects/:projectId/reports`)**:
   - Displays total tasks, completed, overdue, status breakdown, and average cycle time hours.
   - Milestone list with "Complete Milestone" action.
4. **Workspace Members & Invite Modal**:
   - Accessible via workspace switcher or settings button.
   - Lists active members with roles (`OWNER`, `ADMIN`, `MEMBER`, `GUEST`).
   - Form to invite members by email with role selection.
- **Verification Gate**:
  - `cd frontend && npm test -- --run && npm run build` -> Clean build, 0 errors.

---

### Phase 6: Docker Compose Healthcheck & Full End-to-End Verification
**Target Files:**
- `docker-compose.yml`
- `README.md`
- `IMPROVEMENTS_PROGRESS.md`

**Requirements:**
1. In `docker-compose.yml`:
   - Add a container healthcheck for `app`:
     ```yaml
     healthcheck:
       test: ["CMD-SHELL", "wget -q -O - http://localhost:8080/actuator/health | grep UP || exit 1"]
       interval: 10s
       timeout: 5s
       retries: 5
       start_period: 30s
     ```
2. Run full backend suite: `./gradlew test --rerun-tasks`.
3. Run full frontend suite: `cd frontend && npm test -- --run && npm run build`.
4. Update `README.md` highlighting the upgraded query performance, caching, and new UI views.
5. Mark all phases `[COMPLETED]` in `IMPROVEMENTS_PROGRESS.md`.
6. Git commit: `feat: complete OrbitFlow 10/10 polish and performance upgrade`.

---

## 🏁 EXIT CRITERIA
The agent terminates ONLY when:
- All 6 phases are marked `[COMPLETED]`.
- `./gradlew test` passes 100% with zero failures.
- `frontend/` builds with zero TypeScript errors.
- No `tasks.findAll()` or `documents.findAll()` remain in `SearchService` or `NotificationWorker`.
- The frontend includes Search, Documents Wiki, Reports, and Member Management.

**COMMENCE PHASE 1 IMMEDIATELY.**
