# RCA-1B Checkpoint and Lineage Report

## Scope
Defines exact snapshot comparability for both database lanes.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; SC-4 requires zero-lag deterministic fixtures.

## Implementation
Dedicated fingerprinted queries read checkpoint ref/sequence/captured time and ordered lineage entries within the same repeatable-read transaction.

## Authority
Data owns checkpoint and lineage interpretation; lane owners decide semantic acceptance.

## Dependencies
`data_source_checkpoint_v1`, `data_projection_snapshot_v1`, `data_projection_lineage_v1` and explicit fixture timestamps.

## Execution Environment
No system clock, `CURRENT_TIMESTAMP` comparison baseline or `Instant.now()` is used.

## DB Access Boundary
SELECT only on explicit checkpoint/snapshot/lineage objects.

## Query Boundary
`SOURCE_CHECKPOINT_V1` and `SOURCE_LINEAGE_V1`; deterministic ordering and limit enforced.

## Dataset
One valid zero-lag baseline and registered mismatch/stale/lineage/snapshot negative cases.

## Identity/Privacy
Checkpoint refs are hashed in evidence; lineage fingerprints contain no raw identity.

## P1 Result
Unequal checkpoint, snapshot time or lineage blocks exact P1 parity.

## P2 Result
The same boundary independently applies to P2.

## Checkpoint/Lineage
Required format: opaque ref, monotonic sequence, captured-at; equality required; maximum lag zero; lineage fingerprint required.

## Evidence
Source/candidate checkpoint digest, lineage fingerprint, row counts and classifications.

## Verification
Missing/reversed/unequal/stale checkpoints and lineage mismatch cannot be reported as exact matches.

## Compatibility
Ordering and canonical digests must match PostgreSQL 15 and 18.

## Risks
No policy for live asynchronous lag is authorized.

## Exit Criteria
Baseline zero-lag/equal-lineage checks pass and negative inventory is correctly blocked.

## Handoff
Live freshness tolerance requires a separate Data/Operations/SC decision.
