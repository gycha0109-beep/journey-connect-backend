# Journey Connect DB v2.8

SR-6C authoritative Search exposure persistence, SR-6F-C aggregate-only Search CTR boundary, SR-6F-D append-only projection writer, SR-6F-F non-production manual activation foundation, and SR-6F-H Reliability-role convergence package.

## Prerequisite

Apply the frozen `journey-connect-db-v2.7` canonical baseline `01..54` first.

## Order

```text
01_search_exposure_persistence.sql
02_search_exposure_digest_privilege.sql
03_search_exposure_persistence_smoke_test.sql
04_search_ctr_aggregate_boundary.sql
05_search_ctr_aggregate_boundary_smoke_test.sql
06_search_ctr_projection_writer.sql
06a_search_ctr_writer_owner_dependency.sql
07_search_ctr_projection_writer_smoke_test.sql
08_search_ctr_nonprod_manual_activation_foundation.sql
09_search_ctr_nonprod_manual_activation_smoke_test.sql
10_search_ctr_reliability_role_noinherit_convergence.sql
11_search_ctr_reliability_role_noinherit_smoke_test.sql
```

`04` creates the identity-safe, aggregate-only `evaluate_search_ctr_v1` boundary and the isolated `jc_reliability` role. That role receives no direct access to identity mappings, raw Search exposure, Search behavior, or access-audit rows.

`05` verifies deterministic 30-minute attribution, zero-denominator `null`, requester restrictions, identity-free result columns, and fail-closed handling after identity invalidation.

`06` creates the append-only `search_ctr_projection_snapshot_v1` authority and the only projection persistence function, `write_search_ctr_projection_v1`. The writer computes aggregate values, canonical bytes, fingerprint, deterministic projection ID, duplicate handling, idempotency conflict, and predecessor lineage inside one advisory-locked `SECURITY DEFINER` transaction.

`06a` grants only `EXECUTE` on `evaluate_search_ctr_v1` to the NOLOGIN physical owner `jc_security_owner`, allowing the `SECURITY DEFINER` writer to invoke the aggregate boundary without granting raw table access or widening application runtime roles.

`07` verifies root storage, semantic duplicate suppression, idempotency conflict, predecessor conflict, replacement lineage, canonical payload privacy, direct table privilege denial, and append-only mutation rejection.

`08` creates the identity-free `read_search_ctr_projection_head_v1` boundary, append-only `search_ctr_manual_run_audit_v1`, and atomic `execute_search_ctr_manual_v1` orchestration function. The orchestration function is restricted to allowlisted non-production environments, one UTC-aligned hour, provisional eligibility, `PROVISIONAL` writes, and `jc_reliability` execute-only access. It never authorizes finality writes.

`09` verifies the manual execution boundary, zero-denominator one-shot write, current-head read, append-only audit, direct table denial, identity-free results, and production-environment rejection. All fixtures are rolled back.

`10` converges both newly created and previously applied v2.8 databases to the approved `jc_reliability NOLOGIN NOINHERIT` role contract. It refuses convergence if the role has elevated attributes or any inbound/outbound membership, then changes only the `INHERIT` attribute.

`11` verifies the final isolated role attributes, absence of memberships, and preservation of the approved manual execution-function capability. Test fixtures are rolled back.

The backend Testcontainers bootstrap mirrors these reviewed bytes under the historical global execution labels `55`, `55a`, `56`, `57`, `58`, `59`, `59a`, `60`, `61`, `62`, `63`, and `64` so existing bootstrap ordering remains deterministic without extending the frozen v2.7 inventory.

The application login is not required to hold `jc_reliability` by default. SR-6F-F adds an explicit `app.database.role-routing.require-reliability` startup flag, but both that flag and the one-shot runner remain disabled until a separately approved non-production activation decision.

This package is canonical-SQL only. It must not be copied into Flyway auto-discovery without a separately approved baseline/history migration.
