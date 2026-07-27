# ADM-0 Admin Capability, Schema and Repository Integration Baseline

## 1. Decision summary

```text
RESULT=ADMIN_CAPABILITY_SCHEMA_AND_INTEGRATION_BASELINE_ESTABLISHED
ADMIN_MVP_SCOPE=DEFINED
ROLE_PERMISSION_MODEL=DEFINED
SCHEMA_INVENTORY=COMPLETE
SCHEMA_GAP_ANALYSIS=COMPLETE
MIGRATION_PLAN=DEFINED
ADMIN_API_CONTRACT=DEFINED
MODERATION_STATE_MACHINE=DEFINED
AUDIT_CONTRACT=DEFINED
UI_REUSE_MATRIX=DEFINED
FINAL_REPOSITORY_SYNC_PLAN=DEFINED
ADM1_ENTRY=BLOCKED_PENDING_USER_APPROVAL
```

ADM-0 is contract-only. It reuses the existing database security foundation and changes no runtime, frontend, SQL or database object.

## 2. Backend authoritative baseline

| Item | Verified value |
|---|---|
| Repository | `gycha0109-beep/journey-connect-backend` |
| Default branch | `main` |
| Work-start SHA | `251f2d14c91c6e5bebb9dcb245aa8b1d7e859976` |
| Latest merged PR | `#46` |
| Runtime | Spring Boot `3.5.16`, Java `21`, Kotlin `1.9.25` |
| Canonical DB | `journey-connect-db-v2.7`, SQL `01..26` |

The DB folder README is stale because it labels itself v2.5 and lists only `01..22`. ADM-1 must obtain the next canonical/Flyway sequence from System Coordination rather than infer it from that README.

## 3. Youngtak source intake

| Item | Verified value |
|---|---|
| Repository | `YTAK99/Journey-Connect` |
| Default branch | `master` |
| Initial source head | `youngtak@e2c2c283e7f10e32806d4fb5285081e7254b5782` |
| Final verified source head | `youngtak@44435f04df439647d282bd15ae960349d0ee5f84` |
| Admin source | `jc-frontend/src/pages/AdminPage.jsx` |
| Route | `/admin` |
| API base | `/api/v1` |

```text
ADMIN_UI_SHELL=REUSABLE
ADMIN_AUTHORIZATION=NOT_IMPLEMENTED
ADMIN_API=NOT_IMPLEMENTED
MODERATION_WORKFLOW=NOT_IMPLEMENTED
AUDIT_LOG=NOT_IMPLEMENTED
FULL_SOURCE_BRANCH_MERGE=FORBIDDEN
SELECTIVE_UI_PORT=YES
```

The branch moved during ADM-0, but the reviewed `AdminPage.jsx` and `App.jsx` blob SHAs remained unchanged. The page loads `/users/me/posts?size=100`, checks only `isLogin()`, computes search/statistics/pagination in the browser, and uses ordinary create/update/delete post APIs. It is a personal-content console with an Admin shell, not an Admin console.

## 4. Admin UI reuse assessment

| Element | Decision |
|---|---|
| Layout | `REUSE` |
| Sidebar/header | `REUSE_AND_MODIFY` |
| Summary cards | `REUSE_WITH_API` |
| Post table | `REUSE_WITH_SERVER_PAGINATION` |
| Search/mobile sidebar | `REUSE` |
| Error banner | `REUSE_AND_HARDEN` |
| Create/edit user post | `REMOVE` |
| Permanent delete | `REMOVE` |
| Login-only guard | `REPLACE` |
| Client aggregates/pagination | `REPLACE` |
| `/users/me/posts` | `REPLACE` |
| Member/statistics placeholders | `IMPLEMENT_LATER` |

`AdminPage.jsx` must be decomposed; adding all future features to the current combined page is forbidden.

## 5. Admin MVP capability matrix

| Priority | In-scope capabilities |
|---|---|
| P0 | Admin default-deny, staff principal/permission resolution, stable 401/403/error contract, append-only audit, request/operation identity |
| P1 | All-post list/detail/search/filter, post hide/restore, report list/detail/start-review/resolve/dismiss |
| P2 | User list/detail/status/history, suspend/unsuspend, dashboard aggregate, audit list/detail |
| P3 | Fine-grained permission editing, advanced analytics, bulk moderation |

Dashboard aggregate fields are `TOTAL_USERS`, `TOTAL_POSTS`, `OPEN_REPORTS`, `HIDDEN_POSTS`, `SUSPENDED_USERS`, and `RECENT_MODERATION_ACTIONS`. All values come from backend aggregate queries.

