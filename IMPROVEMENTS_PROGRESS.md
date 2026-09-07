# OrbitFlow 10/10 Upgrade Progress

| Phase | Description | Status | Verification Gate |
|---|---|---|---|
| **Phase 1** | Eliminate In-Memory Table Scans & Add Indexed JPQL Queries | `[COMPLETED]` | `./gradlew test` passes with zero `findAll()` in search/reminders |
| **Phase 2** | Fix WebSocket Interval Leak & Precision Neighbor Drag-and-Drop | `[COMPLETED]` | Clean frontend build, no heartbeat leaks |
| **Phase 3** | Eliminate Static Bean Anti-Pattern in WebSocket Interceptor | `[NOT STARTED]` | Clean `@Lazy` injection, all WebSocket tests pass |
| **Phase 4** | Cache UserPrincipal for Stateless JWT Authentication | `[NOT STARTED]` | Spring Cache active, zero redundant DB calls on auth |
| **Phase 5** | Expand Frontend UI Surface (Wiki, Search, Reports, Members) | `[NOT STARTED]` | React SPA builds cleanly with new views |
| **Phase 6** | Docker Healthcheck & Full End-to-End Verification | `[NOT STARTED]` | 100% tests green, Docker valid, README updated |

---
**Current Phase**: Phase 3
**Last Updated**: Phase 2 completed — beatRef heartbeat cleanup, nullable neighbor move signature, rank-sorted precision DnD; `npm test` 2/2 + `npm run build` clean
