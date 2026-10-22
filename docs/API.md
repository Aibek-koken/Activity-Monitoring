# Current API Contract (Sprints 1–2)

Last updated: 2026-10-22

Base path: `/api/v1`

## Authentication

| Method | Path | Authentication | Purpose |
| --- | --- | --- | --- |
| GET | `/auth/csrf` | Public | Issue a CSRF token and cookie |
| POST | `/auth/login` | Public + CSRF | Authenticate and set the access cookie |
| GET | `/auth/me` | Required | Return the current safe user profile |
| POST | `/auth/logout` | Required + CSRF | Clear the access cookie |

Login request:

```json
{
  "email": "translator@easylang.local",
  "password": "Demo123!"
}
```

Successful login response:

```json
{
  "user": {
    "id": 1,
    "initials": "MC",
    "fullName": "Maya Chen",
    "email": "translator@easylang.local",
    "role": "TRANSLATOR"
  }
}
```

The password hash and JWT are never returned in JSON.

## Role verification endpoints

| Method | Path | Required role |
| --- | --- | --- |
| GET | `/workspaces/translator` | `TRANSLATOR` |
| GET | `/workspaces/chief-editor` | `CHIEF_EDITOR` |
| GET | `/workspaces/project-manager` | `PROJECT_MANAGER` |

These small endpoints prove that the server, not only the React router, enforces role separation. Future domain endpoints should follow the same server-side rule.

## Translator activities and daily work records

Every endpoint below requires the `TRANSLATOR` role and an assignment to the requested activity. Detail and record endpoints return 404 for an unassigned activity. State-changing requests also require the CSRF token.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/translator/activities` | List the signed-in translator's assignments; optional `q` searches activity number/name case-insensitively |
| GET | `/translator/activities/{activityId}` | Return assigned activity detail, cumulative translated volume, and this translator's work records |
| POST | `/translator/activities/{activityId}/work-records` | Create one daily record; return refreshed activity detail |
| PUT | `/translator/activities/{activityId}/work-records/{recordId}` | Edit this translator's existing record volume/hours; return refreshed activity detail |

The list response is `{ "activities": [...] }`. Each summary contains `id`, `activityNumber`, `activityName`, `projectName`, `status`, `responsible`, `assignedDate`, and `progress`. Detail adds `createdDate` and `workRecords`. `progress` currently contains `totalTranslatedVolume`, `recordCount`, and nullable `lastRecordDate`; it is the signed-in translator's cumulative work, not the later estimated-total metric. Each work record contains `id`, `recordDate`, `translatedVolume`, and nullable `workHours`.

Create body: `{ "recordDate": "2026-10-22", "translatedVolume": 12.50, "workHours": 4.25 }`. Update body has `translatedVolume` and optional `workHours`; record date cannot be changed. Volume must be greater than zero and at most 1000 with up to two decimals; hours, if supplied, must be 0–24 with up to two decimals. Future record dates are rejected. A second record for the same translator/activity/date returns 409.

The UI's assignment role/status/assigned-date filters are currently client-side filters over this authorized list. The detail history's date range is also client-side; neither changes API progress totals.

Sprint 3 weekly-estimate, finish, and reminder routes are planned in `SPRINT_3_PLAN.md` and do not exist yet.

## Error shape

```json
{
  "timestamp": "2026-09-21T12:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "path": "/api/v1/auth/login",
  "fieldErrors": {}
}
```

Expected status codes are `400` for validation, `401` for missing/invalid authentication, and `403` for an authenticated user with the wrong role.
