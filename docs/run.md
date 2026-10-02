quick start

docker compose up -d --wait
cd backend && mvn spring-boot:run

cd frontend && npm run dev





# Run EasyLang locally


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























БД:

```bash
docker compose up -d --wait
cd backend && mvn spring-boot:run
cd frontend && npm run dev
```

Postgres:

```bash
docker compose exec postgres psql -U easylang -d easylang
```

Поменять статус активности:

```sql
UPDATE activities
SET status = 'CORRECTION_REQUIRED'
WHERE activity_number = 'EL-2026-118';
```

Потом обнови браузер на `/translator`.

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

- `frontend/src/pages/TranslatorActivitiesPage.tsx` — список активностей.

- `frontend/src/pages/TranslatorActivityDetailPage.tsx` — детали, запись объёма, редактирование.

- `frontend/src/lib/activity-format.ts` — как форматируются числа/даты/часы.

- `frontend/src/lib/validation.ts` — UI-валидация.