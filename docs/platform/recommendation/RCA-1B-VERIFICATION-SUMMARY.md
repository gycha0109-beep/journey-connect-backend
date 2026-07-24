# RCA-1B Verification Summary

## Scope
Summarizes required exact-head verification without pre-claiming unexecuted results.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; SC-4 entry is merged and authoritative.

## Implementation
Independent static/runtime verifier, PG15/18 matrix, cross-version comparator and protected regression job.

## Authority
Machine verification does not replace Intelligence, Reliability, Operations, Privacy/Security or SC review; all review packages remain `PENDING_USER_REVIEW`.

## Dependencies
Exact PR head, complete artifacts and Git history.

## Execution Environment
Two independent ephemeral PostgreSQL jobs.

## DB Access Boundary
Catalog, server-state and negative permission evidence required.

## Query Boundary
Exactly seven query IDs/fingerprints required.

## Dataset
Seed digest/idempotency/duplicate assertions required.

## Identity/Privacy
Synthetic-only and redaction checks required.

## P1 Result
Completion candidate: `RECONCILED_WITH_EXPECTED_GAPS` with zero baseline query mismatch.

## P2 Result
Completion candidate: `RECONCILED_WITH_MIGRATION_GAPS` with protected authority markers.

## Checkpoint/Lineage
Zero-lag/equal snapshot/equal lineage required.

## Evidence
Per-version, cross-version and protected-regression artifacts retained 90 days.

## Verification
PASS is recorded only after actual execution. Runtime dark read is `NOT_APPLICABLE`; production DB, traffic, canary, load, replay, actual identity, credentials and activation are `NOT_EXECUTED`.

## Compatibility
Both database versions and all protected regressions must succeed on the same exact head.

## Risks
A changed head invalidates every previous final result.

## Exit Criteria
All required jobs and verifier assertions pass with empty failure lists.

## Handoff
Final run IDs, counters and exact head are added to the Draft PR body after CI.
