# Architecture Decisions

Last updated: 2026-09-22

## System shape

The project follows the three-tier architecture in the source documents:

```text
React web client
       |
       | HTTPS / JSON
       v
Spring Boot application
       |
       | JPA + Flyway
       v
PostgreSQL
```

The code is a layered modular monolith. Backend packages separate controllers, services, repositories, models, DTOs, and security mechanics without adding deployment and consistency costs before the alpha needs them. See [CODE_ARCHITECTURE.md](CODE_ARCHITECTURE.md) for the package map and request flows.

## Authentication and authorization

1. The browser requests `GET /api/v1/auth/csrf`.
2. The browser submits credentials to `POST /api/v1/auth/login` with the CSRF header.
3. Spring Security authenticates the email/password against PostgreSQL using BCrypt.
4. The API returns safe user data and sets a signed JWT in an `HttpOnly`, `SameSite=Lax` cookie.
5. The frontend redirects using the returned database role.
6. Every protected API request is authenticated again by the JWT filter and authorized by the backend route rules. The filter is registered only inside the Spring Security chain; its automatic servlet registration is disabled to prevent duplicate execution and cross-request security-context issues.

The frontend route guard improves user experience but is never the security boundary.

## Why cookie JWT + CSRF

The access token is not exposed to JavaScript or browser storage, reducing the impact of token theft through XSS. Because the browser sends cookies automatically, state-changing requests require a separate CSRF token. The API remains stateless, leaving a clean path to horizontal scaling.

## Data decisions

- PostgreSQL is the production database.
- Flyway owns schema evolution; Hibernate validates rather than creates tables.
- `app_users` avoids the reserved SQL word `user` while representing the master ERD's User entity.
- Role is stored as a constrained string matching the Java enum.
- Demo accounts are seeded in application code so their BCrypt hashes are generated securely and the operation remains idempotent.
- H2 in PostgreSQL compatibility mode is used only for fast automated integration tests. Browser verification also runs against real PostgreSQL.

## Frontend decisions

- React and TypeScript run as a separately deployable Vite application.
- Auth state is held in memory and restored from `/auth/me`; no access token is stored in JavaScript.
- Semantic CSS tokens implement a light slate palette with teal used only as a restrained brand accent.
- Role-specific dashboard configuration shares one component while preserving separate protected routes.
- The product UI shows only Sprint 1 behavior. Roadmap and test-status copy stays in documentation rather than appearing as product functionality.

## Growth path

- Add Sprint 2-5 entities and use cases as feature packages within the monolith.
- Add Redis only for a demonstrated cache, rate-limit, or distributed-session need.
- Add Kafka only when durable asynchronous domain events have real consumers.
- Containerize backend and frontend, then add Kubernetes manifests after deployment topology is known.
- Add CI gates for backend tests, frontend tests/lint/build, database migration verification, and browser smoke tests.
