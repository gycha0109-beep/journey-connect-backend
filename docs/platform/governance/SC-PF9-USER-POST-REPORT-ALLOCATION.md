# SC PF9 User Post Report Allocation

## Document identity

| Field | Value |
|---|---|
| decision ID | `sc-pf9-user-post-report-v1` |
| status | `APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED` |
| prerequisite main | `d536827368583df0369c1dccc9bd5b07454ba466` |
| product slice | `JC-PF9 User Post Report Submission` |
| donor reference | `YTAK99/Journey-Connect PR #19` |
| canonical SQL tail | `68` |
| runtime owner | `APP` |

## Decision

PF9 allocates one bounded authenticated user-facing API over the already-authoritative canonical report command.

```text
CONTRACT_ID=user-post-report-v1
ENDPOINT=POST /api/v1/posts/{postId}/reports
AUTHENTICATION=REQUIRED
TARGET_TYPE=post
WRITE_ROLE=APP
DATABASE_COMMAND=public.submit_report(varchar,bigint,varchar,varchar)
REPORT_STATUS=pending
DIRECT_REPORT_TABLE_WRITE=FORBIDDEN
NEW_SQL=NONE
SQL_69_PLUS=UNALLOCATED
```

The API submits a report for a post that the authenticated requester is currently allowed to report under the existing canonical database command. PF9 does not create a second report table, second moderation queue, second visibility predicate, or alternate duplicate policy.

## Canonical database authority reused

The existing `public.submit_report(varchar,bigint,varchar,varchar)` command remains authoritative for report creation. It is already:

- `SECURITY DEFINER` owned by `jc_security_owner`;
- executable by `jc_app`;
- backed by `public.require_active_user()`;
- backed by `public.can_user_view_post(...)` for post visibility;
- responsible for the immutable target evidence snapshot;
- responsible for inserting into `public.reports`;
- constrained by `reports_open_target_uq` so one reporter cannot hold more than one `pending`/`in_review` report for the same target.

PF9 runtime must call this command instead of directly inserting, updating, selecting-for-write, or deleting `public.reports`.

## Reason contract

PF9 exposes the canonical report reason vocabulary already enforced by `public.reports.reason_category`:

- `spam`
- `harassment`
- `hate`
- `sexual_content`
- `violence`
- `misinformation`
- `privacy`
- `copyright`
- `other`

`reasonDetail` is optional and, when supplied, is trimmed and limited to 1000 characters. Unsupported categories are rejected before or by the canonical command and must produce a stable client-domain error rather than raw PostgreSQL text.

The donor repository's narrower reason set is reference-only and is not authoritative for the personal repository.

## Visibility and self-report semantics

The canonical command deliberately uses one fail-closed reportability predicate for post targets:

- post exists;
- post is published;
- moderation status is visible;
- requester can currently view it under `public.can_user_view_post(...)`;
- requester is not the post author.

A missing, inaccessible, moderation-hidden, non-published, or self-authored post is therefore not exposed as a distinct reportability reason. PF9 maps that canonical `not found` outcome to one stable `REPORT_TARGET_NOT_FOUND` response.

PF9 must not add a parallel Java visibility query that can drift from `public.can_user_view_post(...)`.

## Duplicate semantics

The canonical partial unique index remains authoritative:

```text
(reporter_id, target_type, target_entity_id)
WHERE reporter_id IS NOT NULL
  AND status IN ('pending', 'in_review')
```

A duplicate open report maps to `409 REPORT_ALREADY_EXISTS`.

Once an earlier report is terminal (`resolved` or `rejected`), the canonical schema permits a later new report. PF9 does not invent permanent per-user/per-target dedupe.

## Runtime allocation

The successor implementation may add a bounded package for the user-facing report entry point, containing only the minimum equivalent of:

- `UserReportController`
- `UserReportDtos`
- `UserReportService`
- dedicated PF9 tests

