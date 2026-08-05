# Journey Connect DB v2.8

SR-6C authoritative Search exposure persistence package.

## Prerequisite

Apply the frozen `journey-connect-db-v2.7` canonical baseline `01..54` first.

## Order

```text
01_search_exposure_persistence.sql
02_search_exposure_digest_privilege.sql
03_search_exposure_persistence_smoke_test.sql
```

The backend Testcontainers bootstrap mirrors these reviewed bytes under the historical global execution labels `55`, `55a`, and `56` so existing test bootstrap ordering remains deterministic without extending the frozen v2.7 inventory.

This package is canonical-SQL only. It must not be copied into Flyway auto-discovery without a separately approved baseline/history migration.
