# Run EasyLang locally

Use three terminal windows.

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
