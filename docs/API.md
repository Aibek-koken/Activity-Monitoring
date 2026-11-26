# Current API Contract (Sprints 1–4)

Last updated: 2026-11-14

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

The password hash and JWT are never returned in JSON. Authentication uses an `HttpOnly` cookie, not browser storage.

## Role verification endpoints

| Method | Path | Required role |
| --- | --- | --- |
| GET | `/workspaces/translator` | `TRANSLATOR` |
| GET | `/workspaces/chief-editor` | `CHIEF_EDITOR` |
| GET | `/workspaces/project-manager` | `PROJECT_MANAGER` |

These endpoints prove that the server, not only the React router, enforces role separation. Domain endpoints follow the same server-side rule.

## Translator activities, daily work, and versions

Every endpoint below requires the `TRANSLATOR` role and an assignment to the requested activity. Detail and record endpoints return 404 for an unassigned activity. State-changing requests also require the CSRF token.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/translator/activities` | List the signed-in translator's assignments; optional `q` searches activity number/name |
| GET | `/translator/activities/{activityId}` | Return assigned activity detail, cumulative pages, rework summary, work records, estimates, and permissions |
| POST | `/translator/activities/{activityId}/work-records` | Create one daily record; return refreshed activity detail |
| PUT | `/translator/activities/{activityId}/work-records/{recordId}` | Edit this translator's volume, rework pages, and hours |
| PUT | `/translator/activities/{activityId}/weekly-estimates/current` | Responsible translator creates/updates the current week's remaining-page estimate |
| POST | `/translator/activities/{activityId}/finish` | Responsible translator finishes an active activity after saving zero remaining pages |
| POST multipart | `/translator/activities/{activityId}/weekly-versions` | Responsible translator sends the current week's `file` while the activity stays active |
| POST multipart | `/translator/activities/{activityId}/submit-for-checking` | Responsible translator submits a `FINISHED` activity with a required `file` as the final version |
| GET | `/translator/activities/{activityId}/reviews` | Responsible translator reads immutable cycles newest-first, including kind, marks count, feedback, and document metadata |
| POST multipart | `/translator/activities/{activityId}/reviews/{reviewId}/legacy-attachment` | Attach one file to an open fileless Sprint 3 Review |
| POST multipart | `/translator/activities/{activityId}/resubmit-for-checking` | Upload a corrected `file` for the latest weekly or final correction |

The list response is `{ "activities": [...] }`. Each summary contains `id`, `activityNumber`, `activityName`, `projectName`, `status`, `responsible`, `assignedDate`, and `progress`. Detail adds `createdDate`, `workRecords`, `currentWeek`, nullable `currentWeekEstimate`, `weeklyEstimates` (newest first), `rework`, and server-computed `permissions`.

`progress` contains `totalTranslatedVolume` (first-time pages recorded by the signed-in translator), `recordCount`, and nullable `lastRecordDate`. Each work record contains `id`, `recordDate`, `translatedVolume`, `reworkPages`, and nullable `workHours`.

`rework` contains `correctionReviewId`, `correctionPages`, `reworkRecordedPages`, `outstandingReworkPages`, nullable `remainingPages`, `totalRemainingPages`, and `hasWeeklyEstimate`. `totalRemainingPages` is the current weekly remaining estimate plus outstanding rework. Rework pages never increase `totalTranslatedVolume`.

`permissions` contains `canEditWorkRecords`, `canEditWeeklyEstimate`, `canFinish`, `canSubmitForChecking`, `canSubmitWeeklyVersion`, `hasResponsibleChiefEditor`, `hasOpenReview`, and `hasOutstandingRework`. The server calculates these values.

Create body:

```json
{
  "recordDate": "2026-11-14",
  "translatedVolume": 0,
  "reworkPages": 2,
  "workHours": 1.50
}
```

Update body has `translatedVolume`, optional `reworkPages`, and optional `workHours`; the record date cannot change. Pages must be 0–1000 with up to two decimals; hours, if supplied, must be 0–24. A normal record needs translated pages. During a correction, a record may contain zero translated pages only when it contains rework pages. Rework above the outstanding correction pages is rejected. Rework hours count as working time; rework pages do not count as first-time translated pages.

Estimate body: `{ "remainingTextVolume": 120.50 }`. The server calculates the Monday week start in `Asia/Almaty`; the client cannot submit a date. The value must be non-negative and fit `NUMERIC(14,2)`. Repeated saves update the same activity/week row. Assistants receive 403, unassigned translators 404, and estimates are locked once the activity leaves `ASSIGNED`/`IN_PROGRESS`.

Lifecycle rules:

- A **weekly version** is allowed only while the activity is `ASSIGNED` or `IN_PROGRESS`, has no open review, is not waiting for a correction, and has a responsible Chief Editor. It does not change the Activity status and does not prevent daily records. Sending the same SHA-256 document again for the same week returns the current detail without creating a cycle. A different second version while one is open returns 409. Approval closes the weekly version but keeps the activity active.
- `finish` accepts `ASSIGNED`/`IN_PROGRESS` with zero remaining pages for the current week and no open review or pending correction. It moves the activity to `FINISHED`.
- A **final version** is submitted only from `FINISHED`; it moves the activity to `UNDER_CHECKING`. Final approval completes the activity. Final correction moves it to `CORRECTION_REQUIRED`; the corrected final upload returns it to `UNDER_CHECKING`.
- A weekly correction keeps the activity `IN_PROGRESS`. Its corrected upload creates the next weekly cycle with the same week start and leaves the activity active.

Upload routes accept only a nonempty PDF or DOCX whose filename, extension, and content structure pass server validation. The default application limit is 20 MiB, with multipart transport limits set slightly higher. DOCX validation rejects unsafe ZIP paths, excessive expansion, missing required package entries, and macros. PDF validation checks the header, object/catalog structure, trailer, and cross-reference target. The server chooses the stored content type, generates an opaque storage key, records original name, size, and SHA-256, and never replaces a prior version.

Review history is `{ "reviews": [...] }`. Each cycle contains `id`, `cycleNumber`, `kind` (`WEEKLY`/`FINAL`), nullable `weekStartDate`, `submittedAt`, nullable `result`, `feedback`, `expectedCheckingHours`, `correctionPages`, `decidedAt`, `annotationCount`, and `document`. Jackson omits null fields. Document metadata contains `originalName`, `contentType`, `sizeBytes`, `sha256`, `downloadUrl`, and nullable `annotatedDownloadUrl`.

## Review marks

| Method | Path | Authority |
| --- | --- | --- |
| GET | `/reviews/{reviewId}/annotations` | Responsible translator or Review-recorded Chief Editor |
| POST | `/chief-editor/reviews/{reviewId}/annotations` | Review-recorded Chief Editor, CSRF, open PDF review |
| PUT | `/chief-editor/reviews/{reviewId}/annotations/{annotationId}` | Review-recorded Chief Editor, CSRF, open PDF review |
| DELETE | `/chief-editor/reviews/{reviewId}/annotations/{annotationId}` | Review-recorded Chief Editor, CSRF, open PDF review |

Create/update body:

```json
{
  "pageNumber": 1,
  "kind": "HIGHLIGHT",
  "rects": [
    { "x": 0.10, "y": 0.20, "width": 0.32, "height": 0.04 }
  ],
  "quotedText": "Section 2",
  "comment": "Use the approved glossary term.",
  "suggestedReplacement": "approved term"
}
```

`kind` is `HIGHLIGHT`, `STRIKETHROUGH`, or `RECT`. Coordinates are normalized to the page (0–1, top-left origin). One mark may contain 1–20 rectangles. At least one of selected text, comment, or replacement is required. Marks can be changed only while the review is open and only for a PDF; DOCX reviews are download-only. A decision locks every mark. A review accepts at most 500 marks.

## Review documents

`GET /reviews/{reviewId}/document` requires the responsible translator assignment or the Chief Editor recorded on that Review. It streams the immutable original bytes with `Content-Disposition: attachment`, `Cache-Control: no-store`, the stored content type, and `X-Content-Type-Options: nosniff`.

`GET /reviews/{reviewId}/annotated-document` uses the same ownership rule and generates a new PDF with yellow highlights, red strikethroughs/boxes, and an appended review-notes page. The original file is never modified. It returns 409 for DOCX reviews or a review without marks.

Missing, fileless, unauthorized, or unavailable documents return 404 without filename or document headers. Project Managers and unrelated roles are rejected before document access.

## Chief Editor review workspace

Every endpoint below requires the `CHIEF_EDITOR` role. Writes also require CSRF. Project assignment and Review ownership are deliberately different: the project list uses current assignment, while queue/detail/workload and decisions use the Chief Editor recorded on each Review.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/chief-editor/projects` | Current assigned projects |
| GET | `/chief-editor/reviews?status=open&page=0&size=20` | Recorded open Reviews newest-first; size 1–100 |
| GET | `/chief-editor/reviews/{reviewId}` | Read an owned open or closed Review with marks and earlier cycles |
| PUT | `/chief-editor/reviews/{reviewId}/expected-time` | Set/replace open Review expected hours |
| POST | `/chief-editor/reviews/{reviewId}/decision` | Record one decision and close the version |
| GET | `/chief-editor/workload` | Honest open-queue aggregate and availability comparison |
| GET | `/chief-editor/availability` | Read the signed-in editor's weekly available hours |
| PUT | `/chief-editor/availability` | Set weekly available hours (`0.50`–`168.00`) |

