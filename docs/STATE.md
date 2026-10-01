# State

Last updated: 2026-10-01

## Current Summary

Sprint 1 is complete locally: database-backed login, cookie JWT auth, CSRF, role-based protected workspaces, demo users, logout, and a public landing page. Sprint 2 Stages 1 and 2 are implemented: translators can load/search/open assigned activities, see cumulative translated-volume progress, and create or edit daily work records.

## In Progress

- Stage 2 is ready for review. Do not start Stage 3 until approved.

## Next Priorities

- In Stage 3, run the full test pass, validate Flyway migrations against real PostgreSQL, complete UI/accessibility polish, and run the Draft QA checklist.

## Known Bugs And Risks

- The ERD defines `TranslatedVolume` as a float but does not name a real-world unit such as pages, words, or characters; the UI labels it as translated volume units until the domain unit is clarified.
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
