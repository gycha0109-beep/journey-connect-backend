# System Coordination Handoff

## 상태

`DP6_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`

## 기준

- authoritative main: `05a25771cd99d87891504fc00890ab918b970acf`
- DP-5 implementation PR: `#16`
- DP-5 implementation HEAD: `0e2be00248988abbc8e7648aa3a00a390d923395`
- DP-5 merge commit: `05a25771cd99d87891504fc00890ab918b970acf`
- DP-5 result: `DP5_IMPLEMENTATION_COMPLETE / MAIN INTEGRATED`
- SQL `01..42`: protected
- SQL `43+`: unallocated on authoritative main

## 완료

- DP-1 through DP-5 are integrated into `main`.
- canonical event, idempotency, retry/quarantine, P0 adapter, adapter shadow evidence, projection, checkpoint, snapshot and lineage are protected authority.
- DP-5 deterministic profile/outcome projections and immutable evidence passed PostgreSQL 15/18, Java, Recommendation, Backend and SC gates.
- production worker, scheduler, replay, backfill, production shadow and cutover remain disabled or unauthorized.

## DP-6 allocation proposal

### Scope after allocation merge

DP-6 may validate—without mutating—source completeness, projection completeness, snapshot consistency, lineage integrity, identity integrity, P2 exposure integrity and deterministic rebuild equality. It may append versioned checks, quality metrics, anomalies, late-arrival observations, rebuild comparisons and snapshot quality verdicts.

It may not repair or update source events, adapter evidence, checkpoints, projection records, snapshots or lineage. It may not change Recommendation/P2 authority or metrics, write another track, activate production traffic, execute replay/backfill/rebuild, or create a worker/scheduler/purge path.

### Proposed SQL

- SQL `43`: validation run/check/anomaly foundation
- SQL `44`: quality metrics/verdict/late-arrival evidence
- SQL `45`: atomic `NEW/DUPLICATE/CONFLICT`, roles and grants
- SQL `46`: deterministic rebuild comparison and aggregate safe views
- SQL `47`: PostgreSQL 15/18 validation
- SQL `48+`: unallocated

The proposed SQL numbers are non-authoritative and must remain unused until the allocation PR is merged.

### Proposed roles

- `jc_data_quality_writer`
- `jc_data_quality_reader`
- `jc_data_quality_function_owner` (`NOLOGIN`)

These roles are proposed only and are not created by the allocation PR.

### Proposed quality policy

`data-quality-policy-v1` requires 100% source, lineage, fingerprint, identity/exposure-when-applicable and rebuild reconciliation; 0% orphan lineage; zero conflicted/rejected adapter evidence inclusion; explicit zero-denominator handling.

Verdicts are append-only:

- blocker present → `REJECTED`;
- required evidence missing or required check skipped → `INCONCLUSIVE`;
- all required checks and thresholds pass → `VALIDATED`.

A DP-6 `VALIDATED` verdict has no production, serving, release or cutover meaning.

## Current entry state

```text
DP-5: MAIN INTEGRATED
DP-6 SC ALLOCATION: PROPOSED / NOT YET AUTHORITATIVE
DP-6 IMPLEMENTATION: BLOCKED
```

The allocation PR contains documentation, registry/decision updates, machine-readable design evidence and protected-diff checks only. It contains no SQL `43+`, Java validator, DB role, persistence object or runtime validation.

## Remaining unresolved/outside DP-6 allocation

- merge approval for the proposed SQL/role/quality-policy allocation;
- DP-6 Java and PostgreSQL implementation after allocation merge;
- identity mapping physical repository and deletion workflow;
- production worker/scheduler deployment and activation;
- replay/backfill/automatic rebuild execution;
- production consumer and P1/P2 cutover;
- Operations dashboard/alert routing;
- legal/country-specific retention and erasure;
- SQL `48+` allocation.

## Protected state

```text
Production shadow: DISABLED
Kill switch: true
Effective sampling: 0 BPS
Actual cohort: empty / 0%
Search cutover: NOT STARTED
Production traffic: NOT APPROVED
Go/No-Go: NO_GO_FOR_TRAFFIC
```

## Next baseline

If the SC allocation PR is approved and merged, its merge commit becomes the authoritative base for the separate DP-6 implementation PR. User approval is required for both merges.