# RCA-1B Execution Environment Report

## Scope
Defines and records the isolated database execution boundary for RCA-1B only.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; SC-4 approved `CI_EPHEMERAL_POSTGRESQL`.

## Implementation
Each matrix job starts one Testcontainers PostgreSQL instance, applies canonical SQL and test-only bootstrap/seed, executes reconciliation, emits evidence, and destroys the container.

## Authority
Operations owns environment and credential boundaries; SC owns phase scope. Neither approval is inferred from test success.

## Dependencies
Docker-enabled GitHub runner, Testcontainers, PostgreSQL images `15-alpine` and `18-alpine`.

## Execution Environment
UTC timezone, `C` locale/ctype, no PostGIS dependency, no RCA-1B extension dependency, tmpfs database storage, no persistent volume, no production endpoint or secret.

## DB Access Boundary
Owner connection is bootstrap-only. All reconciliation queries use the separate ephemeral `rca1b_readonly` login.

## Query Boundary
Network access is limited to the started Testcontainers endpoint; seven registered queries only.

## Dataset
Identical synthetic seed resource and digest are used for PostgreSQL 15 and 18.

## Identity/Privacy
No production-derived snapshot or actual identity is loaded.

## P1 Result
P1 execution is isolated to the current container snapshot.

## P2 Result
P2 execution is isolated to the same container snapshot without reading canonical dataset rows or release evidence.

## Checkpoint/Lineage
Explicit fixture timestamps and lineage fingerprints replace system-clock freshness.

## Evidence
Environment type, image tag, database version, transaction state, teardown status, and tested SHA are recorded; host, port and container ID are excluded.

## Verification
CI verifies one independent container per matrix job and `container_stopped=true` with `persistent_state_retained=false`.

## Compatibility
No claim is made for production network, storage, credentials or query plans.

## Risks
Hosted runner and image availability remain external CI dependencies.

## Exit Criteria
Both isolated matrix environments must complete without shared state or persistent storage.

## Handoff
RCA-2 must separately define runtime environment, credentials, traffic and rollback.
