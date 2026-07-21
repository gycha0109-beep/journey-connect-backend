# SC DP-1 Baseline Reconciliation

## 상태

`SC_BASELINE_RECONCILIATION_COMPLETE`

```text
Initial main: b7a613c2c9746c0bc46e6e76fc23dcf94d5029be
PR #3 original: c54e6f2efbff0664470def6a5917292d91828f77
PR #3 verified head: aaea95946133f518996b7e57c7f5a657e8f161b9
PR #3 merge commit: f38cf56b34ff23fbd5cb20b9013444a8cb2d29f4
PR #3 merged: YES
SC branch: codex/sc-dp1-baseline-reconciliation
SC main synchronization: 72c59299392b86125e77d0b2463ad102f02287b1
SC PR #4 merged: NO
```

## Authoritative baseline

- current main authority includes merged PR #3
- canonical DB: `journey-connect-db-v2.7/01..28`
- sequence: IP 기술 기준선 종결 → DP → OP → RP → 교차 트랙 통합 검증
- historical DP-1/IP-1 parallel recommendation does not override current sequence
- DP-1 module/package reserved, not implemented
- SQL 27/28 ownership fixed
- Decision Register/RACI canonical paths fixed

## Authority split

### Current main

PR #3 IP-12.5 operational-input controls are merged and authoritative through `f38cf56b34ff23fbd5cb20b9013444a8cb2d29f4`.

`IP-12.5 HOLD_OPERATIONAL_INPUTS_PENDING` remains unchanged. The merge does not authorize production traffic, sampling, cohort population, kill-switch release or Search cutover.

### Pending verified SC PR #4

The SC branch contains documentation/registry/evidence changes only. SQL/runtime/recommendation/Search source paths are unchanged. PR #4 must pass exact-head validation before merge.

## Restored DP-0 package

- `DATA-PLATFORM-ARCHITECTURE-V1.md`
- `PLATFORM-EVENT-CONTRACT-V1.md`
- `BEHAVIOR-EVENT-TAXONOMY-V1.md`
- `EVENT-IDEMPOTENCY-AND-FINGERPRINT-V1.md`
- `EVENT-RETRY-QUARANTINE-REPLAY-V1.md`
- `DATA-LINEAGE-AND-SNAPSHOT-V1.md`
- `DATA-RETENTION-AND-PRIVACY-V1.md`
- `P0-RECOMMENDATION-EVENT-ADAPTER-V1.md`
- `DP-0-HANDOFF.md`
- `../proposals/DP-0-TRACK-CHANGE-PROPOSAL.md`

## DP-1 gate

```text
DP-1 entry:
BLOCKED UNTIL SC PR #4 IS MERGED
```

Fingerprint implementation additionally stops until Decision `SC-DP1-009` is resolved. Identity mapping implementation/join is outside DP-1.

## No-change assertions

- production Java/Kotlin runtime: no diff
- canonical SQL 01..28/new migration: no diff
- recommendation core/Search modules/IP controls: no semantic change by SC PR
- shadow/sampling/account cohort/Search cutover: unchanged
- Data runtime cutover: none
