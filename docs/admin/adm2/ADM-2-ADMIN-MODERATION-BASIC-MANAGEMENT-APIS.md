# ADM-2 Admin Moderation and Basic Management APIs

## 1. Purpose

ADM-2 provides the minimum backend API surface required by a low-complexity Admin dashboard with four menus:

```text
Dashboard
Reports
Posts
Users
```

The UI remains out of scope. The backend reuses the existing moderation schema, database roles, audited functions and ADM-1 authorization guard.

## 2. Authoritative baseline

| Item | Value |
|---|---|
| Repository | `gycha0109-beep/journey-connect-backend` |
| Work-start `main` | `d4e1189953fe81a0df8ddc680c60076bf8fb51c4` |
| ADM-1 PR | `#48` |
| ADM-1 exact head | `c91c555cf0afcee60f65f4a2859cce4f1a0ab2af` |
| ADM-1 merge commit | `d4e1189953fe81a0df8ddc680c60076bf8fb51c4` |
| Admin API prefix | `/api/admin` |
| Admin application DB role | `DatabaseRole.ADMIN` |
| PostgreSQL role | `jc_admin` |

Entry verification confirmed PR #48 was merged and `main` had not drifted when the branch was created.

## 3. Reused database structures

```text
app_users.role
app_users.account_status
posts.status
posts.deleted_at
posts.purge_after
posts.moderation_status
posts.moderated_at
reports
admin_actions
jc_admin
```

Reused command functions:

```text
admin_finish_report
admin_hide_post
admin_restore_post
admin_suspend_user
admin_restore_user
```

The functions perform row locking, state validation, mutation and audit insertion inside one PostgreSQL transaction. ADM-2 does not duplicate those operations with application-side `UPDATE` or `INSERT INTO admin_actions` statements.

## 4. Migration decision

```text
SQL_CHANGE=NONE
DB_SCHEMA_CHANGE=NONE
```

No schema gap blocks the Admin MVP APIs. The existing two-state post moderation model, report terminal states, user account states, `jc_admin` grants and audited functions are sufficient.

No canonical SQL, Flyway migration, table, column, constraint, index or DB role is changed.

## 5. Authorization and DB role routing

Every service method:

1. calls `AdminAuthorizationGuard.requireActiveAdmin()`;
2. enters a `DatabaseRole.ADMIN` transaction;
3. executes only parameterized fixed SQL or an allowlisted existing function;
4. returns DTOs rather than entities or raw database rows.

The ADM-1 acceptance condition remains unchanged:

```text
authenticated=true
jwt_role=admin
request_identity_matches_subject=true
db_user_exists=true
app_users.role=admin
app_users.account_status=active
```

Anonymous requests receive `401`. Authenticated but unauthorized or stale Admin tokens receive the existing generic `ADMIN_ACCESS_DENIED` `403` response.

## 6. Endpoint contract

### 6.1 Dashboard

```text
GET /api/admin/dashboard
```

Response fields:

```text
totalUsers
activePostCount
pendingReportCount
suspendedUserCount
recentReports <= 5
recentAdminActions <= 5
```

`activePostCount` means `status=published` and `moderation_status=visible`.

The response omits infrastructure state, request or correlation IDs, database role names, raw audit snapshots, secrets, passwords, refresh tokens and internal governance data.

### 6.2 Reports

```text
GET  /api/admin/reports
GET  /api/admin/reports/{reportId}
POST /api/admin/reports/{reportId}/resolve
POST /api/admin/reports/{reportId}/dismiss
```

List filters:

```text
status=pending|in_review|resolved|rejected
targetType=user|post|comment
search=<reason category, reason detail, reporter username/display name, numeric reporter/report ID>
page>=0
1<=size<=100
```

Ordering is fixed:

```text
created_at DESC, id DESC
```

`dismiss` maps to the existing database terminal status `rejected` and audit action `report_reject`. The public API does not invent a duplicate `dismissed` database state.

Report detail exposes only minimum reporter identity, target identity, reason, status, timestamps, resolution note, current target state and whether terminal actions remain available.

### 6.3 Posts

```text
GET  /api/admin/posts
GET  /api/admin/posts/{postId}
POST /api/admin/posts/{postId}/hide
POST /api/admin/posts/{postId}/restore
```

List filters:

```text
moderationStatus=visible|hidden
visibility=public|followers|private
search=<post ID, author ID, author username/display name, title or content>
page>=0
1<=size<=100
```

Ordering is fixed:

