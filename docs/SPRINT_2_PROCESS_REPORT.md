# EasyLang Activity Monitoring — Sprint 2 Process Report

Last updated: 2026-10-22

Status: completed locally

Purpose: factual handoff for the person preparing the formal Sprint 2 course documentation. This report describes what was planned, implemented, changed, tested, and deliberately left for later sprints.

## 1. Sprint overview

Sprint 2 covered the Translator activity workflow from the master backlog, US-03 through US-07. It extended the Sprint 1 authentication foundation with the first working business domain: projects, translation activities, translator assignments, daily work records, and translator-facing activity pages.

The implemented end-to-end flow is:

`Login as Translator → Open assigned activities → Search/filter the list → Open one activity → View progress and daily history → Create or edit a daily record → See recalculated progress`

Sprint 2 is complete in the local repository. This status does not mean that the application was deployed to production.

## 2. Original Sprint 2 scope

The Sprint 1 submission document assigns Weeks 5–6 / Sprint 2 to US-03–US-07 and calls the main focus “Translator activity workflow.” The repository decisions refined this into the following deliverables:

- show only activities assigned to the authenticated translator;
- search assigned activities by activity number or name;
- open an activity and view its project, status, assignment role, dates, and progress;
- view the translator's daily work-record history;
- create a daily record with translated volume and optional work time;
- edit an existing daily record;
- keep all authorization and validation rules on the backend;
- provide loading, error, empty, validation, and success states on the frontend.

Weekly remaining-text estimates, reminders, finishing/submission, Chief Editor checking, corrections, and Project Manager staffing/monitoring were outside Sprint 2.

## 3. Work completed during the sprint

### Stage 1 — Activity foundation and read flow

The first implementation stage introduced the complete data and read-only path required for a translator to see assigned work.

Backend work:

- Added the `Project`, `Activity`, `ActivityTranslator`, and `WorkRecord` domain models.
- Added the full activity lifecycle enum: `ASSIGNED`, `IN_PROGRESS`, `FINISHED`, `UNDER_CHECKING`, `CORRECTION_REQUIRED`, `CORRECTED`, and `COMPLETED`.
- Added repositories for projects, activities, assignments, and work records.
- Added `TranslatorActivityController`, `TranslatorActivityService`, and dedicated translator DTOs.
- Added server-side ownership checks. A translator can load only an activity assigned to their account; unassigned activities return 404 instead of leaking their existence or data.
- Added case-insensitive server-side search by partial activity number or activity name.
- Added progress aggregation based on the authenticated translator's work records: cumulative translated volume, number of records, and latest record date.
- Protected `/api/v1/translator/**` with the `TRANSLATOR` role in Spring Security.

Database work:

- Added Flyway migration V2 with the `projects`, `activities`, `activity_translators`, and `work_records` tables.
- Added primary keys, foreign keys, uniqueness rules, check constraints, and indexes.
- Used `activity_translators.is_responsible` to distinguish the responsible translator from assistants.
- Enforced one work record per translator, activity, and calendar date.
- Added idempotent non-production demo data: two projects, assigned activities for two translators, responsible/assisting examples, and sample work records.

Frontend work:

- Added the `/translator` assigned-activities page.
- Added the `/translator/activities/:activityId` details page.
- Added typed API models and translator API methods.
- Added a shared authenticated workspace layout and translator navigation.
- Displayed activity number, name, project, status, assignment role, assigned date, cumulative volume, record count, and last record date.
- Added loading skeletons, API error states with retry, empty states, and search no-result states.

### Stage 2 — Daily record creation and editing

The second stage changed the detail page from a read-only view into a working data-entry workflow.

Backend work:

- Added `POST /api/v1/translator/activities/{activityId}/work-records`.
- Added `PUT /api/v1/translator/activities/{activityId}/work-records/{recordId}`.
- Required a record date and translated volume when creating a record.
- Allowed work time to remain empty.
- Validated translated volume as greater than 0 and no more than 1000, with up to two decimal places.
- Validated work time from 0 through 24 hours, with up to two decimal places in the API representation.
- Rejected future record dates.
- Returned 409 when a record already existed for the same translator/activity/date.
- Prevented a translator from editing another translator's record or a record from another activity.
- Changed an activity from `ASSIGNED` to `IN_PROGRESS` after work was recorded, while leaving later lifecycle states unchanged.
- Returned refreshed activity detail after create/update so the UI could immediately update totals and history.
- Extended the common API exception handler so validation and business errors use the existing JSON problem shape.

Frontend work:

