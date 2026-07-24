# SC RCA-1B Exit Criteria and RCA-2 Handoff

## Scope

Define RCA-1B completion and the boundary to runtime dark read. This document does not authorize RCA-2.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; RCA-1 Model A is complete with separate P1/P2 results and no authority transfer.

## Decision

RCA-1B completes only when:

```text
NON_PRODUCTION_DB_RECONCILIATION_EXECUTED
P1_DATABASE_RESULTS_CLASSIFIED
P2_DATABASE_RESULTS_CLASSIFIED
CHECKPOINT_BOUNDARY_ENFORCED
LINEAGE_BOUNDARY_ENFORCED
READ_ONLY_BOUNDARY_ENFORCED
IDENTITY_BOUNDARY_ENFORCED
MODEL_A_AND_MODEL_B_TAXONOMY_ALIGNED
PROTECTED_AUTHORITY_UNCHANGED
NO_PRODUCTION_DATABASE
NO_PRODUCTION_TRAFFIC
NO_AUTHORITY_TRANSFER
```

P1 and P2 exit independently. A lane blocker prevents overall RCA-1B completion.

## Rationale

Database-query evidence is meaningful only when read-only, identity, checkpoint, lineage and authority boundaries are proven together.

## Authority

Intelligence approves P1 exit; Reliability approves P2 exit and evidence; Operations/Privacy approve execution boundary; SC declares phase exit.

## Dependencies

Exact-head implementation evidence, version-matrix equivalence and all blocking approvals.

## Execution Environment

Only CI ephemeral PostgreSQL completion counts for RCA-1B.

## DB Access Boundary

Any write capability, production endpoint, owner/superuser use or missing limit invalidates exit.

## Query Boundary

Only registered/fingerprinted queries count. Unexecuted queries cannot be reported as PASS.

## Identity/Privacy

Synthetic-only and zero raw-identity evidence remain mandatory.

## Evidence

Lane verdicts, mismatch inventories, counters, permission tests and cross-version digest are required.

## DB/SQL Impact

No canonical schema/SQL allocation at exit.

## Production Impact

RCA-1B completion does not authorize runtime dark read, production DB access, source replacement, production activation or authority transfer.

## Verification

SC-4 defines but does not execute these criteria.

## Risks

Successful synthetic DB reconciliation may still fail under live freshness, load, credentials or runtime latency.

## Exit Criteria

The exact criteria above plus `P2_NON_PRODUCTION_RECONCILIATION_ONLY`, `CURRENT_P2_AUTHORITY_UNCHANGED`, and `NO_AUTHORITY_TRANSFER`.

## Handoff

RCA-2 requires separate SC authorization for feature flags, timeout/fallback, observability, deployment, production credentials, traffic controls, rollback, runtime identity governance, canary and production protection.