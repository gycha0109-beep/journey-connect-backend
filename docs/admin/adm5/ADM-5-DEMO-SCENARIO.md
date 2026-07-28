# ADM-5 Demo Scenario

## Pre-demo preparation

- Use an isolated local/non-production PostgreSQL database with canonical migrations.
- Prepare one active administrator, one normal user, one suspendable user, a visible post and at least three pending reports.
- Confirm the backend runs on `8080`, the Admin frontend on `5173` or production preview `4173`, and CORS permits that exact origin.
- Use fresh administrator credentials and obtain a new token after any role change.
- Never use production traffic or production data.

## Demonstration sequence

1. Log in with a normal user and navigate directly to `/admin`, `/admin/reports`, `/admin/posts`, and `/admin/users`.
2. Show the generic access-denied screen. Use **다른 계정으로 로그인** to clear the session.
3. Log in through `/login` with the active administrator.
4. Open `/admin` and explain the four summary cards and recent report/action panels.
5. Open Reports, filter/search, open a pending report, enter a reason, and resolve it.
6. Show that the report detail/list and Dashboard pending count refresh. Repeating the same terminal command returns a safe no-change result; attempting the opposite terminal command returns a safe conflict.
7. Open Posts, select a visible post, hide it with a reason, repeat hide to demonstrate `changed=false`, then restore it.
8. Open Users, select the prepared normal account, suspend it, confirm detail/list/Dashboard state, repeat suspend for `changed=false`, then unsuspend it.
9. Demonstrate one safe validation error with a blank reason and one not-found/conflict message. Do not attempt to expose internal exceptions.
10. Resize or use browser device emulation at `1280`, `768`, and `390` widths to show sidebar, table overflow, card wrapping, details and dialog.
11. Refresh `/admin`, `/admin/reports`, `/admin/posts`, and `/admin/users` directly.
12. Log out and show that direct `/admin` navigation returns to `/login`.

## Demo recovery

- Hidden post: execute **restore**.
- Suspended user: execute **unsuspend**.
- Resolved/rejected report: terminal; do not reuse it. Prepare another local pending fixture.
- To repeat the full demo, recreate the disposable database from canonical SQL and rerun the local fixture procedure. Do not implement or execute a production reset.