## 6. Deferred and forbidden capabilities

**Deferred:** separate `ASSIGNED` report state, timed suspension, comment UI, role editing UI, advanced analytics, bulk moderation.

**Forbidden:** Admin content creation/editing, permanent user/post deletion endpoint, password/token/secret access, private-message access, request-body audit dumps, Admin self-signup, production default Admin credentials.

## 7. Current backend domain inventory

| Domain | Finding | Decision |
|---|---|---|
| User/profile | `app_users`, `AuthAccount`, active checks | `REUSE_EXTEND` |
| Role | user/moderator/admin | `REUSE` |
| Permission | no application permission model | `CREATE_CODE_MAPPING` |
| Post lifecycle | draft/published/deleted + one-year purge | `REUSE` |
| Post moderation | visible/hidden | `REUSE` |
| Reports | table, snapshots, transitions/functions | `REUSE_EXTEND` |
| Audit | append-only `admin_actions` | `REUSE_EXTEND` |
| Java Admin API/domain | absent | `CREATE` |
| Java Admin DB routing | `DatabaseRole.ADMIN` absent | `CREATE` |
| Notification | no required Admin dependency | `DEFER` |

## 8. Security and role inventory

Current JWT is stateless and contains subject/nickname but no role. `/api/v1/admin/**` has no role rule. Existing 401/403 JSON responses are reusable. PostgreSQL already has a read-only `jc_admin` role with audited `SECURITY DEFINER` functions, but Java `DatabaseRole` exposes only APP, AUTH and RECOMMENDATION and the ordinary backend login is not currently contracted to enter `jc_admin`.

Final model:

1. Verify JWT and numeric subject.
2. Re-read current role/account status for every Admin request; stale token claims are not authoritative.
3. Map role to immutable code permissions.
4. Default-deny `/api/v1/admin/**` and enforce method permissions.
5. Enter `DatabaseRole.ADMIN` only inside authorised Admin transactions.
6. Reuse DB functions for actor checks, hierarchy, row locking and audit.

| Permission | Moderator | Admin |
|---|:---:|:---:|
| `ADMIN_READ` | yes | yes |
| `POST_MODERATE` | yes | yes |
| `REPORT_MANAGE` | yes | yes |
| `USER_MODERATE` | non-staff only | yes, DB hierarchy applies |
| `AUDIT_READ` | no | yes |
| `USER_ROLE_MANAGE` | no | deferred controlled admin-only |

Production staff bootstrap is an offline separately authorised procedure. Test staff exist only as fixtures. CSRF remains disabled only while bearer tokens stay in the Authorization header; CORS origins remain explicit.

## 9. Current schema inventory

Existing objects:

- `app_users.role`: user/moderator/admin;
- `app_users.account_status`: active/suspended/withdrawn;
- `posts.status`, `deleted_at`, `purge_after`;
- `posts.moderation_status`: visible/hidden;
- `comments.moderation_deleted_at`;
- `reports`: immutable target snapshot, reason, pending/in_review/resolved/rejected, handler snapshot;
- `admin_actions`: actor/action/target/reason/metadata, append-only;
- `jc_admin`: read-only plus controlled function execution;
- functions for report review/finalisation, user suspend/restore, post hide/restore and comment delete/restore.

Open pending/in-review report targets are excluded from the existing purge function, and the purge function is not executable by app/Admin roles.

## 10. Schema gap analysis

| Candidate/gap | Decision |
|---|---|
| `user_moderation_status` table | `DO_NOT_CREATE`; reuse account status |
| New `reports` table | `DO_NOT_CREATE`; extend existing only |
| `moderation_actions` | `DO_NOT_CREATE`; use `admin_actions` |
| `admin_audit_logs` | `DO_NOT_CREATE`; extend `admin_actions` |
| Optimistic conflict control | `CREATE` version/CAS contract |
| Request/operation identity | `CREATE` |
| Before/after audit projection | `EXTEND` with whitelist |
| Audit JSON bounds/redaction | `EXTEND` |
| Admin query indexes | `EXTEND` only after measured query design |
| Backend `jc_admin` routing | `CREATE` |
| Timed suspension | `DEFER` |

Manual Admin physical deletion is forbidden. The legacy one-year purge is a separate retention operation and is not converted into an Admin endpoint.

## 11. Proposed migration design

