# Sprint 1 Code Architecture

Last updated: 2026-09-22

This document explains how the EasyLang Sprint 1 implementation is organized, how login and role-based access work, and where new code should be added. The repository contains a React client and a Spring Boot API that run as separate applications and share PostgreSQL through HTTP APIs.

## 1. System overview

```text
Browser
  |
  | React routes, forms, fetch requests
  v
React + TypeScript frontend (:5173)
  |
  | /api/v1/* JSON, cookies, CSRF header
  v
Spring Boot backend (:8080)
  |
  | Controller -> Service -> Repository
  v
PostgreSQL (:55432 on the host, :5432 in Docker)
```

This is a **layered modular monolith**: one backend process contains clearly separated layers. It is intentionally not a microservice system. Sprint 1 has one small domain and one database, so splitting it into independently deployed services would add network, deployment, transaction, and monitoring complexity without solving a current problem. The layer boundaries keep the code understandable now and allow selected modules to be extracted later if real scaling requirements appear.

The frontend and backend are already separate deployable applications. The frontend never connects directly to PostgreSQL. The backend remains the authority for authentication, authorization, validation, and database access.

## 2. Repository structure

```text
soe_202_proj/
├── backend/                 Spring Boot API
│   └── src/
│       ├── main/java/com/easylang/activitymonitoring/
│       │   ├── common/      Shared API errors and exception handling
│       │   ├── config/      Spring Security and demo-data configuration
│       │   ├── controller/  HTTP endpoints only
│       │   ├── dto/         Request and response objects
│       │   ├── model/       JPA entities and domain enums
│       │   ├── repository/  Spring Data database access
│       │   ├── security/    Authenticated principal and JWT filter
│       │   └── service/     Application and authentication logic
│       └── main/resources/
│           └── db/migration/ Flyway database migrations
├── frontend/                React + TypeScript web application
│   └── src/
│       ├── auth/            Session state and protected routing
│       ├── components/      Reusable visual components
│       ├── lib/             API client and role helpers
│       ├── pages/           Login and role workspace screens
│       └── types/           Shared TypeScript data shapes
├── docs/                    Product, API, run, and architecture notes
└── compose.yaml             Local PostgreSQL service
```

### Backend package responsibilities

| Package | Responsibility | Important classes |
| --- | --- | --- |
| `controller` | Accept HTTP input and return HTTP output. Controllers do not query the database directly. | `AuthController`, `WorkspaceController` |
| `service` | Execute use cases and coordinate security or persistence dependencies. | `AuthService`, `DatabaseUserDetailsService`, `JwtService` |
| `repository` | Define database queries through Spring Data JPA. | `UserRepository` |
| `model` | Represent persisted domain data and fixed domain values. | `User`, `Role` |
| `dto` | Define API request/response records and internal operation results. | `AuthDtos`, `LoginResult` |
| `security` | Convert a database user into a Spring principal and authenticate JWT requests. | `AuthenticatedUser`, `JwtAuthenticationFilter` |
| `config` | Assemble Spring beans, access rules, CORS, CSRF, password hashing, and demo data. | `SecurityConfig`, `DemoDataConfig` |
| `common` | Keep error responses consistent across controllers and Spring Security. | `ApiExceptionHandler`, `ProblemResponse`, `SecurityErrorWriter` |

The dependency direction is kept simple:

```text
controller -> service -> repository -> model
     |            |
     v            v
    dto        security

config wires the layers together
common handles cross-cutting API errors
```

Controllers should stay thin. A future controller may validate a request and call a service, but business decisions belong in the service layer. Services may use repositories; controllers should not. Repositories work with model classes and should not return HTTP response objects.

## 3. Authentication and role flow

### Login

1. `LoginPage` asks `GET /api/v1/auth/csrf` for a CSRF token.
2. The frontend sends email and password to `POST /api/v1/auth/login` with the CSRF header.
3. `AuthController` validates the request DTO and delegates to `AuthService`.
4. Spring Security uses `DatabaseUserDetailsService` and `UserRepository` to load the user from `app_users`.
5. `BCryptPasswordEncoder` compares the submitted password with the stored hash.
6. `JwtService` creates a signed token containing the user id, email subject, role, issue time, and expiry.
7. The API sets the token in the `EASYLANG_ACCESS_TOKEN` cookie with `HttpOnly` and `SameSite=Lax`, then returns only safe profile fields.
8. React stores the user profile in memory and redirects to the route for the returned role.

The token is not stored in `localStorage` or `sessionStorage`, and the password hash is never returned to the browser.

