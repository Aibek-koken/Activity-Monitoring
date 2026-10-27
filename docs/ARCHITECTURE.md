# Architecture

Last updated: 2026-10-25

## Project Map

- `backend/` is a Spring Boot 3 API. Entry point: `ActivityMonitoringApplication`.
- `frontend/` is a React + TypeScript Vite app. Entry points: `frontend/src/main.tsx` and `frontend/src/App.tsx`.
- `compose.yaml` runs local PostgreSQL on host port `55432`.
- `backend/src/main/resources/db/migration/` contains Flyway migrations: V1 creates users, V2 adds projects/activities/assignments/work records, V3 renames the translated-volume column, and V4 adds weekly estimates, project editor assignments, reviews, and notifications. Applied migrations are immutable.
- `docs/` contains handoff, architecture, design, API, status, and run notes.

## Backend Structure

The backend is a layered modular monolith:

- `controller` exposes auth/workspace checks, translator activity/record/estimate/lifecycle endpoints, and reminder endpoints.
- `service` handles auth, translator activity use cases, weekly estimates, finish/submission, and reminder evaluation.
- `repository` owns JPA access to users, projects, activities, assignments, work records, estimates, reviews, and notifications.
- `model` contains users/roles, activities, assignments, records, estimates, reviews, and notifications.
- `dto` contains request/response records.
- `security` contains the authenticated principal and JWT filter.
- `config` wires Spring Security, CORS, password hashing, CSRF, JWT filter registration, business time, reminder scheduling, and non-production demo seeding.
- `common` contains JSON error shapes and exception/security error writers.

Controllers call services; services use repositories; repositories work with models. Controllers should not query repositories directly.

## Frontend Structure

- `App.tsx` defines routes: `/`, `/login`, `/translator`, `/translator/activities/:activityId`, `/chief-editor`, `/project-manager`, and `*`.
- `auth/` owns in-memory session state and protected routing.
- `lib/api.ts` is the HTTP boundary; it sends cookies and CSRF headers.
- `lib/roles.ts` maps backend roles to labels and routes.
- `pages/` contains landing/login, the translator list and detail screens, starter editor/manager workspaces, and 404.
- `types/activity.ts` and `types/reminder.ts` define translator API shapes; `lib/activity-format.ts` and `lib/validation.ts` support display and form validation.
- `styles.css` holds shared product styling; `landing.css` holds landing-page styling.

## Data And Auth Flow

1. Browser requests `GET /api/v1/auth/csrf`.
2. Login posts email/password to `POST /api/v1/auth/login` with the CSRF header.
3. Spring Security authenticates against `app_users` using BCrypt.
4. Backend returns safe user fields and sets a JWT in an `HttpOnly`, `SameSite=Lax` cookie.
5. React stores only the safe user profile in memory and redirects by role.
6. `JwtAuthenticationFilter` authenticates later requests from the cookie.
7. `SecurityConfig` enforces role access to each workspace endpoint.

There is no self-registration flow in the current application.

## Sprint 2 Activity Flow

1. `GET /api/v1/translator/activities` scopes results to assignments for the authenticated translator. Optional `q` searches the activity number/name on the server. Assignment role, status, and assigned-date controls currently filter that authorized result in the client and persist in the URL.
2. `GET /api/v1/translator/activities/{id}` checks the same assignment and returns detail, the translator's own work records, and cumulative translated volume.
3. `POST`/`PUT` work-record endpoints validate the signed-in translator's assignment and data. The database enforces one record per translator/activity/date; creating the first record moves `ASSIGNED` to `IN_PROGRESS`.
4. The history date range only changes which records the page displays. It does not change the server's cumulative total or the record form.

## Sprint 3 Progress, Submission, And Reminders

`TranslatorProgressService` locks the activity row for current-week estimate upserts, Finish, and Submit. It verifies the responsible assignment and active state. Finish requires a saved zero estimate for the current Monday–Sunday week; Submit requires `FINISHED` and an active responsible Chief Editor for the project. Review cycle 1 and the `UNDER_CHECKING` transition commit in one transaction. The row lock and database uniqueness protect against simultaneous writes and duplicate reviews.

`TranslatorActivityService` includes estimate history and server-computed action permissions in detail responses. It uses the same activity lock for record writes and rejects writes after `IN_PROGRESS`. Assistants can view estimates but cannot change the authoritative value or transition the activity.

`BusinessTimeConfig` supplies an injectable `Clock` defaulting to `Asia/Almaty`. `ReminderScheduler` evaluates due work hourly and on startup. `ReminderService` queries due assignments in batches, checks daily records and current-week estimates, and inserts persisted notifications through `NotificationWriter` with a separate transaction. The notification dedupe key handles concurrent scheduler runs. The reminder API returns only unread reminders whose day/week, assignment, and missing work are still relevant. It marks only the owning user's reminder read.

The React activity list summarizes reminders in a disclosure, with direct links and read actions when expanded. The detail page prioritizes the daily record form, gives the responsible translator a next-step explanation, and keeps the weekly estimate and its history collapsed by default. Zero remaining exposes an explicit Finish confirmation; Submit appears only in `FINISHED`. After submission, the work forms are replaced by the `UNDER_CHECKING` state. The backend remains authoritative for permissions and transitions.
