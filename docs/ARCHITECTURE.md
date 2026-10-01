# Architecture

Last updated: 2026-10-01

## Project Map

- `backend/` is a Spring Boot 3 API. Entry point: `ActivityMonitoringApplication`.
- `frontend/` is a React + TypeScript Vite app. Entry points: `frontend/src/main.tsx` and `frontend/src/App.tsx`.
- `compose.yaml` runs local PostgreSQL on host port `55432`.
- `backend/src/main/resources/db/migration/` contains Flyway migrations; current Sprint 1 schema is `app_users`.
- `docs/` contains handoff, architecture, design, API, status, and run notes.

## Backend Structure

The backend is a layered modular monolith:

- `controller` exposes HTTP endpoints for auth and workspace checks.
- `service` handles login, JWT creation, and database-backed user loading.
- `repository` owns JPA access to `app_users`.
- `model` contains `User` and `Role`.
- `dto` contains request/response records.
- `security` contains the authenticated principal and JWT filter.
- `config` wires Spring Security, CORS, password hashing, CSRF, JWT filter registration, and demo seeding.
- `common` contains JSON error shapes and exception/security error writers.

Controllers call services; services use repositories; repositories work with models. Controllers should not query repositories directly.

## Frontend Structure

- `App.tsx` defines routes: `/`, `/login`, `/translator`, `/chief-editor`, `/project-manager`, and `*`.
- `auth/` owns in-memory session state and protected routing.
- `lib/api.ts` is the HTTP boundary; it sends cookies and CSRF headers.
- `lib/roles.ts` maps backend roles to labels and routes.
- `pages/` contains the landing page, login page, starter workspace, and 404 page.
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
