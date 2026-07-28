# ADM-4 Admin Dashboard UI Selective Port and Finalisation

## Purpose

Complete a low-complexity administrator dashboard in the authoritative Journey Connect repository by connecting Dashboard, Reports, Posts and Users to the existing ADM-2/ADM-3 backend contract. ADM-4 adds no administrator capability, endpoint, schema object or deployment resource.

## Authoritative baseline

- Target repository: `gycha0109-beep/journey-connect-backend`
- Target base branch: `main`
- Target base SHA: `dc4800814f690d2e6b0f8cf7abba56c6a3a32d30`
- ADM-3 exact head: `15c9cef39786e0cb418be384c32d000ecfec3130`
- Work branch: `agent/adm4-admin-dashboard-ui-finalisation`

## Reference source freeze

- Reference repository: `YTAK99/Journey-Connect`
- Reference branch: `youngtak`
- Reference SHA: `47f8cceeaaa4f9afdd90896bc0793a34e9cefefb`
- Ongoing source sync: `NO`
- Full source branch merge: `NO`

The reference repository is not an integration target. Only layout concepts from its initial Admin page were selectively evaluated. The runtime implementation, API client, route guard, moderation commands, error handling, tests and verification were rewritten against the canonical backend contract and committed under `jc-frontend/` in this repository.

Reused design concepts: responsive sidebar, sticky header, summary-card arrangement, table spacing, loading/empty presentation and modal visual shell.

Rejected source elements: current-user/mock data, client-only pagination, fake administrator identity, create/edit/delete controls, physical deletion, `window.confirm`, unsupported menus, bundled template/media tree, source application shell, source authentication assumptions and whole-branch merge.

## Runtime structure

ADM-4 introduces a standalone Admin frontend inside the authoritative repository:

```text
jc-frontend/
  index.html
  src/main.jsx
  src/App.jsx
  src/pages/AdminLoginPage.jsx
  src/admin/**
  src/pages/admin/**
  src/services/auth.js
  src/services/apiClient.js
  src/services/adminApi.js
  verification/adm4/**
```

## Routes

```text
/login
/admin
/admin/reports
/admin/reports/:reportId
/admin/posts
/admin/posts/:postId
/admin/users
/admin/users/:userId
```

Unknown Admin routes render an Admin-specific not-found state. The root redirects to `/admin`.

## Authentication and authorization

`POST /api/v1/auth/login` supplies the existing access/refresh tokens. The login response does not expose a role, so the UI neither decodes nor fabricates one. Before rendering the Admin layout, `GET /api/admin/dashboard` acts as the authorization probe. A 401 clears the local session and redirects to `/login`; a 403 renders a generic access-denied screen. Backend DB-authoritative authorization remains the security boundary.

## Canonical Admin API mapping

All 13 endpoints are represented in `src/services/adminApi.js`.

- Dashboard: 1 read
- Reports: list, detail, resolve, dismiss
- Posts: list, detail, hide, restore
- Users: list, detail, suspend, unsuspend

Every mutation requires a non-blank reason of at most 1000 characters. Duplicate submission is blocked. `changed=false` is treated as a successful idempotent outcome without falsely claiming a new state transition.

## Safety constraints

```text
PHYSICAL_DELETE_IMPLEMENTED=NO
ROLE_MANAGEMENT_IMPLEMENTED=NO
ADMIN_APPOINTMENT_IMPLEMENTED=NO
RAW_INTERNAL_ERROR_RENDERING=NO
MOCK_ADMIN_DATA=NO
BACKEND_RUNTIME_CHANGE=NO
BACKEND_SQL_CHANGE=NO
DB_SCHEMA_CHANGE=NONE
```

## Accessibility and responsive baseline

- semantic navigation and table headers
- explicit labels and accessible names
- dialog role, modal semantics, initial focus, Tab loop and Escape behavior
- error/help association for reason input
- non-colour status labels
- responsive sidebar overlay and mobile controls
- horizontally scrollable tables and responsive card/detail grids

## Validation

- Node built-in Admin contract tests
- ESLint
- Vite production build
- ADM-4 static verifier and machine-readable evidence
- exact in-repository backend endpoint/DTO verifier
- inherited Admin PostgreSQL integration tests

## Delivery gate

ADM-4 remains Draft until the exact remote PR head passes both `frontend` and `backend-integration-smoke`. Ready conversion, merge and auto-merge remain forbidden without explicit user approval.