```text
created_at DESC, id DESC
```

Content is returned as a bounded 1,000-character preview plus a truncation flag. Images, binaries, search scores and unrelated metadata are excluded.

Hide and restore never modify `posts.status`, `deleted_at` or `purge_after` and never physically delete a row.

### 6.4 Users

```text
GET  /api/admin/users
GET  /api/admin/users/{userId}
POST /api/admin/users/{userId}/suspend
POST /api/admin/users/{userId}/unsuspend
```

List filters:

```text
role=user|moderator|admin
accountStatus=active|suspended|withdrawn
search=<user ID, email, username or display name>
page>=0
1<=size<=100
```

Ordering is fixed:

```text
created_at DESC, id DESC
```

Returned fields are limited to user ID, email, username, display name, role, account status and lifecycle timestamps. Password hashes, refresh tokens, OAuth payloads, DB memberships and security metadata are excluded.

Because the current schema has no dedicated `suspended_at`, the API derives `suspendedAt` from `updated_at` only while `account_status=suspended`. This is explicitly a current-state timestamp, not a complete sanction history.

## 7. Request contract

All six commands accept:

```json
{
  "reason": "1 to 1000 characters after trim"
}
```

Blank or oversized reasons return:

```text
400 INVALID_ADMIN_COMMAND
```

Malformed JSON continues to use the existing global bad-request response.

## 8. Pagination contract

