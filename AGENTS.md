# AGENTS.md

## Project purpose

EasyLang Activity Monitoring helps a translation house identify activities that are likely to finish late. The long-term product supports Translators, Chief Editors, and Project Managers. Sprint 1 implements the shared authentication and authorization foundation only.

Read `docs/PROJECT_CONTEXT.md`, `docs/CODE_ARCHITECTURE.md`, `docs/ARCHITECTURE.md`, and `docs/SPRINT_1_STATUS.md` before changing behavior or scope.

## Repository boundaries

- Keep backend code under `backend/` and frontend code under `frontend/`.
- Keep the backend layer boundaries explicit: HTTP handling in `controller`, use cases in `service`, persistence in `repository`, entities/enums in `model`, API shapes in `dto`, and authentication mechanics in `security`.
- Controllers must not query repositories directly; route business operations through a service.
- Do not place business logic in React components. The backend remains the authority for authentication, authorization, and domain rules.
- Keep modules inside the Spring Boot application until measured scaling or deployment requirements justify extraction. Kafka, Redis, and Kubernetes are future infrastructure options, not current dependencies.
- Add database changes through new Flyway migrations. Never edit an applied migration after it has been shared.
- Use semantic CSS variables from `frontend/src/styles.css`; follow `brand.md` and do not copy the old presentation prototype literally.

## Security rules

- Never store JWTs in localStorage or sessionStorage. Authentication uses an `HttpOnly` cookie.
- Keep CSRF protection enabled for state-changing requests.
- Enforce roles on the backend even when a frontend route guard exists.
- Return generic login failures so the API does not disclose whether an email exists.
- Never commit real secrets or production credentials.

## Commands

Backend:

```bash
cd backend
mvn test
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm test
npm run lint
npm run build
npm run dev
```

End-to-end, after the database and both applications are running:

```bash
cd frontend
npm run test:e2e
```

## Definition of done for future work

- Requirements and role boundaries are traceable to a user story.
- Backend endpoints have positive and negative authorization tests.
- Frontend has loading, error, empty, and success states where applicable.
- Keyboard focus, labels, target sizes, responsive behavior, and reduced-motion preferences are checked.
- `mvn test`, `npm test`, `npm run lint`, `npm run build`, and relevant browser tests pass.
- Update the appropriate file in `docs/` with decisions, delivered scope, and known follow-up work.
