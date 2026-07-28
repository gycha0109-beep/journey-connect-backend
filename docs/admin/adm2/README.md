# ADM-2 Admin Moderation and Basic Management APIs

ADM-2 implements the backend API surface required by the simple Journey Connect Admin dashboard MVP.

Included:

- `GET /api/admin/dashboard`
- Reports list, detail, resolve and dismiss
- Posts list, detail, hide and restore
- Users list, detail, suspend and unsuspend
- DB-authoritative Admin authorization inherited from ADM-1
- PostgreSQL `jc_admin` role routing
- audited `SECURITY DEFINER` command execution
- PostgreSQL 15 integration tests and exact-head verification

Excluded:

- frontend work or Youngtak source changes
- role management or Admin appointment
- physical deletion
- advanced workflow, bulk operations or infrastructure controls

See `ADM-2-ADMIN-MODERATION-BASIC-MANAGEMENT-APIS.md` for the authoritative contract and `ADM-3-ENTRY-GATE-AND-HANDOFF.md` for the successor gate.
