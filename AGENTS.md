# AGENTS.md

EasyLang Activity Monitoring is a Spring Boot + React/Vite monorepo for a translation-house activity monitoring system. Sprint 1 implements authentication, role-based access, protected starter workspaces, and a public landing page.

## Read First

1. `docs/STATE.md`
2. `docs/ARCHITECTURE.md`
3. For UI/UX work, also read `docs/DESIGN.md` and use `$frontend-design`.

Open other files only when the task needs them. Do not read the whole repo by default.

## Commands

- Backend: `cd backend && mvn test`; `cd backend && mvn spring-boot:run`.
- Frontend: `cd frontend && npm install`; `npm test`; `npm run lint`; `npm run build`; `npm run dev`.
- DB: `docker compose up -d --wait`.
- E2E after DB/API/web are running: `cd frontend && npm run test:e2e`.

## Coding Rules

- Keep backend under `backend/` and frontend under `frontend/`.
- Keep backend layers explicit: HTTP in controllers, use cases in services, persistence in repositories, entities/enums in models, API shapes in DTOs, auth mechanics in security/config.
- Controllers must not query repositories directly.
- Do not put business rules in React components; backend remains authority for auth, authorization, validation, and domain rules.
- Add schema changes with new Flyway migrations; never edit a shared/applied migration.
- Never store JWTs in `localStorage` or `sessionStorage`; auth uses an `HttpOnly` cookie.
- Keep CSRF enabled for state-changing requests.
- Enforce roles on the backend even when frontend route guards exist.
- Return generic login failures; do not reveal whether an email exists.
- Never commit real secrets or production credentials.
- Use semantic CSS variables from `frontend/src/styles.css`; do not invent a new visual system.

## Token-Saving Rules

- Use `rg`/search to find the files needed for the task.
- Do not re-read large files without a reason.
- One session handles one task; update `docs/STATE.md` at the end of every session.

## Where To Find Deeper Docs

- ERD: `../3.3_Database_Design.md` — open only when the task needs it.
- User stories: `../Project 3_Practice CSS 342_01_p User_Stories.xlsx` — open only when the task needs it.
- Sprint docs: `../EasyLang - Technical Specification & Sprint 1 Plan.pdf`, `../Project 3_Practice CSS 342_01_p Spints.pdf` — open only when the task needs it.
- `docs/API.md` — open only when the task needs it.
- `docs/run.md` — open only when the task needs it.
- `docs/PROJECT_CONTEXT.md` — open only when the task needs it.
- `docs/CODE_ARCHITECTURE.md` — open only when the task needs it.
- `docs/SPRINT_1_STATUS.md` — open only when the task needs it.
