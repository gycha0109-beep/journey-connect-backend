# ADM-5 Local Runbook

## Scope

This runbook starts the already implemented Admin frontend and backend together for local or isolated non-production acceptance. It creates no new endpoint, schema, administrator capability, deployment platform or production data.

```text
NEW_ADMIN_FEATURE=NO
NEW_ADMIN_ENDPOINT=NO
BACKEND_RUNTIME_CHANGE=NO
BACKEND_SQL_CHANGE=NO
DB_SCHEMA_CHANGE=NONE
PRODUCTION_DEMO_DATA_MIGRATION=NO
```

## Required versions

| Component | Required / verified baseline |
|---|---|
| PostgreSQL | 15+ |
| Java | 21 |
| Node.js | 22 |
| npm | package manager used by `jc-frontend` |
| Backend | `jc-backend`, Gradle wrapper |
| Frontend | `jc-frontend`, Vite |

## 1. PostgreSQL

Start a disposable PostgreSQL 15 database. The canonical database name is `journey_connect`; the usual local endpoint is `localhost:5432`.

The reviewed canonical SQL remains manually applied because `FLYWAY_ENABLED` defaults to `false`. Apply, in order, the SQL files listed by `jc-backend/src/test/java/com/jc/backend/CanonicalPostgresInitializer.java`: `01` through `28`, then `53` and `54`, from `jc-backend/src/test/resources/db/canonical/`.

Example pattern:

```bash
export PGHOST=127.0.0.1 PGPORT=5432 PGDATABASE=journey_connect PGUSER=postgres
export PGPASSWORD='<local-postgres-password>'
for script in 01_initial_schema.sql 02_seed.sql 03_smoke_test.sql 04_admin_support.sql \
  05_security_roles.sql 06_security_smoke_test.sql 07_recommendation_storage.sql \
  08_recommendation_security_roles.sql 09_recommendation_smoke_test.sql \
  10_backend_runtime.sql 11_backend_runtime_security_roles.sql 12_backend_runtime_smoke_test.sql \
  13_backend_role_routing.sql 14_backend_role_routing_smoke_test.sql \
  15_backend_role_runtime_fix.sql 16_backend_role_runtime_fix_smoke_test.sql \
  17_recommendation_run_exploration_partition_fix.sql \
  18_recommendation_run_exploration_partition_fix_smoke_test.sql \
  19_recommendation_replay_audit.sql 20_recommendation_replay_audit_smoke_test.sql \
  21_recommendation_behavior_runtime.sql 22_recommendation_behavior_runtime_smoke_test.sql \
  23_recommendation_p1_profile_policy.sql 24_recommendation_p1_profile_policy_smoke_test.sql \
  25_recommendation_p2_evaluation_release.sql 26_recommendation_p2_evaluation_release_smoke_test.sql \
  27_search_document_projection.sql 28_search_document_projection_smoke_test.sql \
  53_admin_control_plane_hardening.sql 54_admin_control_plane_hardening_smoke_test.sql; do
  psql -v ON_ERROR_STOP=1 -f "jc-backend/src/test/resources/db/canonical/$script"
done
```

Do not apply demo data as a production migration.

## 2. Restricted backend database login

The backend login must be a restricted `NOINHERIT`, `NOSUPERUSER`, `NOBYPASSRLS` login and a member of exactly the runtime roles required by the application: `jc_app`, `jc_auth`, `jc_admin`, `jc_recommendation`. It must not own database objects or receive direct table grants.

Illustrative local-only provisioning, executed by the PostgreSQL owner:

```sql
CREATE ROLE jc_backend_local LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE
  NOREPLICATION NOBYPASSRLS PASSWORD '<local-only-password>';
GRANT jc_app, jc_auth, jc_admin, jc_recommendation TO jc_backend_local;
```

Verify:

```sql
SELECT rolname, rolinherit, rolsuper, rolbypassrls FROM pg_roles WHERE rolname='jc_backend_local';
SELECT pg_has_role('jc_backend_local', role_name, 'MEMBER')
FROM (VALUES ('jc_app'),('jc_auth'),('jc_admin'),('jc_recommendation')) roles(role_name);
```

## 3. Backend environment and start

Copy `jc-backend/src/main/resources/application.yml.sample` to an ignored local `application.yml`. Required names are the names actually consumed by that file:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
FLYWAY_ENABLED
DB_ROLE_ROUTING_VERIFY
DB_ROLE_ROUTING_REQUIRE_RESTRICTED_LOGIN
JWT_SECRET
JWT_ACCESS_TOKEN_MINUTES
JWT_REFRESH_TOKEN_DAYS
CORS_ALLOWED_ORIGINS
SERVER_PORT
```

Example local shell, with values supplied outside version control:

```bash
cd jc-backend
export DB_HOST=127.0.0.1 DB_PORT=5432 DB_NAME=journey_connect
export DB_USERNAME=jc_backend_local DB_PASSWORD='<local-only-password>'
export FLYWAY_ENABLED=false
export DB_ROLE_ROUTING_VERIFY=true
export DB_ROLE_ROUTING_REQUIRE_RESTRICTED_LOGIN=true
export JWT_SECRET='<at-least-32-byte-local-secret>'
export CORS_ALLOWED_ORIGINS='http://localhost:5173,http://127.0.0.1:5173'
export SERVER_PORT=8080
./gradlew bootRun
```

A default Spring profile is sufficient. The backend URL is `http://localhost:8080`.

## 4. Administrator account provisioning

1. Create a normal account through `POST /api/v1/auth/signup` or the ordinary signup flow.
2. In the isolated local database, a database owner changes `public.app_users.role` to `admin` and confirms `account_status='active'`.
3. Log in again after the role change. The old access token still contains the old role and must not be reused.
4. Confirm the new token contains the `admin` role without storing or printing the token.
5. Confirm `GET /api/admin/dashboard` succeeds through the restricted backend login that can `SET ROLE jc_admin`.

```text
ADMIN_USER_EXISTS=YES
ADMIN_ROLE_IN_APP_USERS=admin
ADMIN_ACCOUNT_STATUS=active
BACKEND_DB_LOGIN_HAS_JC_ADMIN_MEMBERSHIP=YES
ROLE_CHANGE_REQUIRES_NEW_TOKEN=YES
```

Never commit a password, token, JWT claim dump or production administrator identity.

## 5. Frontend environment and start

`jc-frontend/.env.example` contains the actual Vite variables:

```text
VITE_API_BASE_URL
VITE_ADMIN_API_BASE_URL
VITE_DEV_BACKEND_URL
```

For Vite development with its proxy:

```bash
cd jc-frontend
cp .env.example .env.local
# Set VITE_DEV_BACKEND_URL=http://localhost:8080 in .env.local
npm install --no-audit --no-fund
npm run dev -- --host 127.0.0.1 --port 5173
```

Open `http://127.0.0.1:5173/login`.

For a production build served separately from the backend, build with absolute backend bases and configure backend CORS for the preview origin:

```bash
VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1 \
VITE_ADMIN_API_BASE_URL=http://127.0.0.1:8080/api/admin \
npm run build
npm run preview -- --host 127.0.0.1 --port 4173
```

The production preview URL is `http://127.0.0.1:4173`. A real static host must route unknown frontend paths to `index.html` for SPA refresh support.

## 6. Stop and clean up

- Stop Vite and Spring Boot with `Ctrl+C` or their recorded process IDs.
- Stop the disposable PostgreSQL container/service.
- Remove ignored local `application.yml`, `.env.local`, logs and test-only data if no longer required.
- Never reset or truncate an operating production database.
