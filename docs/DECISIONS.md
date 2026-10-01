# Decisions

Last updated: 2026-10-01

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-09-22 | Use a layered modular monolith for the backend. | Sprint 1 has one small domain and one database; separate services would add deployment and consistency cost too early. |
| 2026-09-22 | Store JWTs in `HttpOnly`, `SameSite=Lax` cookies and keep CSRF protection enabled. | Reduces JavaScript token exposure while protecting state-changing cookie requests. |
| 2026-09-22 | Let Flyway own schema evolution and keep Hibernate on validation. | Avoids silent schema drift and keeps database changes reviewable. |
| 2026-09-22 | Seed demo users in application code. | BCrypt hashes are generated securely and seeding remains idempotent for local/demo environments. |
| 2026-09-24 | Add a public landing page without changing backend auth behavior. | Presents the product direction while keeping Sprint 1 scope limited to auth/workspace access. |
| 2026-10-01 | Remove public self-registration from the current product flow. | Staff accounts should be provisioned outside the public UI; login errors remain generic for security. |
| 2026-10-01 | Keep the UI style calm, light, and operational. | The product is a work tool for translators, editors, and project managers, not a marketing-heavy interface. |