Expected-time body: `{ "expectedCheckingHours": 1.50 }`. The value must be 0.01–1000 with at most two decimals and can change only while the review is open.

Decision body:

```json
{
  "result": "CORRECTION_REQUESTED",
  "feedback": "Fix terminology in section 2.",
  "correctionPages": 3
}
```

Correction feedback and `correctionPages` (1–10000) are required. Approval feedback is optional. A correction closes the current version and notifies the responsible translator; for a final version it moves the Activity to `CORRECTION_REQUIRED`. Approval closes the version; a final approval moves the Activity to `COMPLETED`, while a weekly approval keeps it active. Closed/stale decisions return 409. Another Chief Editor receives 404.

Project response is `{ "projects": [...] }`. Queue rows include Activity/project identity, `cycleNumber`, `kind`, nullable `weekStartDate`, submission time, nullable expected hours, `hasDocument`, and `annotationCount`.

Workload response:

```json
{
  "openCount": 4,
  "knownExpectedHours": 11.90,
  "missingEstimateCount": 1,
  "availableWeeklyHours": 10.00,
  "remainingHours": -1.90,
  "queueExceedsWeek": true,
  "completeForecast": false,
  "availabilityUpdatedAt": "2026-11-14T14:00:00Z"
}
```

`queueExceedsWeek` is null until availability is set. `completeForecast` is false when any open version still has no estimate. The API does not claim a full forecast while `missingEstimateCount` is greater than zero.

