# RCA-1B P1 Database Reconciliation Result

## Scope
Records P1 database comparison behavior without replacing RCA-1 or current P1 authority.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; `P1_SOURCE=RecommendationP1ProfileSource`; `P1_RESULT=recommendation_p1_profile_snapshot`.

## Implementation
Authoritative snapshot and `recommendation-profile-input-v1` candidate are queried in one read-only repeatable-read snapshot and normalized deterministically.

## Authority
Intelligence remains accountable for P1 semantic acceptance. Data candidate rows remain non-authoritative.

## Dependencies
RCA-1 taxonomy, P1 query pair, explicit checkpoint/snapshot/lineage and deterministic seed.

## Execution Environment
PostgreSQL 15 and 18 ephemeral matrix only.

## DB Access Boundary
Explicit SELECT grants; no owner query or mutation.

## Query Boundary
P1 authoritative and candidate query IDs plus shared checkpoint/lineage queries.

## Dataset
Synthetic baseline produces three ordered 7/30/90 window rows and explicit negative inventory.

## Identity/Privacy
Subject/user fields are synthetic and redacted before evidence.

## P1 Result
Expected completion result: `RECONCILED_WITH_EXPECTED_GAPS`. Exact field, deterministic derived values, window, null, numeric, timezone, order, row count, checkpoint, snapshot and lineage use zero tolerance.

## P2 Result
Not evaluated by this document; P2 has an independent result.

## Checkpoint/Lineage
Exact parity requires equal checkpoint, zero lag, equal snapshot time and equal lineage fingerprint.

## Evidence
P1 dimensions produce independent records and mismatch inventory; no aggregate-to-event reconstruction.

## Verification
Database result lists must match exactly and PostgreSQL 15/18 canonical evidence must agree.

## Compatibility
Ordering/event-grain/explicit preference/transform/fingerprint semantics remain expected or protected gaps.

## Risks
Database aggregate parity is not event-stream equivalence or production readiness.

## Exit Criteria
No unexpected query mismatch, duplicate blocker, checkpoint/lineage blocker or identity blocker in baseline.

## Handoff
Intelligence review package remains `PENDING_USER_REVIEW`.
