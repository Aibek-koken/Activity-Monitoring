# Sprint 1 API Contract

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