- Added a form for record date, translated volume, hours, and minutes.
- Defaulted a new record to the browser's local calendar date.
- Disabled future dates in the date input and repeated the validation in the client for immediate feedback.
- Converted hours and minutes to the decimal-hour value expected by the backend.
- Added field-level errors, a general form error, save progress, and success feedback.
- Added edit mode for an existing history row. The record date stays locked while volume and time can be changed.
- If the chosen date already has a record, the form switches to editing that record instead of encouraging a duplicate.
- On a server-side 409 race/conflict, the page reloads the activity and guides the translator to edit the existing record.

### Stage 3 — Data, migration, and reliability fixes

After the main feature worked, several correctness issues were found and fixed.

- The future-date rule was made safe for users in every time zone by allowing dates through the current date in UTC+14. This prevents the server from rejecting a legitimate local “today.”
- An already-created Flyway V2 migration was restored instead of being rewritten. The translated-volume column rename was moved into a new V3 migration to avoid checksum failures in existing databases.
- PostgreSQL-specific integration tests were added for search and query behavior, including escaped wildcard characters.
- Search escapes `%`, `_`, and backslashes so user input is treated as text instead of unintended SQL `LIKE` patterns.
- Nullable API fields were reflected correctly in TypeScript. Missing `workHours` and `lastRecordDate` no longer crash rendering.
- A route error boundary was added so an unexpected page exception displays a recoverable error screen instead of a blank page.
- Number and date formatting were centralized and made independent of the browser locale where consistency was needed.
- Work time was changed into separate hour/minute inputs for clearer data entry, with checks for whole hours, minutes from 0–59, and a maximum total of 24:00.
- Large mock data was added for realistic local UI checking.
- Request examples for health, authentication, activity reading, record creation, and record editing were added to `backend/src/main/resources/api.http`.

### Stage 4 — UI/UX refinement and navigation

The last stage reduced visual noise and made the translator workflow easier to navigate.

Assigned activities page:

- Reduced the size and visual weight of the page title.
- Removed the undocumented “Shown / In progress / Translated” metric cards because they were not required and distracted from the task list.
- Added `All / Responsible / Assisting` assignment-role filtering.
- Added an icon-triggered advanced-filter panel for activity status and inclusive assigned-date range.
- Combined search, role, status, and date filters.
- Stored filter state in the URL so refresh, direct links, and browser navigation preserve the view.
- Made displayed results and empty messages reflect the active filters.

Activity detail page:

- Added a breadcrumb: `Activities / activity number`.
- Preserved the list search and filters when opening an activity and returning through the breadcrumb.
- Kept daily history collapsed by default to reduce the number of visible controls.
- Added inclusive `From date` and `To date` filtering inside expanded history.
- Added clear-range behavior, invalid-range feedback, and a dedicated “No records in this period” state.
- Kept lifetime progress and the create/edit form independent from the history filter.
- Kept old records editable after filtering.
- Fixed the optional-hours field alignment so it remains level with the other controls even when its label wraps.
- Improved narrow-screen layouts: filters and forms reflow, and history rows become readable stacked records without horizontal page scrolling.
- Preserved visible keyboard focus and semantic labels, alerts, tables, buttons, and disclosure state.

## 4. Implemented API contract

Base path: `/api/v1`

| Method | Endpoint | Result |
| --- | --- | --- |
| `GET` | `/translator/activities` | Lists only the current translator's assigned activities |
| `GET` | `/translator/activities?q=...` | Searches the authorized list by number or name |
| `GET` | `/translator/activities/{activityId}` | Returns assigned activity details, progress, and the translator's records |
| `POST` | `/translator/activities/{activityId}/work-records` | Creates a daily record and returns refreshed detail |
| `PUT` | `/translator/activities/{activityId}/work-records/{recordId}` | Updates the translator's existing record and returns refreshed detail |

All endpoints require the `TRANSLATOR` role. POST and PUT require a valid CSRF token because authentication uses an `HttpOnly` cookie. Authorization is enforced by the backend even though the frontend also protects the routes.

The list's role/status/date filters and the detail history date range are UI filters over data already authorized by the server. The search query is handled on the server.

## 5. Data model introduced in Sprint 2

| Entity | Purpose | Important rules |
| --- | --- | --- |
| `Project` | Parent container for activities | Belongs to a Project Manager |
| `Activity` | A translation activity | Unique number and lifecycle status |
| `ActivityTranslator` | Assignment between an activity and translator | Unique activity/translator pair; responsible flag; assigned date |
| `WorkRecord` | One translator's daily work entry | Unique activity/translator/date; positive volume; optional 0–24 hours |