The service must execute under `@DatabaseTransactional(role = DatabaseRole.APP)` and call `public.submit_report(...)`.

No modification is allocated for:

- `PostController`
- `PostService`
- `AdminReportController`
- `AdminReportService`
- `SecurityConfig`
- Recommendation runtime or persistence
- Search runtime
- Notification runtime

If an implementation discovers one of these files is genuinely required, it must stop and obtain a new governance decision before changing that surface.

## HTTP/domain outcomes

The successor implementation must provide stable outcomes at minimum:

- successful report: `201`, generated `reportId`, status `pending`;
- unauthenticated request: existing `401 AUTHENTICATION_REQUIRED` contract;
- inactive requester: `403 USER_INACTIVE`;
- missing/inaccessible/hidden/non-published/self-authored target: `404 REPORT_TARGET_NOT_FOUND`;
- duplicate open report: `409 REPORT_ALREADY_EXISTS`;
- unsupported reason category: `400 INVALID_REPORT_REASON`;
- invalid request shape/detail length: existing validation error contract.

Raw SQLSTATE, constraint names, or PostgreSQL exception messages must not be exposed as the API contract.

## Database / SQL boundary

PF9 allocates no SQL.

- canonical SQL `01..68` remains unchanged;
- SQL `69+` remains unallocated;
- no table, column, index, trigger, function, procedure, role, grant, or sequence change is authorized;
- `jc_app` keeps no direct `INSERT/UPDATE/DELETE` privilege on `public.reports`;
- the existing `submit_report` EXECUTE capability is reused exactly.

## Admin boundary

PF9 creates reports that immediately enter the already-authoritative `public.reports` moderation queue. It does not change admin list/detail/resolve/reject behavior or admin authorization.

No report-resolution notification is allocated by PF9.

## Recommendation / Search / Notification boundary

A report submission is moderation evidence only. PF9 must not modify or emit:

- recommendation behavior events;
- recommendation exposure/impression evidence;
- Search ranking/exposure/CTR evidence;
- notifications;
- push/email/WebSocket/SSE events.

## Donor adaptation rule

`YTAK99/Journey-Connect` PR #19 is product-semantic reference only. Its direct `admin_report` insert, table names, reason vocabulary, transaction model, and visibility query are not authoritative here.

The personal repository's canonical `public.reports`, `public.submit_report(...)`, APP/AUTH/ADMIN role split, and PostgreSQL visibility/evidence rules are authoritative.

## Required verification

The successor implementation must verify at minimum:

1. authenticated active non-author can report a visible reportable post;
2. persisted row enters the existing admin report queue as `pending`;
3. immutable target snapshot identifies the exact post;
4. all nine canonical reason categories are accepted;
5. unsupported reason category is rejected with the stable API code;
6. reason detail length boundary is enforced;
7. self-authored post is hidden behind `REPORT_TARGET_NOT_FOUND`;
8. missing/private-or-inaccessible/hidden/non-published post is hidden behind the same outcome;
9. duplicate `pending`/`in_review` report returns `REPORT_ALREADY_EXISTS`;
10. terminal previous report permits a later report under the canonical unique-index rule;
11. inactive requester is rejected;
12. unauthenticated endpoint access returns the existing authentication error;
13. runtime contains no direct `insert into public.reports` path;
14. APP remains unable to directly mutate `public.reports` while retaining EXECUTE on `public.submit_report(...)`;
15. SQL69+ remains absent.

The implementation must pass canonical PostgreSQL 15/18 plus inherited Backend/P0/Admin/PIE protected regression gates on one exact head before merge.

## Explicit non-goals

PF9 does not authorize:

- user/comment report endpoints;
- report list/history for normal users;
- report withdrawal/edit;
- report-resolution notification;
- moderation workflow changes;
- automated moderation;
- Recommendation/Search feedback;
- frontend work;
- deployment or production traffic activation;
- SQL `69+`.