ADM-0 adds no migration. Candidate after SC allocation:

```text
V<SC_ASSIGNED_AFTER_26>__admin_moderation_foundation.sql
```

ADM-1 may add validated version/CAS fields, request/operation identity and minimal before/after fields to `admin_actions`, payload-size constraints, measured indexes, and backend-login membership/startup verification for `jc_admin`. Existing migrations, enums, rows, functions and audit history remain unchanged. DDL, grants, smoke tests and forward-fix instructions ship together.

`before_data` and `after_data` are object-only, allowlisted, recommended maximum 16 KiB each, and never contain email, password, token, content body or a copied request.

## 12. Admin API contract

Prefix: `/api/v1/admin`. Default page size 20, maximum 100. Filters and sorts use allowlists. Success uses `ApiResponse<T>`; lists use the repository page response; raw entities are never exposed.

```text
GET /dashboard/summary
GET /users
GET /users/{userId}
GET /posts
GET /posts/{postId}
GET /reports
GET /reports/{reportId}
GET /audit-logs
GET /audit-logs/{auditLogId}

POST /users/{userId}/suspend
POST /users/{userId}/unsuspend
POST /posts/{postId}/hide
POST /posts/{postId}/restore
POST /reports/{reportId}/start-review
POST /reports/{reportId}/resolve
POST /reports/{reportId}/dismiss
```

Explicit commands are chosen over generic status PATCH because transition, reason, idempotency and audit are first-class. `DELETE /suspension` is rejected because mandatory reason/version data in a DELETE body is operationally fragile. Post author visibility is never used as moderation status.

Each command requires `Idempotency-Key`, `reasonCode`, bounded `reasonDetail`, and `expectedVersion`. The server supplies/echoes `X-Request-Id`.

| HTTP | Contract |
|---:|---|
| 400 | invalid filter/command/reason |
| 401 | unauthenticated |
| 403 | inactive or unauthorised staff |
| 404 | unavailable target |
| 409 | transition/version/idempotency conflict |

Stable codes include `INVALID_ADMIN_COMMAND`, `ADMIN_PERMISSION_DENIED`, `ADMIN_TARGET_NOT_FOUND`, `ADMIN_STATE_CONFLICT`, `OPTIMISTIC_LOCK_CONFLICT`, and `IDEMPOTENCY_CONFLICT`. Purpose-built projections/count queries prevent N+1.

## 13. Moderation state machine

```text
POST:   visible -> hidden -> visible
REPORT: pending -> in_review -> resolved | rejected
USER:   active -> suspended -> active
```

Commands require reason and expected version; stale/duplicate transitions return 409. Existing state names remain authoritative. `OPEN`, `DISMISSED` and `ASSIGNED` are UI vocabulary only. Handler assignment and start-review are atomic. The application does not expose legacy direct closure of a pending report. `withdrawn` remains a legacy terminal user state with no MVP endpoint.

## 14. Audit contract

Canonical store: `admin_actions`.

Required logical fields: actor ID/role, action type, target type/ID, reason code/detail, before/after status, request ID, operation ID and created time.

Audit is append-only. One successful command writes exactly one audit row in the same transaction. Failed commands must not create false success rows. Actor comes from verified subject plus current DB staff record. Audit APIs are read-only and `AUDIT_READ` restricted. Full bodies, stack traces and secrets are forbidden.

## 15. Data privacy and redaction contract

Allowed audit data is limited to IDs, role/status, lifecycle/moderation status, report status/target reference, request/operation ID and bounded reason.

Forbidden: password/hash, access/refresh token, JWT dump, full email/profile/bio, full post/comment/report narrative, target-snapshot copy, raw IP, secrets, full headers/body. Optional IP correlation uses keyed HMAC plus hash version only after key-owner/retention approval.

`reports.target_snapshot` is restricted report evidence, not audit; report-detail DTOs must minimise its exposure.

## 16. Frontend decomposition plan

```text
jc-frontend/src/admin/
  components/
  layouts/
  pages/
  services/
  hooks/
  guards/
  routes/
```

Pages: dashboard, users/user-detail, posts/post-detail, reports/report-detail and audit logs. The API service owns DTO/error adaptation. Tables use server page metadata. Command modals submit reason/version/idempotency. Frontend guards are UX only.

## 17. Repository responsibility matrix

