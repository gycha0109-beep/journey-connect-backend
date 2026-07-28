# ADM-5 Troubleshooting

| Symptom | Check | Resolution |
|---|---|---|
| Backend connection failure | `SERVER_PORT`, process log, DB connectivity | Confirm backend is on `8080`, database is ready, and `application.yml.sample` was copied only to an ignored local config. |
| Frontend API connection failure | `VITE_API_BASE_URL`, `VITE_ADMIN_API_BASE_URL`, `VITE_DEV_BACKEND_URL` | Dev uses the Vite `/api` proxy only when `VITE_DEV_BACKEND_URL` is set. A production preview needs absolute API bases or same-origin reverse proxying. |
| CORS error | Exact browser origin and `CORS_ALLOWED_ORIGINS` | Add the exact scheme/host/port; do not use an unreviewed wildcard for credentialed requests. Restart backend after the change. |
| 401 | Missing/expired token, stale local storage | Log in again. The client clears `accessToken`, `refreshToken`, and `loginUser` after a 401. |
| 403 | Token role, DB role/status, subject or DB authorization mismatch | Confirm token role is admin, `app_users.role='admin'`, `account_status='active'`, subject is the same DB user, and log in again after role changes. |
| Database login failure | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Test the restricted login with `psql`; do not switch to a superuser as an application workaround. |
| `jc_admin` membership failure | `pg_has_role` and startup verifier | Grant membership to the restricted backend login. Keep `NOINHERIT`, no direct table grants and no ownership. |
| Migration failure | Canonical order and first failing SQL file | Apply `01`–`28`, then `53`, `54`, with `ON_ERROR_STOP=1`. Do not skip a smoke script or modify historical SQL. |
| Port conflict | `8080`, `5173`, `4173`, `5432` | Stop the other process or set the supported port variables/CLI options consistently. |
| SPA refresh 404 | Static-host fallback | Configure the selected host to serve `index.html` for unknown non-API routes. Vite dev/preview already provides fallback for acceptance. |
| Admin token stale after role change | JWT `role` reflects issuance time | Clear the session and log in again. `ROLE_CHANGE_REQUIRES_NEW_TOKEN=YES`. |
| Report cannot be reused | Report is terminal after resolve/reject | Use another pending local fixture or rebuild the disposable local database. |
| 409 state conflict | Concurrent or terminal state changed | Reload list/detail/Dashboard and retry only when the new state permits it. Do not bypass the backend state machine. |
