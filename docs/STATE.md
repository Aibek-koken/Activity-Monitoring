# State

Last updated: 2026-10-01

## Current Summary

Sprint 1 is complete locally: database-backed login, cookie JWT auth, CSRF, role-based protected workspaces, demo users, logout, and a public landing page. Sprint 2 is complete locally: translators can load/search/open assigned activities, see cumulative translated-volume progress, and create or edit daily work records.

## In Progress

- Nothing active. Sprint 2 is ready for commit/review.

## Next Priorities

- Review/commit Sprint 2, then start later-sprint project-manager activity creation, weekly remaining-volume estimates, reminders, and correction workflows.

## Known Bugs And Risks

- Open question: the ERD defines `TranslatedVolume` as a float but does not name a real-world unit such as pages, words, or characters; the UI labels it as translated volume units until the domain unit is clarified.
- Production settings still need a real JWT secret, secure cookies over HTTPS, narrowed CORS origins, and demo seeding disabled.

## Decisions Made

- Keep the backend as a layered modular monolith.
- Use JWT in an `HttpOnly` cookie plus CSRF protection.
- Do not expose a public self-registration flow; users are expected to be provisioned outside the public UI.
- Keep the UI light, restrained, and operational.

## Last Session

- Sprint 2 Stage 1 added Flyway-managed project/activity/assignment/work-record schema, idempotent non-prod demo data, translator read APIs, translator activity list/detail pages, search, progress summaries, and server-side translator ownership checks.
- Cleanup before Stage 2 confirmed docs already use `mvn`, added a second demo translator assignment for ownership checks, and added read-page frontend tests.
- Sprint 2 Stage 2 added create/edit daily work record APIs, backend validation for decimal volume/work hours and future dates, duplicate-date protection, progress recalculation from cumulative translated volume, and a translator detail-page record form with loading, validation, error, and success states.
- Sprint 2 Stage 3 restored the edited V2 migration and moved the rename into V3, validated the full migration chain on PostgreSQL 17 via Docker, added ownership/CSRF/date tests, polished accessibility semantics, and completed the US-03 through US-07 QA pass.
- Fixed the blank translator detail page: the API omits null fields, so the page crashed on `workHours.toFixed`; nullable fields are now optional in the types and formatted defensively, with an en-US locale, a route error boundary, a cleaned-up summary row, and regression tests.