The schema is created by Flyway V2 and corrected through V3. Shared/applied migrations were not rewritten after the migration-integrity issue was identified.

## 6. Main business rules delivered

- A translator sees only their own assignments.
- Search never expands the translator's authorized data scope.
- A translator can read and edit only their own work records.
- Only one daily record is allowed for the same translator, activity, and date.
- The record date cannot be in the future.
- Translated volume must be `0.01–1000.00` with no more than two decimal places.
- Work time is optional and cannot exceed 24 hours.
- Creating or editing a record recalculates the returned cumulative progress.
- Recording work moves only `ASSIGNED` activities to `IN_PROGRESS`.
- Current progress means the signed-in translator's cumulative recorded volume. It is not yet full activity progress, an estimated total, or a delivery-risk calculation.

## 7. Testing and verification

Backend coverage includes:

- authentication and translator-role access;
- list/detail ownership isolation;
- search behavior and wildcard escaping;
- record creation and editing;
- record ownership isolation;
- CSRF rejection for unsafe requests without a token;
- duplicate-date conflict;
- future-date, volume, precision, and work-hour validation;
- activity status transition;
- PostgreSQL-specific query verification through a separate profile.

Frontend coverage includes:

- loading, error, retry, empty, and populated states;
- search and combined filters;
- filter persistence in URLs and breadcrumbs;
- record form validation and save states;
- create and edit flows;
- nullable values and safe formatting;
- collapsed and filtered history;
- invalid/empty date ranges;
- route-level crash recovery.

Latest handoff baseline on 2026-10-22:

- `mvn test`: 27 tests executed, 0 failures; 2 PostgreSQL-profile tests skipped in the default run.
- `npm test`: 39 tests passed across 6 test files.
- `npm run lint`: passed.
- `npm run build`: passed.
- `git diff --check`: passed for the documentation handoff.
- PostgreSQL 17 migration chain was validated during Sprint 2 work.
- Full Playwright E2E against running database/API/web services was not rerun in the final documentation-only session and should be repeated before a release or defense if required.

## 8. Problems encountered and how they were resolved

| Problem | Resolution |
| --- | --- |
| A modified V2 migration could break Flyway checksums | Restored V2 and introduced V3 for the rename |
| “Today” could be rejected because server and browser used different dates | Browser uses local date; backend permits the current date through UTC+14 |
| Nullable numeric fields caused a blank detail page | Updated TypeScript types, defensive formatters, regression tests, and route error boundary |
| Search input could behave as SQL wildcards | Escaped `%`, `_`, and backslashes; added PostgreSQL tests |
| Decimal work hours were inconvenient to enter | Split the UI into hours and minutes; kept decimal conversion at the API boundary |
| Duplicate date could occur through stale UI or concurrent interaction | Database unique constraint plus API 409 and UI recovery guidance |
| The activity page contained too many controls and metrics | Removed undocumented metric cards, collapsed history, and moved secondary filters behind one filter button |
| Users could lose list context after opening a detail page | Persisted filters in the URL and carried them through the breadcrumb |
| Optional-hours label pushed its input out of alignment | Adjusted the responsive form grid and label layout |

## 9. Definition of Done for Sprint 2

Sprint 2 is considered complete locally because the following flow works and is covered by tests:

`Translator login → Assigned activity list → Search/filter → Activity detail → Create daily record → Updated progress/history → Edit existing record`

The completed result also satisfies these technical conditions:

- server-side role and assignment authorization;
- persistent PostgreSQL data managed through Flyway;
- backend validation plus database constraints;
- CSRF protection on writes;
- responsive translator UI with accessible states;
- automated backend/frontend tests, lint, and production build;
- documented API, architecture, decisions, run instructions, and sprint status.

## 10. Demonstration flow

For a Sprint 2 demonstration:

1. Start PostgreSQL, backend, and frontend using `docs/run.md`.
2. Log in as `translator@easylang.local` with the demo password configured for local data.
3. Show that only this translator's assignments appear.
4. Search by part of an activity number or name.
5. Switch between Responsible and Assisting; open advanced filters and filter by status/date.
6. Open an activity and use the breadcrumb to demonstrate preserved list context.
7. Create a record for today or a past date with volume and optional hours/minutes.
8. Show the updated cumulative volume, record count, and last-record date.
9. Expand history, filter it by an inclusive date range, and edit an old record.
10. Demonstrate validation with a future date, invalid volume, minutes above 59, or a duplicate date.

