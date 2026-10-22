# EasyLang Activity Monitoring

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black" alt="React"/>
  <img src="https://img.shields.io/badge/TypeScript-5.9-3178C6?logo=typescript&logoColor=white" alt="TypeScript"/>
  <img src="https://img.shields.io/badge/PostgreSQL-Flyway-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL"/>
</p>

**Full-stack activity tracking system with secure authentication, role-based workspaces, and translator progress records.**

**Project status:** Sprint 1 and Sprint 2 are complete locally. Sprint 3 is next and has not been implemented. Its scope is weekly remaining-text estimates, a `FINISHED` transition when the responsible translator reports zero remaining, and daily/weekly reminders.

The repository contains an independent Spring Boot API and React/TypeScript frontend. The strongest part of the project is the security and access model: authentication is enforced on the server, protected again in the client, and tied to real database-backed roles.

## What stands out

- **Secure auth** — Spring Security + BCrypt + JWT stored in an `HttpOnly`, `SameSite=Lax` cookie.
- **CSRF protection** — the frontend requests and sends CSRF tokens for state-changing requests.
- **Double-layer RBAC** — Translator, Chief Editor, and Project Manager access is protected on both backend and frontend.
- **Activity workflow** — translators can view assigned activities, create/update work records, and track progress.
- **Database discipline** — PostgreSQL + Flyway migrations, constraints, uniqueness rules, and indexes.
- **Verification** — backend integration tests, frontend unit tests, lint/build gates, and Playwright end-to-end coverage.

## Request flow

```mermaid
flowchart LR
    A[Browser] --> B[React + TypeScript]
    B --> C[Spring Security]
    C --> D[JWT Authentication Filter]
    D --> E[Controllers]
    E --> F[Services]
    F --> G[JPA Repositories]
    G --> H[(PostgreSQL)]
```

## Roles

| Role | Workspace |
| --- | --- |
| Translator | Assigned activities and work records |
| Chief Editor | Protected editor workspace |
| Project Manager | Protected manager workspace |

## Run locally

```bash
docker compose up -d --wait

cd backend
mvn spring-boot:run
```

In another terminal:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

## Verify

```bash
cd backend && mvn test
cd frontend && npm test
cd frontend && npm run lint
cd frontend && npm run build
cd frontend && npm run test:e2e
```

## Delivery roadmap

| Phase | Status | Main outcome |
| --- | --- | --- |
| Sprint 1 | Complete locally | Authentication, role-based access, starter workspaces |
| Sprint 2 | Complete locally | Translator assignments, activity details, daily work records, history and filters |
| Sprint 3 | Next | Weekly remaining estimates, finish rule, daily/weekly reminders |
| Sprint 4 | Planned | Chief Editor checking and corrections |
| Sprint 5 | Planned | Project Manager creation, assignment, workload and risk monitoring |
| Sprint 6 | Planned | Integrated alpha workflow and demonstration |

See [Sprint 2 status](docs/SPRINT_2_STATUS.md) for delivered behavior and verification, [Sprint 3 plan](docs/SPRINT_3_PLAN.md) for the next scope and open policy choices, [architecture](docs/ARCHITECTURE.md) for the current structure, and [API contract](docs/API.md) for implemented endpoints. The current status and latest test results are in [STATE.md](docs/STATE.md).