### Authenticated request

```text
Request with access cookie
        |
        v
JwtAuthenticationFilter
  - reads and verifies JWT
  - reloads the active user
  - creates Spring Security authentication
        |
        v
SecurityConfig role rule
        |
        +--> correct role: controller executes
        |
        +--> wrong role: 403 JSON response
        |
        +--> no valid login: 401 JSON response
```

The frontend `ProtectedRoute` prevents confusing navigation, but it is not a security boundary. `SecurityConfig` independently protects each workspace endpoint:

| Endpoint | Required role |
| --- | --- |
| `GET /api/v1/workspaces/translator` | `TRANSLATOR` |
| `GET /api/v1/workspaces/chief-editor` | `CHIEF_EDITOR` |
| `GET /api/v1/workspaces/project-manager` | `PROJECT_MANAGER` |

### Logout and session restoration

`POST /api/v1/auth/logout` expires the access cookie. When the browser refreshes, `AuthContext` calls `GET /api/v1/auth/me`. A valid cookie restores the profile; a `401` returns the user to the login page.

## 4. Data and database evolution

Sprint 1 implements only the user slice required for US-01 and US-02. The `app_users` table contains identity, display data, a BCrypt password hash, role, active state, and creation time. A database constraint accepts only the three supported role values.

Flyway owns the schema. Hibernate uses `ddl-auto: validate`, so it verifies that Java entities match the schema but does not silently create or modify production tables. Every future schema change must be a new migration, for example `V2__create_projects.sql`; an applied migration must not be edited.

Local Docker PostgreSQL uses host port `55432`, which avoids conflicts with PostgreSQL on `5432` and the user's other project on `5433`. Inside the container PostgreSQL still listens on its standard port `5432`.

`DemoDataConfig` inserts the three alpha accounts only when they do not exist. It generates BCrypt hashes at startup and can be disabled with `SEED_DEMO_USERS=false`.

## 5. Frontend organization

`App.tsx` declares public and protected routes. `AuthContext` owns the current session state and exposes `login`, `logout`, and session reload operations. `lib/api.ts` is the single HTTP boundary: it includes cookies, attaches CSRF tokens to state-changing requests, converts error responses into `ApiError`, and maps each role to its workspace endpoint.

`LoginPage` owns login-form interaction and demo-account autofill. `WorkspacePage` is shared by all roles because Sprint 1 workspaces differ only in labels and allowed data. When later sprints introduce genuinely different role workflows, those screens should become separate page modules rather than one large conditional component.

## 6. API and configuration reference

Public authentication surface:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/auth/csrf` | Issue the CSRF token and cookie |
| `POST` | `/api/v1/auth/login` | Verify credentials and issue the access cookie |
| `GET` | `/api/v1/auth/me` | Return the current safe user profile |
| `POST` | `/api/v1/auth/logout` | Expire the access cookie |

Important environment variables are `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_TTL`, `SECURE_COOKIE`, `ALLOWED_ORIGINS`, and `SEED_DEMO_USERS`. Development defaults are described in the root README. Production must replace the JWT secret, enable secure cookies behind HTTPS, disable demo seeding, and narrow allowed origins.

## 7. How to add future functionality

For a normal backend use case, add code in this order:

1. Add a Flyway migration when the data model changes.
2. Add or extend a class in `model`.
3. Add database operations in `repository`.
4. Implement the use case in `service`.
5. Define external request/response shapes in `dto`.
6. Expose the use case from `controller`.
7. Add role rules in `SecurityConfig` and positive/negative integration tests.
8. Add the frontend API call, page state, and visible loading/error/empty/success states.

This structure is suitable while the application remains one team-owned system. Redis should be added only for a measured cache, rate-limit, or coordination need. Kafka should be added when durable asynchronous events have real consumers. Kubernetes becomes useful after backend/frontend containers and deployment requirements are defined. Those technologies should extend a proven boundary rather than compensate for unclear code organization.

## 8. Verification

Backend integration tests start the Spring application with an H2 database in PostgreSQL compatibility mode, run the real Flyway migration, seed the demo users, and verify login, invalid credentials, unauthenticated access, cross-role rejection, and CORS. Browser tests separately exercise the complete React-to-PostgreSQL flow against the real local services.

Use the commands in [run.md](run.md) to start the system. The endpoint payloads and error format are listed in [API.md](API.md), while product scope and deferred work are tracked in [SPRINT_1_STATUS.md](SPRINT_1_STATUS.md).
