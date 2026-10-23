# Architecture

Last updated: 2026-10-22

## Project Map

- `backend/` is a Spring Boot 3 API. Entry point: `ActivityMonitoringApplication`.
- `frontend/` is a React + TypeScript Vite app. Entry points: `frontend/src/main.tsx` and `frontend/src/App.tsx`.
- `compose.yaml` runs local PostgreSQL on host port `55432`.
- `backend/src/main/resources/db/migration/` contains Flyway migrations: V1 creates users, V2 adds projects/activities/assignments/work records, V3 renames the translated-volume column, and V4 adds weekly estimates, project editor assignments, reviews, and notifications. Applied migrations are immutable.
- `docs/` contains handoff, architecture, design, API, status, and run notes.

## Backend Structure

The backend is a layered modular monolith:

- `controller` exposes auth/workspace checks and translator activity/work-record endpoints.
- `service` handles auth and translator activity use cases, including assignment checks, record validation, progress responses, and the Sprint 3 weekly-estimate upsert.
- `repository` owns JPA access to users, projects, activities, assignments, and work records.
- `model` contains users/roles and the Sprint 2 activity domain.
- `dto` contains request/response records.
- `security` contains the authenticated principal and JWT filter.
- `config` wires Spring Security, CORS, password hashing, CSRF, JWT filter registration, and non-production demo seeding.
- `common` contains JSON error shapes and exception/security error writers.

Controllers call services; services use repositories; repositories work with models. Controllers should not query repositories directly.

## Frontend Structure

- `App.tsx` defines routes: `/`, `/login`, `/translator`, `/translator/activities/:activityId`, `/chief-editor`, `/project-manager`, and `*`.
- `auth/` owns in-memory session state and protected routing.
- `lib/api.ts` is the HTTP boundary; it sends cookies and CSRF headers.
- `lib/roles.ts` maps backend roles to labels and routes.
- `pages/` contains landing/login, the translator list and detail screens, starter editor/manager workspaces, and 404.
- `types/activity.ts` defines translator activity API shapes; `lib/activity-format.ts` and `lib/validation.ts` support display and form validation.
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

## Sprint 3 In Progress

V4 and the weekly-estimate slice are implemented. `TranslatorProgressService` locks the activity row, verifies the responsible assignment, and creates or updates the server-calculated current-week estimate. `TranslatorActivityService` includes estimate history and permissions in detail responses; record writes use the same row lock and reject states after `IN_PROGRESS`. `BusinessTimeConfig` supplies a configurable `Clock` defaulting to `Asia/Almaty`. The UI shows the estimate and collapsed history on the existing detail page. Review and notification tables are present, but finish, submission, reminder scheduling, and reminder APIs remain to be implemented. See `SPRINT_3_PLAN.md` for the remaining stages.
