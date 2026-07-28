# ADM-1 Admin Database and Security Foundation

```text
STAGE=ADM-1
REMOTE_MAIN=a2b9e3d8e79df3dcf9d75b418011b3a8cca754b1
PR47_MERGE=a2b9e3d8e79df3dcf9d75b418011b3a8cca754b1
ADMIN_API_PREFIX=/api/admin
ADMIN_AUTHORIZATION_SOURCE=DB_AUTHORITATIVE
SQL_CHANGE=NONE
DB_SCHEMA_CHANGE=NONE
FRONTEND_SOURCE_CHANGE=NO
YOUNGTAK_SOURCE_CHANGE=NO
ADMIN_UI_PORT_EXECUTED=NO
ADM2_ENTRY=BLOCKED_PENDING_USER_APPROVAL
```

ADM-1 connects the already-reviewed `jc_admin` PostgreSQL role and `app_users` authority columns to the Spring Security and service transaction boundaries. It does not add Admin business APIs or UI.

Documents:

- `ADM-1-ADMIN-DATABASE-SECURITY-FOUNDATION.md`
- `ADM-2-ENTRY-GATE-AND-HANDOFF.md`
- machine-readable contract: `verification/admin/adm1/adm1-contract.json`
- independent verifier: `verification/admin/adm1/verify_adm1.py`