All list endpoints reuse `PageResponse`:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "last": true
}
```

```text
DEFAULT_PAGE_SIZE=20
MAX_PAGE_SIZE=100
STABLE_SORT=YES
UNBOUNDED_QUERY=NO
MAX_SEARCH_LENGTH=100
```

Sort identifiers are not accepted from clients. SQL fragments are never assembled from request values.

## 9. State transition contract

### Reports

| Current | Command | Result |
|---|---|---|
| `pending` | resolve | `resolved`, audited |
| `in_review` | resolve | `resolved`, audited |
| `pending` | dismiss | `rejected`, audited |
| `in_review` | dismiss | `rejected`, audited |
| `resolved` | resolve | idempotent no-op |
| `rejected` | dismiss | idempotent no-op |
| `resolved` | dismiss | `409 ADMIN_STATE_CONFLICT` |
| `rejected` | resolve | `409 ADMIN_STATE_CONFLICT` |

### Posts

| Current | Command | Result |
|---|---|---|
| `visible` | hide | `hidden`, audited |
| `hidden` | restore | `visible`, audited |
| `hidden` | hide | idempotent no-op |
| `visible` | restore | idempotent no-op |

The database constraint permits only `visible` and `hidden`; there is no third valid moderation state. Author deletion lifecycle remains separate.

### Users

| Current | Command | Result |
|---|---|---|
| `active` | suspend | `suspended`, audited |
| `suspended` | unsuspend | `active`, audited |
| `suspended` | suspend | idempotent no-op |
| `active` | unsuspend | idempotent no-op |
| `withdrawn` | unsuspend | `409 ADMIN_STATE_CONFLICT` |
| current Admin actor | suspend self | `409 ADMIN_STATE_CONFLICT` |

A successful suspension immediately blocks reuse of an existing Admin token because ADM-1 re-reads `account_status` on every Admin request.

## 10. Idempotency decision

ADM-2 uses state-based idempotency rather than introducing a new idempotency table or migration.

```text
same desired terminal/current state = 200, changed=false, no new audit row
conflicting report terminal state = 409
successful state transition = 200, changed=true, one audit row
```

This prevents duplicate audit records on UI retries while retaining explicit conflict behaviour for incompatible report outcomes.

## 11. Concurrency handling

The security-definer functions are the concurrency authority:

- reports use `SELECT ... FOR UPDATE` before terminal transition;
- posts use `SELECT ... FOR UPDATE` before moderation transition;
- users use `SELECT ... FOR UPDATE` before account-status transition;
- each function inserts audit after mutation inside the same transaction.

The service performs a pre-read for user-facing `404`, idempotency and conflict classification. If a race occurs after that read, a failed function call is reconciled by re-reading the authoritative state:

```text
same desired state after race = idempotent success
conflicting terminal report state = 409
missing target = 404
unexpected database failure = propagated for server-side failure handling
```

No distributed lock or application-side lock is introduced.

## 12. Audit mapping

| API command | DB function | Audit action | Before state | After state |
|---|---|---|---|---|
| resolve report | `admin_finish_report` | `report_resolve` | target snapshot + previous report status | action type implies `resolved` |
| dismiss report | `admin_finish_report` | `report_reject` | target snapshot + previous report status | action type implies `rejected` |
| hide post | `admin_hide_post` | `post_hide` | snapshot contains previous moderation status | action type implies `hidden` |
| restore post | `admin_restore_post` | `post_restore` | snapshot contains previous moderation status | action type implies `visible` |
| suspend user | `admin_suspend_user` | `user_suspend` | snapshot contains previous account status | action type implies `suspended` |
| unsuspend user | `admin_restore_user` | `user_restore` | snapshot contains previous account status | action type implies `active` |

```text
AUDIT_REQUIRED_FOR_ALL_COMMANDS=YES
AUDIT_AND_MUTATION_TRANSACTIONALLY_CONSISTENT=YES
AUDIT_REASON_REQUIRED=YES
AUDIT_ACTOR_DB_AUTHORITATIVE=YES
```

No token, password, request body, IP address or raw security context is written to audit metadata.

## 13. Error contract

| HTTP | Code | Meaning |
|---:|---|---|
| 401 | `AUTHENTICATION_REQUIRED` | no valid authentication |
| 403 | `ADMIN_ACCESS_DENIED` | authenticated but not an active DB-authoritative Admin |
| 404 | `ADMIN_TARGET_NOT_FOUND` | authorized Admin requested an absent target |
| 409 | `ADMIN_STATE_CONFLICT` | incompatible current state or self-suspension |
| 400 | `INVALID_ADMIN_COMMAND` | invalid reason, filter, search or pagination input |

Database function names, SQLSTATE values, role names, SQL text and stack traces are not returned to clients.

## 14. Changed files

Runtime:

```text
jc-backend/src/main/java/com/jc/backend/admin/AdminDtos.java
jc-backend/src/main/java/com/jc/backend/admin/AdminQueryPolicy.java
jc-backend/src/main/java/com/jc/backend/admin/AdminDashboardService.java
jc-backend/src/main/java/com/jc/backend/admin/AdminDashboardController.java
jc-backend/src/main/java/com/jc/backend/admin/AdminReportService.java
jc-backend/src/main/java/com/jc/backend/admin/AdminReportController.java
jc-backend/src/main/java/com/jc/backend/admin/AdminPostService.java
jc-backend/src/main/java/com/jc/backend/admin/AdminPostController.java
jc-backend/src/main/java/com/jc/backend/admin/AdminUserService.java
jc-backend/src/main/java/com/jc/backend/admin/AdminUserController.java
```

Verification and documentation:

```text
jc-backend/src/test/java/com/jc/backend/admin/AdminBasicApisIntegrationTest.java
docs/admin/adm2/**
verification/admin/adm2/**
.github/workflows/adm2-admin-basic-apis.yml
```

## 15. Tests and non-regression

The dedicated PostgreSQL 15 gate covers:

- all route-level 401/403 conditions;
- dashboard bounds and field minimization;
- report query, terminal transitions, idempotency, audit and concurrency;
- post query, hide/restore, no physical deletion, idempotency and audit;
- user query, sensitive-field exclusion, suspend/unsuspend, self-protection, withdrawn protection and stale-token rejection;
- bounded pagination;
- ADM-1 security and DB role routing regressions.

The repository-wide Backend PostgreSQL CI and Recommendation protection workflows remain required before completion.

## 16. Residual risks

1. `suspendedAt` is derived from `updated_at`; a future audit/history phase may provide a dedicated lifecycle projection without changing this MVP response contract.
2. Search predicates are safe and bounded but may scan at larger data volumes. No speculative index or materialized view is added in ADM-2.
3. State-based idempotency does not deduplicate two different requests before the first transaction commits; database row locks serialize them and the loser is reconciled against final state.
4. Direct privileged database changes outside the application remain an operational governance concern and are not solved by an API layer.
5. `admin_actions` stores before snapshots and action-derived after state rather than separate generic before/after columns. ADM-2 reuses that established contract instead of expanding the schema.

## 17. ADM-3 entry condition

```text
ADM2_ADMIN_BASIC_APIS_IMPLEMENTED=YES
ADM2_SCOPE_COMPLIANT=YES
ADM3_ENTRY=BLOCKED_PENDING_USER_APPROVAL
```

ADM-3 may begin only after this Draft PR is reviewed and explicitly approved. ADM-3 owns hardening, audit acceptance, error-contract review and end-to-end acceptance; it must not silently add frontend scope.
