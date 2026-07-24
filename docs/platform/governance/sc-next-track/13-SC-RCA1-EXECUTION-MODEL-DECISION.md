# SC RCA-1 Execution Model Decision

## Scope

Select one execution model for RCA-1 using authoritative main `f802a105e46a62718616acaa7a3db6c172e7ed10` and RCA-0 head `d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d`.

## Current Baseline

RCA-0 is merged and DB-free. Current P1/P2 sources remain authoritative. No identity mapping, runtime reader or production activation exists.

## Decision

| Model | Verdict | Boundary |
|---|---|---|
| Model A — offline deterministic reconciliation | `APPROVED` | official RCA-1 model |
| Model B — non-production read-only PostgreSQL | `DEFERRED` | separate RCA-1B authorization |
| Model C — runtime dark read | `BLOCKED` | RCA-2 candidate |

```text
RCA1_EXECUTION_MODEL=MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION
```

## Rationale

Model A maximizes reproducibility and allows field/semantic/authority classification without unresolved identity or operational dependencies. Model B can test real queries and checkpoint behavior but introduces roles, credentials, limits and data handling. Model C changes runtime behavior and needs Operations controls, timeout/fallback and observability.

## Authority

SC selects the model. Intelligence owns P1 comparison. Reliability owns P2 comparison. Operations owns any later execution environment.

## Dependencies

Recorded authoritative snapshots, Data candidate fixtures, explicit reference time, deterministic normalization and verifier version.

## Allowed Changes

Pure Java/offline comparison and synthetic fixtures in a separate implementation PR.

## Forbidden Changes

DB reads, Spring wiring, feature flags, runtime dark reads, production traffic and authority transfer.

## Identity/Privacy

`SYNTHETIC_ONLY`; no real identity data or mapping port implementation.

## DB/SQL Impact

None. SQL allocation is not required.

## Production Impact

None. Runtime alerts, dashboard and traffic percentages are deferred to RCA-2.

## Verification

Verify exactly one approved model marker and reject simultaneous Model A/B/C activation.

## Risks

Fixture-only execution cannot prove freshness, checkpoint lag, query plan behavior or production latency.

## Exit Criteria

Model A decision is machine-readable, unique and consistently referenced in governance, evidence and handoff.

## Handoff

RCA-1 implementation must reject any DB/runtime dependency as out of scope.
