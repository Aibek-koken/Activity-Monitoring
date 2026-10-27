# Sprint 3 Status — Progress, reminders, finish and submission

Last updated: 2026-10-24

Status: complete locally for US-08 through US-12.

## Delivered

| Story | Delivered behavior |
| --- | --- |
| US-08 | A translator with active assigned work and no daily record receives one in-app reminder on weekdays after 18:00. Recording work hides it. |
| US-09 | The responsible translator saves or updates the current week's remaining-volume estimate; assistants see it read-only and prior weeks remain in collapsed history. This flow was implemented before the remaining Sprint 3 work. |
| US-10 | An active responsible translator missing an estimate receives one reminder per activity on Friday after 16:00. Saving the estimate hides it. |
| US-11 | A responsible translator can explicitly finish an active activity only when the current week's estimate is zero. Daily records and estimates lock at `FINISHED`. |
| US-12 | A responsible translator can submit a `FINISHED` activity to the project's active responsible Chief Editor. One Review cycle is created in the same transaction as the `UNDER_CHECKING` transition. |

Reminders are persisted and deduplicated. The evaluator runs hourly and at startup in the configured business zone (`Asia/Almaty` by default). The translator list shows actionable links and allows marking a reminder read; stale or satisfied reminders are hidden. The detail page uses the existing route, server-computed permissions, an inline Finish confirmation, and a clear submitted state.

## Verification

- Backend H2 integration suite: `mvn test` passed.
- PostgreSQL-specific activity and concurrent Sprint 3 tests passed against a separate test database. Concurrent estimate saves create one weekly row; simultaneous Finish/Submit requests yield one successful transition and one Review.
- Frontend: 45 Vitest tests passed; ESLint and production build passed.
- Playwright against a disposable PostgreSQL database with `E2E_ISOLATED_DB=true`: 25 passed, 2 intentionally skipped. The mutating Finish/Submit path runs once on desktop; auth and read-only activity paths ran at desktop, tablet, and mobile widths. Without the flag, the mutating test is skipped.
- Flyway V1→V4 clean creation and V3→V4 upgrade were verified when the Sprint 3 foundation was built. Applied migrations were not edited during this work.

## Boundaries

Chief Editor checking, approval, and corrections belong to Sprint 4. Project Manager staffing and enforcement of the separate 5 responsible + 10 assisting active-assignment limits belong to Sprint 5. The original master user-story workbook is not available in this workspace; the implemented interpretation follows the Sprint 3 plan and available software/database design documents.
