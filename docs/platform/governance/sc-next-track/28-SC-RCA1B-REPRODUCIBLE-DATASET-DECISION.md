# SC RCA-1B Reproducible Dataset Decision

## Scope

Select the database dataset used by RCA-1B and define reproducibility and teardown. No seed SQL is written by SC-4.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; RCA-1 Model A uses 23 P1 and 39 P2 deterministic synthetic cases.

## Decision

- Dataset A — existing canonical migration plus deterministic fixture seed: `APPROVED`.
- Dataset B — sanitized snapshot: `DEFERRED`.
- Dataset C — production dump or production-derived raw extract: `BLOCKED`.

```text
RCA1B_DATASET_MODE=DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE
SCHEMA_SOURCE=CANONICAL_SQL_01_52
SEED_MODE=VERSION_CONTROLLED_TEST_ONLY
SEED_IDEMPOTENCY=REQUIRED
TEARDOWN=CONTAINER_DESTRUCTION
RAW_SNAPSHOT_RETENTION=NONE
```

## Rationale

Synthetic database rows provide real SQL/join/null/order behavior without privacy and drift risks. Model A cases become the semantic oracle while database rows exercise query behavior.

## Authority

Data confirms candidate/checkpoint/lineage fields; Intelligence owns P1 reference semantics; Reliability owns P2 reference semantics; Operations owns teardown; Privacy/Security blocks production-derived data.

## Dependencies

Canonical schema replay, stable synthetic IDs, explicit timestamps and deterministic row insertion order.

## Execution Environment

Seed is applied by bootstrap owner before the read-only login is used. Each version receives equivalent rows and captured timestamps.

## DB Access Boundary

The reconciliation login cannot seed, alter or delete data.

## Query Boundary

Dataset must include P1 source/candidate rows, P2 authoritative exposure/outcome and candidate rows, checkpoint/lineage metadata, valid/negative/stale/mismatch/migration-required cases. Canonical dataset rows and release evidence are excluded.

## Identity/Privacy

Synthetic identities have no reversible link to actual users.

## Evidence

Record seed contract version and digest, not row payloads.

## DB/SQL Impact

Test seed may use noncanonical test-only SQL/resources in the future implementation. It is not a migration and does not allocate SQL `53+`.

## Production Impact

None.

## Verification

SC-4 verifies the dataset decision. Schema replay, seed determinism and teardown are `NOT_EXECUTED`.

## Risks

Synthetic distributions cannot prove production-scale behavior. Seed drift must be prevented by a committed digest and duplicate-case checks.

## Exit Criteria

Both PostgreSQL versions use the same seed contract and produce identical normalized results after complete teardown.

## Handoff

Any request for sanitized/shared/production-derived data requires a separate Privacy/Security and SC decision.