| Responsibility | Authority |
|---|---|
| Backend Admin DB/security/API | `gycha0109-beep/journey-connect-backend/main` |
| UI source | `YTAK99/Journey-Connect/youngtak@44435f04...` read-only |
| Frontend target | `YTAK99/Journey-Connect/master` provisional |
| Frontend work | `feature/admin-integration` from target |
| Final monorepo sync | ADM-5 integration branch only |

`master` is provisional because it is the repository default. ADM-3 branches from the approved target and selectively extracts UI from the exact `youngtak` SHA; it does not branch from `youngtak` and carry unrelated history into `master`.

## 18. Branch and PR workflow

Backend: `main@251f2d14...` -> `agent/adm0-admin-capability-schema-integration-baseline` -> Draft PR -> CI -> user approval -> merge.

Frontend later: approved target -> `feature/admin-integration` <- selective extraction from exact `youngtak` source -> Draft PR -> lint/build/tests -> team approval.

Ready transition, merge, auto-merge, force push and direct `youngtak` push remain forbidden without explicit approval.

## 19. Final repository synchronisation plan

Compared: directory snapshot, subtree, filtered remote merge, patch/cherry-pick and scripted rsync. The recommended method is **scripted rsync snapshot with delete semantics, path allowlist, checksums and manifest**. It is deterministic and reviewable without importing unrelated history.

ADM-5 order: merge authoritative backend; record merge SHA; create team integration branch; sync approved paths; generate manifest; integrate approved frontend PR; run full-stack tests; open Draft PR.

Manifest fields: `SOURCE_REPOSITORY`, `SOURCE_SHA`, `SYNC_TIMESTAMP`, `INCLUDED_PATHS`, `EXCLUDED_PATHS`, `VERIFICATION_RESULT`. Random copy, ZIP copy, history replacement and full `youngtak` merge are forbidden.

## 20. Dependency graph

```text
ADM-0 approval -> ADM-1 DB/security -> ADM-2 Admin API
  -> ADM-3 selective UI port -> ADM-4 integration -> ADM-5 final sync
```

SC sequence allocation gates ADM-1 migration; frontend-target confirmation gates ADM-3; exact backend merge SHA gates ADM-5.

## 21. Risk register

High risks: duplicate schema, stale JWT role, missing `jc_admin` routing, SQL numbering drift, audit privacy leakage, generic transition bypass, author-visibility/moderation confusion, legacy frontend history, client-only authorisation and concurrent moderator overwrite. Controls are reuse-first design, DB role recheck, SC allocation, explicit commands, redaction tests, target-based selective port and version/CAS conflict handling.

Medium risks: source branch drift, large Admin queries, and purge-policy wording. Controls are exact-SHA verifier, bounded projections/pages and separation of manual Admin deletion from retention purge.

## 22. Blocker register

| Blocker | Status |
|---|---|
| User ADM-0 approval | `OPEN` |
| Next SQL/Flyway sequence | `OPEN` |
| Backend `jc_admin` membership/routing | `OPEN` for ADM-1 |
| Exact frontend target | `PROVISIONAL_MASTER` |
| IP hash key/retention | `DEFERRED` |
| Timed suspension requirement | `DEFERRED` |

No blocker prevents the contract package. They intentionally block implementation entry.

## 23. Phase map

```text
ADM-0=CAPABILITY_SCHEMA_AND_INTEGRATION_BASELINE
ADM-1=ADMIN_DATABASE_AND_SECURITY_FOUNDATION
ADM-2=MODERATION_AND_ADMIN_API
ADM-3=YOUNGTAK_UI_SELECTIVE_PORT
ADM-4=FRONTEND_BACKEND_INTEGRATION
ADM-5=FINAL_TEAM_REPOSITORY_SYNCHRONISATION
```

## 24. Non-change attestation

```text
RUNTIME_SOURCE_CHANGE=NO
FRONTEND_SOURCE_CHANGE=NO
SQL_CHANGE=NO
DB_MIGRATION_CHANGE=NO
DB_CHANGE=NONE
ROLE_SEED_CHANGE=NO
ADMIN_USER_CREATION=NO
SECURITY_RULE_CHANGE=NO
YOUNGTAK_REPOSITORY_CHANGE=NO
CLOUD_INFRASTRUCTURE_CHANGE=NO
TRAFFIC_CONFIG_CHANGE=NO
HISTORICAL_RCA_SC_OP_EVIDENCE_CHANGE=NO
```

## 25. Approval state

```text
ADM1_ENTRY=BLOCKED_PENDING_USER_APPROVAL
APPROVAL_STATUS=PENDING_USER_REVIEW
```