## Reminders

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/translator/reminders` | List unread, still-relevant notifications |
| PATCH | `/translator/reminders/{reminderId}/read` | Mark the signed-in translator's notification read; CSRF required |

Types are `DAILY_RECORD_REMINDER`, `WEEKLY_ESTIMATE_REMINDER`, `WEEKLY_SUBMISSION_REMINDER`, and `CORRECTION_REQUESTED`. Daily reminders are user-level; the other three link to a responsible activity. The Friday 16:00 evaluator creates one weekly-submission reminder per active responsible activity that has no weekly version for that week. Sending late is still accepted. This legacy endpoint hides stale reminders and resolved corrections; `/notifications` retains them in history until explicit deletion.

## Notification history and record edit approval

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/notifications?page=0&size=20` | Paginated signed-in user's notification history, including read and past items |
| PATCH | `/notifications/{id}/read` | Mark one owned notification read |
| PATCH | `/notifications/read-all` | Mark all owned notifications read, across all pages |
| DELETE | `/notifications/{id}` | Soft-delete one owned notification |
| POST | `/translator/activities/{activityId}/work-records/{recordId}/edit-requests` | Request permission to edit an owned record; body: `{ "reason": "..." }` |
| GET | `/translator/work-record-edit-requests` | Translator's requests and decisions |
| GET | `/project-manager/work-record-edit-requests` | Requests for the signed-in manager's projects |
| POST | `/project-manager/work-record-edit-requests/{requestId}/decision` | Approve or decline; body: `{ "approved": true, "comment": "..." }` |

The existing work-record `PUT` requires an `editRequestId` for an approved request. Its approval is consumed by one successful edit. Requests and decisions are limited to active activities, enforced by the backend. New notification types are `RECORD_EDIT_REQUESTED`, `RECORD_EDIT_APPROVED`, and `RECORD_EDIT_REJECTED`. Reading never deletes a notification; deletion is explicit.

Chief Editor `GET /chief-editor/reviews` also accepts `projectId`, `kind` (`WEEKLY` or `FINAL`), and `q` to filter the open queue. A correction decision can send `correctionPageNumbers: [1, 2, 5]`; the server sorts and deduplicates the list, derives its count, and returns both fields in review history. The legacy `correctionPages` count remains accepted for older clients.

## Errors

Expected status codes are `400` for validation or invalid document content, `401` for missing/invalid authentication, `403` for a wrong role, CSRF failure, or an assistant attempting a responsible-only action, `404` for hidden ownership/missing resources, and `409` for duplicate, stale, closed, locked, or invalid lifecycle transitions. State-changing JSON and multipart requests require the CSRF header.
