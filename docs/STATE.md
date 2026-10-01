# State

Last updated: 2026-10-01

## Current Summary

Sprint 1 is complete locally: database-backed login, cookie JWT auth, CSRF, role-based protected workspaces, demo users, logout, and a public landing page. See `docs/SPRINT_1_STATUS.md` for the fuller acceptance record.

## In Progress

- Documentation cleanup is in place so future sessions start from `docs/STATE.md`, `docs/ARCHITECTURE.md`, and optionally `docs/DESIGN.md`.
- The working tree also contains recent UI/login validation changes; verify `git status` before committing.

## Next Priorities

- Keep Sprint 1 stable and documentation short.
- For Sprint 2, add project/activity foundation only after checking the source sprint/user-story docs.
- Add new database changes through Flyway migrations.

## Known Bugs And Risks

- Local Docker databases that previously ran removed experimental migrations may need a volume reset.
- `npx skills add anthropics/skills --skill frontend-design --agent codex` was attempted but did not complete visibly; `$frontend-design` was not visible afterwards.
- Production settings still need a real JWT secret, secure cookies over HTTPS, narrowed CORS origins, and demo seeding disabled.

## Decisions Made

- Keep the backend as a layered modular monolith.
- Use JWT in an `HttpOnly` cookie plus CSRF protection.
- Do not expose a public self-registration flow; users are expected to be provisioned outside the public UI.
- Keep the UI light, restrained, and operational.

## Last Session

- Documentation cleanup updated `AGENTS.md`, `docs/ARCHITECTURE.md`, and added `docs/DESIGN.md`, `docs/STATE.md`, `docs/DECISIONS.md`.
- Stale onboarding guidance was consolidated into `AGENTS.md`; deeper docs are now references to open only when needed.
- No application behavior was intentionally changed by this documentation pass.
