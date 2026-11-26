# Run EasyLang locally

Sprints 1–4 run locally. Sprint 4 now adds weekly and final PDF versions, an in-app PDF review workbench with linked marks and one decision, correction pages/rework accounting, generated annotated PDFs, and honest Chief Editor availability/workload. The commands below run the current application.

## 1. PostgreSQL

```bash
cd "/Users/kuanyszarylkasyn/Desktop/Edu/SOE 202/soe_202_proj"
docker compose up -d --wait
```

The EasyLang database is published on `localhost:55432`; it does not use the other project's port `5433`.

## 2. Backend

```bash
cd "/Users/kuanyszarylkasyn/Desktop/Edu/SOE 202/soe_202_proj/backend"
mvn spring-boot:run
```

Verify it at `http://localhost:8080/actuator/health`.

Review files default to `backend/data/review-documents` when Maven is started from `backend/`. The directory is private application storage, not a public static-file path and is ignored by Git. Override it with an absolute writable path for deployed or disposable environments:

```bash
DOCUMENT_STORAGE_ROOT=/absolute/private/easylang-review-documents mvn spring-boot:run
```

The validated file limit defaults to 20 MiB (`DOCUMENT_STORAGE_MAX_SIZE_BYTES=20971520`). Spring's multipart transport limits default to 21 MB per file and 22 MB per request and can be overridden with `DOCUMENT_STORAGE_MULTIPART_MAX_FILE_SIZE` and `DOCUMENT_STORAGE_MULTIPART_MAX_REQUEST_SIZE`. Production storage needs its own access controls, persistence, backup, and restore policy alongside PostgreSQL.

## 3. Frontend

```bash
cd "/Users/kuanyszarylkasyn/Desktop/Edu/SOE 202/soe_202_proj/frontend"
npm install
npm run dev
```

Open `http://localhost:5173`.

### Maya showcase data

After the backend starts, `translator@easylang.local` receives idempotent non-production showcase data:

- `EL-2026-001`: weekly remaining history for the current week and the two previous weeks (`120`, `260`, and `410` pages);
- one current daily-record reminder;
- one current weekly-estimate reminder for `EL-2026-002`, where the estimate is intentionally still missing.

Restarting the backend applies missing showcase rows without duplicating existing ones. Production and automated test profiles do not receive this showcase data.

Local startup also seeds 10 additional `Demo ·` projects, each with two Maya activities and Sofia Bianchi (`editor2@easylang.local`, password `Demo123!`) as the responsible Chief Editor. Sofia is also made responsible for Maya's other demo projects that lack a responsible editor, including `Banking Mobile App Strings`; Elias Brooks remains responsible only for `Central Asia Legal Portal`. This is a non-production demo exception to the normal one-responsible-project-per-editor assignment service. The open review PDFs include a sample mark. Maya's notification history also receives multiple idempotent examples across reminder, correction, and daily-record-edit outcomes. Set `APP_SEED_DEMO_PORTFOLIO=false` before starting the backend to skip the extra 10-project portfolio for an isolated test run; the existing Maya project assignments and notification examples are still seeded. Existing demo rows are reassigned to Sofia on the next startup, not deleted.

A Chief Editor is responsible for one project. Additional projects are assisting assignments, which the Project Manager may add when the editor has spare capacity; the backend assignment policy (`ChiefEditorAssignmentService`) enforces the one-responsible-project limit and V8 demoted legacy duplicate assignments.

## Database inspection

Postgres:

```bash
docker compose exec postgres psql -U easylang -d easylang
```

Посмотреть допустимые активности:

```sql
SELECT activity_number, activity_name, status
FROM activities
ORDER BY activity_number;
```

Посмотреть назначения:

```sql
SELECT a.activity_number, u.email, at.is_responsible
FROM activity_translators at
JOIN activities a ON a.id = at.activity_id
JOIN app_users u ON u.id = at.translator_id
ORDER BY a.activity_number;
```

- `backend/sql/mock-data.sql` — большие тестовые данные.

- `backend/src/main/java/.../model/ActivityStatus.java` — все статусы.

- `backend/src/main/java/.../service/TranslatorActivityService.java` — главная бизнес-логика переводчика.

- `backend/src/main/java/.../service/TranslatorProgressService.java` — недельная оценка, завершение и отправка на проверку.

- `backend/src/main/java/.../service/ReminderService.java` — проверка ежедневных и еженедельных напоминаний.

- `frontend/src/pages/TranslatorActivitiesPage.tsx` — список активностей.

- `frontend/src/pages/TranslatorActivityDetailPage.tsx` — детали, запись страниц/переделки, версии и исправления.

- `frontend/src/pages/ChiefEditorDashboardPage.tsx` — очередь версий, доступность и нагрузка редактора.

- `frontend/src/pages/ChiefEditorReviewDetailPage.tsx` — PDF-проверка, пометки и решение.

- `frontend/src/components/PdfReviewViewer.tsx` — PDF.js, выделение текста, зачёркивание и область.

- `backend/src/main/java/.../service/ReviewAnnotationService.java` — пометки и PDF с замечаниями.

- `backend/src/main/java/.../service/TranslatorProgressService.java` — недельные/финальные версии и исправления.

- `frontend/src/lib/activity-format.ts` — как форматируются числа/даты/часы.

- `frontend/src/lib/validation.ts` — UI-валидация.

## Automated checks

```bash
cd backend && mvn test
cd frontend && npm test && npm run lint && npm run build
```

PostgreSQL-specific tests use a separate database named `easylang_postgres_query_test` and run with `POSTGRES_INTEGRATION_TESTS=true` from `backend/`. The Playwright Sprint 3 write-through test changes activity state, so it requires a disposable database and the explicit flag `E2E_ISOLATED_DB=true`:

```bash
docker compose exec postgres createdb -U easylang easylang_sprint3_e2e
# Start the API from backend/ with:
DB_URL=jdbc:postgresql://localhost:55432/easylang_sprint3_e2e mvn spring-boot:run
# Start Vite from frontend/ in another terminal, then run from frontend/:
E2E_ISOLATED_DB=true npm run test:e2e -- --workers=1
```

Without the flag, the state-changing browser test is skipped. Its desktop variant runs once; tablet and mobile runs cover the read-only path. Recreate the disposable database before repeating the full write-through flow.

Reminder evaluation uses the business zone (`BUSINESS_ZONE`, default `Asia/Almaty`) and is enabled by default. `REMINDER_SCHEDULING_ENABLED=false` disables the scheduled evaluator for deterministic tests; it does not disable the reminder API.

## Isolated Sprint 4 acceptance flow

The Sprint 4 command below is destructive only to its own temporary directory. It creates a temporary PostgreSQL cluster/database and document root, selects random loopback ports, runs the three PostgreSQL migration/concurrency acceptance tests, starts isolated backend/frontend processes, runs the desktop cross-role Playwright flow, and tears all of it down. It does not connect to the Compose `easylang` database.

Prerequisites are frontend dependencies plus local PostgreSQL command-line tools (`initdb`, `pg_ctl`, and `createdb`). On macOS the script also detects Postgres.app at its standard `latest` path.

```bash
cd frontend
npm run test:e2e:sprint4-isolated
```

This flow covers the final-version correction loop and the new weekly loop: weekly version while active, in-app marks, annotated PDF download, return with pages, rework recording, corrected weekly version, weekly approval that keeps the activity active, then Finish → final submission → final approval → Completed. It also covers missing-CSRF/cross-role denial and immutable cycle bytes/history. Fresh results and remaining limitations are in `docs/SPRINT_4_REWORK_REPORT.md` and `docs/SPRINT_4_STATUS.md`.