## 11. Deliberately deferred work

The following items must not be described as delivered in Sprint 2:

- weekly remaining-text estimates;
- daily and weekly reminders;
- the responsible translator's zero-remaining `FINISHED` action;
- submission to a Chief Editor;
- review, mistakes, correction, resubmission, and approval;
- Project Manager project/activity creation and assignment UI;
- enforcement of the future assignment quotas: 5 active Responsible and 10 active Assisting per translator;
- estimated-total trends, stalled-progress detection, risk flags, and manager monitoring;
- production deployment and production security configuration.

Weekly estimates, reminders, the `FINISHED` rule, and submission for checking form Sprint 3. Chief Editor checking/corrections belong to Sprint 4. Project Manager creation, staffing quotas, and monitoring belong to Sprint 5.

## 12. Key implementation files

Backend:

- `backend/src/main/java/com/easylang/activitymonitoring/controller/TranslatorActivityController.java`
- `backend/src/main/java/com/easylang/activitymonitoring/service/TranslatorActivityService.java`
- `backend/src/main/java/com/easylang/activitymonitoring/dto/TranslatorActivityDtos.java`
- `backend/src/main/java/com/easylang/activitymonitoring/model/`
- `backend/src/main/java/com/easylang/activitymonitoring/repository/`
- `backend/src/main/resources/db/migration/V2__create_activity_tracking.sql`
- `backend/src/main/resources/db/migration/V3__rename_translated_volume_column.sql`
- `backend/src/test/java/com/easylang/activitymonitoring/controller/TranslatorActivityControllerIntegrationTest.java`
- `backend/src/test/java/com/easylang/activitymonitoring/controller/TranslatorActivityPostgresIntegrationTest.java`

Frontend:

- `frontend/src/pages/TranslatorActivitiesPage.tsx`
- `frontend/src/pages/TranslatorActivityDetailPage.tsx`
- `frontend/src/components/Breadcrumbs.tsx`
- `frontend/src/lib/api.ts`
- `frontend/src/lib/validation.ts`
- `frontend/src/lib/activity-format.ts`
- `frontend/src/types/activity.ts`
- corresponding `*.test.ts` and `*.test.tsx` files.

Project documents:

- `docs/SPRINT_2_STATUS.md`
- `docs/API.md`
- `docs/ARCHITECTURE.md`
- `docs/DESIGN.md`
- `docs/DECISIONS.md`
- `docs/STATE.md`
- `docs/run.md`

## 13. Git implementation timeline

The main Sprint 2 work is visible in the following repository history:

| Date | Commit | Main result |
| --- | --- | --- |
| 2026-10-01 | `b652ab1` | Activity schema, assignments, demo data, read APIs, list/detail pages |
| 2026-10-01 | `110b90d` | Create/edit daily records, validation, UI forms, initial tests |
| 2026-10-01 | `f5baa09` | Future-date correction and immutable Flyway V3 migration |
| 2026-10-01 | `3af29bc` | PostgreSQL query tests, null safety, formatting, route error boundary |
| 2026-10-02 | `c9aac88` | Work-time UX/validation, number formatting, realistic mock data |
| 2026-10-09 | `f5558f0` | Removed redundant form hints and reduced UI noise |
| 2026-10-09 | `d483095`, `e696e1d` | Expanded executable API request examples |
| 2026-10-22 | `80000db` | Breadcrumbs, URL-preserved filters, collapsed/date-filtered history, responsive UI polish, final handoff docs |

Across the Sprint 2 implementation range, the repository changed approximately 53 files with about 5,045 inserted and 217 removed lines. These numbers include code, tests, documentation, API examples, demo/mock data, and the final UI refinement commit; they are context, not a productivity metric.

## 14. Short summary for the formal document

Sprint 2 implemented the first complete EasyLang business workflow for the Translator role. The team introduced the project/activity/assignment/work-record database model, translator-scoped REST endpoints, server-side authorization, search, daily record creation/editing, progress aggregation, and responsive React pages for assigned activities and activity details. The implementation was hardened with validation, database constraints, immutable Flyway migrations, PostgreSQL-specific query tests, null-safe formatting, a route error boundary, accessible loading/error/empty states, and combined URL-persisted filters. Final UI work added breadcrumbs, responsible/assisting and advanced filters, a compact page hierarchy, and a collapsed daily history with inclusive date filtering. Automated backend and frontend tests, lint, and production build pass. Weekly estimates, reminders, finishing/submission, editor review/corrections, and Project Manager monitoring remain assigned to later sprints.
