# Sprint 1 Status

Last updated: 2026-09-22

Status: complete locally

## Acceptance criteria

| Requirement | Status | Evidence |
| --- | --- | --- |
| Enter login credentials | Complete | Accessible email/password form with validation and password visibility control |
| Verify credentials against database | Complete | Spring Security uses `app_users` through JPA and BCrypt |
| Correct credentials grant access | Complete | Browser tests cover all three seeded roles |
| Incorrect credentials show explicit error | Complete | Generic inline error preserves form values |
| Unauthenticated users cannot access protected areas | Complete | Backend integration test and frontend protected routes |
| Role stored in database | Complete | constrained `role` column and Java enum |
| Automatic role detection | Complete | login response is built from the authenticated database user |
| Role-specific redirection | Complete | Translator, Chief Editor, and Project Manager routes tested |
| Cross-role access is rejected | Complete | server returns 403; client redirects away from another role route |
| Preconfigured alpha accounts | Complete | three idempotently seeded users, controlled by environment setting |
| Logout | Complete | cookie is cleared; browser test confirms the former workspace becomes protected |

## Delivered files and capabilities

- Independent Spring Boot backend and React/TypeScript frontend.
- Clear backend layers for controllers, services, repositories, models, DTOs, and security code.
- PostgreSQL Compose service and Flyway `app_users` migration.
- JWT authentication in `HttpOnly` cookie with CSRF protection.
- Consistent JSON 400/401/403 responses.
- Three protected starter workspaces.
- Responsive, accessible login and dashboard layouts with a compact light visual system.
- Backend integration tests, frontend unit test, lint/build gates, and Playwright end-to-end tests.
- Living project context, architecture notes, brand direction, API contract, and agent instructions.

## Verification record

- `mvn test`: 5 tests passed.
- `npm test`: 1 test passed.
- `npm run lint`: passed with no warnings.
- `npm run build`: production build passed.
- `npm run test:e2e`: 21 scenarios passed against real PostgreSQL and the running API/web applications at 1280, 768, and 375 pixel widths.

## Intentional non-goals

Sprint 1 does not implement projects, activities, work records, reviews, reminders, or monitoring calculations. The interface therefore shows only the authenticated user's role and access scope; future product features remain documented outside the product UI.

## Operational follow-up

- The provided `compose.yaml` requires Docker Desktop to be running.
- The Compose PostgreSQL service uses the dedicated host port `55432` to avoid collisions with Postgres.app on `5432` and other development projects on `5433`.
- Production configuration must replace the development JWT key, use HTTPS cookies, disable demo seeding, and narrow allowed origins.
- A remote GitHub repository was visible in the supplied screenshot, but this implementation has not been pushed because remote writes were not requested.
