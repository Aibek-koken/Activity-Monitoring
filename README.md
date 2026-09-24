# EasyLang Activity Monitoring

Sprint 1 foundation for a role-based translation activity monitoring system. The repository contains two independent applications:

- `backend` — Spring Boot 3, Spring Security, JPA, Flyway, PostgreSQL
- `frontend` — React, TypeScript, Vite, React Router

The implemented flow covers login, logout, database-backed users, automatic role detection, three protected workspaces, and both server-side and client-side RBAC.

## Requirements

- Java 17+
- Maven 3.9+
- Node.js 20+
- Docker Desktop with Docker Compose, or a local PostgreSQL 16+

## Run locally

Start PostgreSQL from the repository root:

```bash
docker compose up -d --wait
```

Start the API:

```bash
cd backend
mvn spring-boot:run
```

Start the web app in a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

## Alpha accounts

All demo accounts use the password `Demo123!`.

| Role | Email |
| --- | --- |
| Translator | `translator@easylang.local` |
| Chief Editor | `editor@easylang.local` |
| Project Manager | `manager@easylang.local` |

Demo seeding is idempotent and controlled by `SEED_DEMO_USERS`. Set it to `false` outside local/demo environments.

## Test and verify

```bash
cd backend && mvn test
cd frontend && npm test
cd frontend && npm run lint
cd frontend && npm run build
```

With PostgreSQL, backend, and frontend running:

```bash
cd frontend
npm run test:e2e
```

The end-to-end suite covers all three roles, invalid credentials, cross-role route protection, logout, and responsive viewports at 375, 768, and 1280 pixels.

## Configuration

Copy `.env.example` to `.env` for Docker Compose overrides. Spring accepts these environment variables:

| Variable | Purpose | Development default |
| --- | --- | --- |
| `DB_URL` | JDBC connection URL | `jdbc:postgresql://localhost:55432/easylang` |
| `DB_USERNAME` | PostgreSQL user | `easylang` |
| `DB_PASSWORD` | PostgreSQL password | `easylang_dev_password` |
| `JWT_SECRET` | Base64 HMAC key | development-only value |
| `JWT_TTL` | Access-token lifetime | `PT8H` |
| `SECURE_COOKIE` | Require HTTPS for auth cookie | `false` |
| `ALLOWED_ORIGINS` | Comma-separated frontend origins | local Vite origins |
| `SEED_DEMO_USERS` | Create alpha accounts | `true` |

Docker publishes the project database on the dedicated host port `55432` by default to avoid
conflicts with Postgres.app on `5432` and existing development projects on `5433`.
Set `POSTGRES_PORT` and the matching `DB_URL` if a different port is required.

Production must provide a new `JWT_SECRET`, set `SECURE_COOKIE=true`, disable demo users, and use HTTPS.

## Documentation

- [Project context](docs/PROJECT_CONTEXT.md)
- [Sprint 1 code architecture](docs/CODE_ARCHITECTURE.md)
- [Sprint 1 technical presentation](docs/EasyLang_Sprint1_Technical_Overview.pptx)
- [Architecture decisions](docs/ARCHITECTURE.md)
- [Sprint 1 status](docs/SPRINT_1_STATUS.md)
- [API contract](docs/API.md)
- [Local run commands](docs/run.md)
- [Brand direction](brand.md)
