# System Coordination Handoff

## Status

`DP-6 MAIN INTEGRATED / DP-7 ALLOCATION PROPOSED`

## Authoritative baseline

- DP-6 implementation PR #18: merged;
- DP-6 implementation HEAD: `0e9b09283bad61faa830db1019d421c6e906fc7c`;
- DP-6 merge commit/current main: `69b2f9619733e8e6068a23bb149c2aaf41f23fc9`;
- SQL `01..47`: implemented and protected;
- SQL `48+`: unallocated on the work-start baseline and absent from the DP-7 allocation PR;
- DP-7 implementation authority: not granted.

## DP-7 allocation proposal

DP-7 validates whether Data snapshots with exact DP-6 `VALIDATED` quality verdicts are compatible with Recommendation, Intelligence and Search contracts without modifying target-track authority or activating a runtime.

Proposed SQL allocation after explicit merge:

```text
48_cross_track_integration_validation_foundation.sql
49_cross_track_contract_mapping_and_boundary_evidence.sql
50_cross_track_integration_verdict_and_conflict.sql
51_cross_track_integration_persistence_roles_and_safe_view.sql
52_cross_track_integration_validation.sql
```

Proposed roles:

```text
jc_data_integration_writer
jc_data_integration_reader
jc_data_integration_function_owner
```

Proposed policy:

```text
data-cross-track-integration-policy-v1
```

These are proposal-only entries. No SQL, role, grant, function, view or Java DP-7 implementation is authorized by the branch alone.

## Allocation-time target findings

- Recommendation profile projection: `CONDITIONALLY_COMPATIBLE`; current P1 source remains authoritative.
- Recommendation experiment outcome projection: `CONDITIONALLY_COMPATIBLE`; P2 exposure/dataset/metric authority remains unchanged.
- Intelligence input: `INCONCLUSIVE`; generic snapshot envelope exists but Data-specific semantic input mapping is absent.
- Search input: `INCONCLUSIVE`; no approved Data input contract exists and DP-5 profile/outcome grain is not a Search document.
- Full integration: not executable before allocation; no runtime PASS may be claimed.

## Required integration policy boundaries

- only exact snapshot-bound DP-6 `VALIDATED` verdicts may proceed;
- `REJECTED`, `INCONCLUSIVE`, missing, conflicted or unsupported quality verdicts fail closed;
- `subject:<opaque-id>` and `user:<numeric-id>` remain separate;
- quality status is not Intelligence confidence, Search readiness or production approval;
- P2 engagement/fallback semantics and exposure authority remain exact;
- target contract ambiguity yields `INCONCLUSIVE`;
- target object write, runtime activation, Search index write and cutover are blockers;
- unexecuted checks are never PASS.

## Proposed persistence outcome

Logical identity:

```text
source_snapshot_ref
+ source_track
+ target_track
+ source_contract
+ target_contract
+ integration_scope
+ validator_version
+ integration_policy_version
```

Outcome:

```text
NEW
DUPLICATE
CONFLICT / CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT
```

Append-only evidence, atomic exact-one-NEW concurrency and aggregate-only reader access are implementation requirements after allocation.

## Protected state

```text
Production shadow: DISABLED
Kill switch: true
Effective sampling: 0 BPS
Actual cohort: empty / 0%
Search cutover: NOT STARTED
/api/v1/explore authority: LEGACY
Production traffic: NOT APPROVED
Recommendation production write: DISABLED
Intelligence runtime activation by DP-7: PROHIBITED
Worker/scheduler: ABSENT / DISABLED
Replay/backfill/rebuild/purge: UNAUTHORIZED
```

## Current gate

`DP7_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`

The allocation PR requires exact-head static allocation, protected-diff and current Data/Recommendation/Intelligence/Search/backend contract regressions. It must not be merged automatically. If explicitly merged, its merge commit becomes the only valid base for a separate DP-7 implementation PR.
