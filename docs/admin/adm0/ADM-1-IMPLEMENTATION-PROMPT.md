# ADM-1 Implementation Prompt

Continue Journey Connect Admin work with:

```text
ADM-1 Admin Database and Security Foundation
```

## Authoritative source

- Repository: `gycha0109-beep/journey-connect-backend`
- Base: the actual merged ADM-0 commit on `main`; never assume this document's work-start SHA is still current.
- ADM-0 source UI remains `YTAK99/Journey-Connect@youngtak` exact intake `e2c2c283e7f10e32806d4fb5285081e7254b5782` and is read-only.

## Mandatory start checks

1. Fetch actual remote `main`, ADM-0 PR/merge state, open PRs and canonical DB governance.
2. Confirm the ADM-0 merge tree and exact baseline artifacts.
3. Obtain System Coordination allocation for the next canonical/Flyway sequence after protected SQL `26`.
4. Inspect the current DB role router, backend login grants, staff role/status path and all affected tests.

## Implement only

- Admin DB role routing and startup fail-closed checks.
- DB-authoritative staff principal/permission resolution.
- `/api/v1/admin/**` default-deny security and method permission foundation.
- Forward-only migration for validated CAS/request/audit gaps.
- Existing table/function entity/repository adapters.
- Test-only staff fixtures and security/database tests.

## Preserve

- Existing `app_users.role/account_status`.
- Existing `reports`, `admin_actions`, moderation columns and security-definer functions.
- Existing 401/403 response envelope.
- Existing P0/P1/P2 and Data/Intelligence/Operations governance evidence.

## Do not implement

- ADM-2 Admin query/command controllers.
- React changes.
- Permanent delete.
- User-content create/edit.
- Production staff seed.
- Full source branch merge.
- Cloud or traffic changes.

## Delivery

Use branch `agent/adm1-admin-database-security-foundation`, open a Draft PR, run exact-head PostgreSQL/security/regression verification, and report `완료 / 문제 / 다음 작업 / 최종 판정`. Do not mark Ready, merge or auto-merge without explicit user approval.
