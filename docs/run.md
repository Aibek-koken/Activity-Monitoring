# Run EasyLang locally

Sprints 1–3 run locally. Sprint 3 adds weekly estimates, in-app reminders, Finish, and submission for Chief Editor checking. The commands below run the current application.

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

## 3. Frontend

```bash
cd "/Users/kuanyszarylkasyn/Desktop/Edu/SOE 202/soe_202_proj/frontend"
npm install
npm run dev
```

Open `http://localhost:5173`.

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

- `frontend/src/pages/TranslatorActivityDetailPage.tsx` — детали, запись объёма, редактирование.

- `frontend/src/lib/activity-format.ts` — как форматируются числа/даты/часы.

- `frontend/src/lib/validation.ts` — UI-валидация.

## Sprint 3 checks

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
