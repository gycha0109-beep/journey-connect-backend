# Journey Connect DB v2.8

SR-6C authoritative Search exposure persistence and SR-6F-C aggregate-only Search CTR boundary package.

## Prerequisite

Apply the frozen `journey-connect-db-v2.7` canonical baseline `01..54` first.

## Order

```text
01_search_exposure_persistence.sql
02_search_exposure_digest_privilege.sql
03_search_exposure_persistence_smoke_test.sql
04_search_ctr_aggregate_boundary.sql
05_search_ctr_aggregate_boundary_smoke_test.sql
```

`04` creates the identity-safe, aggregate-only `evaluate_search_ctr_v1` boundary and the isolated `jc_reliability` role. That role receives no direct access to identity mappings, raw Search exposure, Search behavior, or access-audit rows.

`05` verifies deterministic 30-minute attribution, zero-denominator `null`, requester restrictions, identity-free result columns, and fail-closed handling after identity invalidation.

The backend Testcontainers bootstrap mirrors these reviewed bytes under the historical global execution labels `55`, `55a`, `56`, `57`, and `58` so existing bootstrap ordering remains deterministic without extending the frozen v2.7 inventory.

This package is canonical-SQL only. It must not be copied into Flyway auto-discovery without a separately approved baseline/history migration.
