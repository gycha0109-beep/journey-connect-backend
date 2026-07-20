# Journey Connect DB v2.0 — P0-2 backend runtime

This package extends the reviewed v1.9 database without modifying files 01-09.

Apply on a fresh database in numeric order:

1. `01_initial_schema.sql`
2. `02_seed.sql`
3. `03_smoke_test.sql`
4. `04_admin_support.sql`
5. `05_security_roles.sql`
6. `06_security_smoke_test.sql`
7. `07_recommendation_storage.sql`
8. `08_recommendation_security_roles.sql`
9. `09_recommendation_smoke_test.sql`
10. `10_backend_runtime.sql`
11. `11_backend_runtime_security_roles.sql`
12. `12_backend_runtime_smoke_test.sql`

On a database already at v1.9, apply only 10-12 in order.
Smoke scripts 03, 06, 09 and 12 end with `ROLLBACK` and leave no test data.

## Verification

Static source-integrity gate:

```bash
./jc-backend/gradlew -p jc-backend p0Verification --stacktrace
```

The backend integration suite starts PostgreSQL 15 through Testcontainers, applies
canonical SQL 01-12 with `ON_ERROR_STOP=1`, and runs Hibernate schema validation.
PostgreSQL 15 and 18 execute SQL 01-12 in `.github/workflows/recommendation-p0-db-ci.yml`.

## Runtime role boundary

The canonical roles remain separate (`jc_app`, `jc_auth`, `jc_recommendation`,
`jc_admin`). The current single Spring datasource is tested with the schema owner.
Production least privilege requires role-routed or separate datasource transactions in P0-3.
