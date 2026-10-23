# State

Last updated: 2026-10-22

## Current Summary

Sprint 1 is complete locally: database-backed login, cookie JWT auth, CSRF, role-based protected workspaces, demo users, logout, and a public landing page. Sprint 2 is complete locally: translators can load/search/open assigned activities, see cumulative translated-volume progress, and create or edit daily work records. The translator UI has breadcrumbs, assignment-role and optional status/assigned-date filters, and a collapsible daily history with an inclusive date-range filter. Sprint 3 implementation has begun: the V4 schema and current-week remaining-volume estimate flow are present.

## In Progress

- Sprint 3 Stage 0/1 and the first US-09 vertical slice are implemented. The responsible translator can save/update one current-week estimate per activity; assigned assistants can read it; the detail page shows current value and collapsed history. The plan's product defaults are recorded in `DECISIONS.md`.

## Next Priorities

- Complete Sprint 3 with zero-remaining `FINISHED` and submission for checking (US-11/US-12), then daily/weekly reminders (US-08/US-10). Add fixed-clock/concurrency and E2E coverage for the remaining flows, then close Sprint 3 only when Definition of Done passes.
- Leave editor checking/corrections for Sprint 4 and manager activity creation/assignment for Sprint 5. When manager assignment is built, enforce separate quotas of 5 active responsible and 10 active assisting assignments on the backend.

## Known Bugs And Risks

- Open question: the ERD defines `TranslatedVolume` as a float but does not name a real-world unit such as pages, words, or characters; the UI labels it as translated volume units until the domain unit is clarified.
- Production settings still need a real JWT secret, secure cookies over HTTPS, narrowed CORS origins, and demo seeding disabled.
- The separate 5 responsible + 10 assisting active-assignment limits are documented future requirements, not enforced by the current demo seeding or an assignment API.
- The original six-sprint overview and master user-story file referenced in `PROJECT_CONTEXT.md` are not present in this workspace. Sprint 3's working scope is cross-checked against available sources; the working reminder schedule and estimate ownership are now recorded project decisions.

## Decisions Made

- Keep the backend as a layered modular monolith.
- Use JWT in an `HttpOnly` cookie plus CSRF protection.
- Do not expose a public self-registration flow; users are expected to be provisioned outside the public UI.
- Keep the UI light, restrained, and operational.

## Last Session

- 2026-10-22: Started Sprint 3. Added V4 tables for weekly estimates, project Chief Editor assignments, reviews, and notifications; matching models/repositories; demo editor assignment; and a configurable `Asia/Almaty` business clock. Implemented current-week estimate save/update, server permissions/history, responsible-only authorization, post-finish record-write guard, and the weekly panel in the translator detail UI. Verified backend tests, 41 frontend tests, lint/build, clean PostgreSQL V1→V4, and local V3→V4 upgrade/startup. Finish/submission and reminders are not yet implemented.
- 2026-10-22: Expanded `SPRINT_3_PLAN.md` into an implementation-ready plan for US-08–US-12. It now includes the previously missing submission story, product rules, V4 schema, API contracts, lifecycle/authorization rules, backend/UI architecture, two-week schedule, test matrix, failure modes, work split, Definition of Done, and defense flow.
- 2026-10-22: Prepared `SPRINT_2_PROCESS_REPORT.md` as a complete handoff for formal course documentation. It records Sprint 2 scope, implementation stages, API/schema/business rules, UI/UX refinements, problems and fixes, verification evidence, demonstration flow, deferred work, key files, and the Git timeline.
- 2026-10-22: Verified the handoff baseline: `mvn test` passed (27 tests, 2 PostgreSQL-specific tests skipped without their database profile); `npm test` passed (39 tests), `npm run lint` passed, and `npm run build` passed. End-to-end tests against running services were not rerun in this documentation session.
- 2026-10-22: Closed the Sprint 2 documentation handoff, refreshed the architecture and API descriptions to match the implemented translator flow, and prepared `SPRINT_3_PLAN.md`. Sprint 3 is planned but not coded.
- 2026-10-22: Revised the future translator assignment limits to 5 active responsible and 10 active assisting activities, counted separately. There is still no assignment endpoint or quota enforcement in Sprint 2.
- 2026-10-22: Aligned the optional-hours field with the other daily-record inputs, made the assigned-activities title compact, removed the undocumented top metric cards, and added an icon-triggered status/assigned-date filter panel. Filter choices persist in the list URL and activity breadcrumb; frontend tests, lint, and build pass.
- 2026-10-22: Improved translator Sprint 2 navigation and record-history UX. Search and assignment filters persist in list URLs and activity breadcrumbs; daily history starts collapsed and supports inclusive From/To date filtering without changing activity progress or the record form. Documented the future 5 responsible + 10 assisting active-assignment caps for Project Manager staffing.
- 2026-10-22: Reviewed the documentation learning path. `CODE_ARCHITECTURE.md` explains the Sprint 1 code in detail; Sprint 2 should be studied from `TranslatorActivityController`, `TranslatorActivityService`, related repositories/models/DTOs, and the V2/V3 migrations because the architecture docs have not yet been updated for Sprint 2.
- 2026-10-15: Reviewed the project documentation, Sprint 1 context, Sprint 2 scope, implementation structure, API surface, database migrations, and Git history. Sprint 2 implementation is present on `main` and the worktree is clean.
- Sprint 2 Stage 1 added Flyway-managed project/activity/assignment/work-record schema, idempotent non-prod demo data, translator read APIs, translator activity list/detail pages, search, progress summaries, and server-side translator ownership checks.
- Cleanup before Stage 2 confirmed docs already use `mvn`, added a second demo translator assignment for ownership checks, and added read-page frontend tests.
- Sprint 2 Stage 2 added create/edit daily work record APIs, backend validation for decimal volume/work hours and future dates, duplicate-date protection, progress recalculation from cumulative translated volume, and a translator detail-page record form with loading, validation, error, and success states.
- Sprint 2 Stage 3 restored the edited V2 migration and moved the rename into V3, validated the full migration chain on PostgreSQL 17 via Docker, added ownership/CSRF/date tests, polished accessibility semantics, and completed the US-03 through US-07 QA pass.
- Fixed the blank translator detail page: the API omits null fields, so the page crashed on `workHours.toFixed`; nullable fields are now optional in the types and formatted defensively, with an en-US locale, a route error boundary, a cleaned-up summary row, and regression tests.
