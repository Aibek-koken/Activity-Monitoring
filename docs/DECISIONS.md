# Decisions

Last updated: 2026-10-22

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-09-22 | Use a layered modular monolith for the backend. | Sprint 1 has one small domain and one database; separate services would add deployment and consistency cost too early. |
| 2026-09-22 | Store JWTs in `HttpOnly`, `SameSite=Lax` cookies and keep CSRF protection enabled. | Reduces JavaScript token exposure while protecting state-changing cookie requests. |
| 2026-09-22 | Let Flyway own schema evolution and keep Hibernate on validation. | Avoids silent schema drift and keeps database changes reviewable. |
| 2026-09-22 | Seed demo users in application code. | BCrypt hashes are generated securely and seeding remains idempotent for local/demo environments. |
| 2026-09-24 | Add a public landing page without changing backend auth behavior. | Presents the product direction while keeping Sprint 1 scope limited to auth/workspace access. |
| 2026-10-01 | Remove public self-registration from the current product flow. | Staff accounts should be provisioned outside the public UI; login errors remain generic for security. |
| 2026-10-01 | Keep the UI style calm, light, and operational. | The product is a work tool for translators, editors, and project managers, not a marketing-heavy interface. |
| 2026-10-01 | Scope Sprint 2 to translator assigned activities, details, search, and daily work records; exclude weekly estimates and corrections. | The approved Sprint 2 stories are US-03 through US-07; later translator flows start in Sprint 3+. |
| 2026-10-01 | Use demo-seeded projects, activities, assignments, and work records until project-manager activity creation is built. | Sprint 2 depends on assigned activities, while PM assignment stories are later in the roadmap. |
| 2026-10-01 | Treat current progress as cumulative translated volume for now. | Full progress and estimated total work require weekly remaining-text estimates from later stories. |
| 2026-10-01 | Store daily translated volume as decimal volume units, keep work hours optional, and allow one record per translator/activity/date. | The ERD models `TranslatedVolume` as a float but does not define pages/words/characters; a unique daily record prevents accidental duplicates while preserving editability. |
| 2026-10-01 | Default the daily record form to the browser's local date, but reject only dates later than UTC+14 on the backend. | Translators get the expected local default while the API accepts "today" across all global time zones and still blocks true future entries. |
| 2026-10-01 | Search translator activities server-side by case-insensitive partial activity number or name. | Search results must respect server-side translator ownership, not only client-side filtering. |
| 2026-10-01 | Treat applied Flyway migrations as immutable; keep restored V2 content and move the translated-volume column rename into V3. | V2 may already exist in local or teammate databases, so changing its checksum would make upgrades fragile. |
| 2026-10-22 | Limit a translator to 5 active responsible assignments and 10 active assisting assignments, counted separately; only `COMPLETED` frees a place. Enforce this when Project Manager assignment is built. | The current Sprint 2 data is demo-seeded and has no assignment API or manager UI. The future service must check the relevant quota atomically with assignment creation and return a clear conflict; the manager UI should show each quota and explain when it is full. Chief Editors do not assign translators. |
| 2026-10-22 | Close Sprint 2 locally and scope Sprint 3 to US-08–US-12: daily/weekly reminders, weekly estimates, zero-remaining `FINISHED`, and submission for checking. | The sprint distribution explicitly includes “submission”; the database design maps US-12 to a Review cycle. Chief Editor checking/results remain Sprint 4, while reminder and estimate policies stay visible for confirmation in `SPRINT_3_PLAN.md`. |
| 2026-10-22 | Adopt the Sprint 3 plan's working defaults: Monday–Sunday weeks in `Asia/Almaty`; responsible translator owns one current-week estimate per activity; zero remaining requires a separate Finish action; daily and estimate writes lock after `FINISHED`; weekday 18:00 daily and Friday 16:00 weekly in-app reminders. | Starting implementation establishes a consistent server-owned contract. Reminder scheduling and finish/submission behavior still need implementation and tests. |
| 2026-10-22 | Use a nullable unique responsible-project key for Chief Editor assignment and a unique notification dedupe key in V4. | Both database constraints work in PostgreSQL and the existing H2 test profile while enforcing one responsible editor per project and preventing repeated reminders. |
