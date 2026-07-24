# SC RCA-1B Checkpoint, Freshness and Lineage Decision

## Scope

Define when source and candidate database results are comparable. No checkpoint implementation is changed.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; RCA-1 already classifies checkpoint, stale-source and lineage failures.

## Decision

```text
CHECKPOINT_REQUIRED=YES
CHECKPOINT_FORMAT=OPAQUE_REF_PLUS_MONOTONIC_SEQUENCE_PLUS_CAPTURED_AT
CHECKPOINT_ORDERING=MONOTONIC_SEQUENCE_THEN_CAPTURED_AT
CHECKPOINT_EQUALITY_REQUIRED=YES_FOR_EXACT_PARITY
MAX_ALLOWED_CHECKPOINT_LAG=0
STALE_SOURCE_THRESHOLD=0_FOR_DETERMINISTIC_FIXTURE
SNAPSHOT_CAPTURE_TIME=EXPLICIT_FIXTURE_TIMESTAMP
LINEAGE_FINGERPRINT_REQUIRED=YES
SYSTEM_CLOCK_DEPENDENCY=FORBIDDEN
```

A missing checkpoint, inverted sequence, unequal snapshot or lineage mismatch fails closed. Different snapshot times prohibit exact parity and produce `RECONCILIATION_INCONCLUSIVE` unless a future phase explicitly approves bounded lag.

## Rationale

The synthetic dataset can enforce zero-lag comparison; inventing a lag threshold without empirical evidence would weaken the result.

## Authority

Data owns candidate checkpoint/lineage interpretation; lane owners approve source alignment; SC controls comparison eligibility.

## Dependencies

Captured UTC timestamp, stable sequence, versioned lineage fingerprint and same seed contract.

## Execution Environment

All values come from fixture/seed metadata, never `now()` or runner local time.

## DB Access Boundary

Checkpoint and lineage queries are allowlisted, bounded single-row reads.

## Query Boundary

Query results must carry source and candidate checkpoint/lineage independently. No implicit latest/current selection without deterministic ordering and limit.

## Identity/Privacy

Checkpoint and lineage evidence must not embed raw identity or row payloads.

## Evidence

Record redacted checkpoint references, sequences, captured timestamp and lineage fingerprint/digest.

## DB/SQL Impact

No new checkpoint table, view or migration.

## Production Impact

None.

## Verification

SC-4 validates the zero-lag policy. Database checkpoint/freshness execution is `NOT_EXECUTED`.

## Risks

Zero lag is suitable for deterministic CI but does not establish live-source freshness tolerance. That remains a later non-ephemeral or runtime decision.

## Exit Criteria

Every exact-parity case uses equal checkpoint/snapshot and lineage; mismatches are classified and block lane acceptance.

## Handoff

Any nonzero lag proposal requires measured evidence, explicit units and a new SC decision.