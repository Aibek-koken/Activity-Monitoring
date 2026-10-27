# EasyLang Activity Monitoring

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black" alt="React"/>
  <img src="https://img.shields.io/badge/TypeScript-5.9-3178C6?logo=typescript&logoColor=white" alt="TypeScript"/>
  <img src="https://img.shields.io/badge/PostgreSQL-Flyway-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL"/>
</p>

**Full-stack activity tracking system with secure authentication, role-based workspaces, and translator progress records.**

**Project status:** Sprints 1–3 are implemented locally. Translators can record daily work, save weekly remaining-volume estimates, receive in-app reminders, finish at zero remaining, and submit finished activities for Chief Editor checking.

The repository contains an independent Spring Boot API and React/TypeScript frontend. The strongest part of the project is the security and access model: authentication is enforced on the server, protected again in the client, and tied to real database-backed roles.

## What stands out

- **Secure auth** — Spring Security + BCrypt + JWT stored in an `HttpOnly`, `SameSite=Lax` cookie.
- **CSRF protection** — the frontend requests and sends CSRF tokens for state-changing requests.
- **Double-layer RBAC** — Translator, Chief Editor, and Project Manager access is protected on both backend and frontend.
- **Activity workflow** — translators can view assigned activities, create/update work records and weekly estimates, finish eligible work, and submit it for checking.
- **Relevant reminders** — persisted daily and weekly prompts link to the missing work and disappear when it is supplied.
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
| Translator | Assigned activities, work records, weekly estimates, reminders, and submission |
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
```

Run Playwright against a disposable PostgreSQL database with `E2E_ISOLATED_DB=true`; see [run guide](docs/run.md). The Sprint 3 browser flow changes an activity to `UNDER_CHECKING`.

## Delivery roadmap

| Phase | Status | Main outcome |
| --- | --- | --- |
| Sprint 1 | Complete locally | Authentication, role-based access, starter workspaces |
| Sprint 2 | Complete locally | Translator assignments, activity details, daily work records, history and filters |
| Sprint 3 | Complete locally | Weekly estimates, daily/weekly reminders, Finish, and submission with Review cycle 1 |
| Sprint 4 | Planned | Chief Editor checking and corrections |
| Sprint 5 | Planned | Project Manager creation, assignment, workload and risk monitoring |
| Sprint 6 | Planned | Integrated alpha workflow and demonstration |

See [Sprint 2 status](docs/SPRINT_2_STATUS.md) for delivered behavior, the [Sprint 2 process report](docs/SPRINT_2_PROCESS_REPORT.md) for the full documentation handoff, and the [Sprint 3 status](docs/SPRINT_3_STATUS.md) for the completed flow and verification. The current system is described in [architecture](docs/ARCHITECTURE.md), the [API contract](docs/API.md), and [STATE.md](docs/STATE.md).
