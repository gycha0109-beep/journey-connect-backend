# SC DP-1 Baseline Reconciliation

## 상태

`SC_BASELINE_RECONCILIATION_COMPLETE`

```text
Initial main: b7a613c2c9746c0bc46e6e76fc23dcf94d5029be
PR #3 original: c54e6f2efbff0664470def6a5917292d91828f77
PR #3 updated: aaea95946133f518996b7e57c7f5a657e8f161b9
PR #3 merged: NO
SC branch: codex/sc-dp1-baseline-reconciliation
SC verified content: 9979cd7e59d696a7d0d4d00d388ebfa0ce2ba75a
SC workflow: 29820224641 PASS
SC PR #4 merged: NO
```

## Authoritative baseline

- canonical DB: `journey-connect-db-v2.7/01..28`
- sequence: IP 기술 기준선 종결 → DP → OP → RP → 교차 트랙 통합 검증
- historical DP-1/IP-1 parallel recommendation does not override current sequence
- DP-1 module/package reserved, not implemented
- SQL 27/28 ownership fixed
- Decision Register/RACI canonical paths fixed

## Authority split

### Current main

PR #3 IP-12.5 operational-input controls and SC reconciliation are not yet merged; neither may be represented as current main authority.

### Pending verified PR #3

Head `aaea95946133f518996b7e57c7f5a657e8f161b9` contains latest main and passed exact-head protected CI. It remains `HOLD_OPERATIONAL_INPUTS_PENDING` and authorizes no traffic.

### Pending verified SC PR #4

The SC branch contains documentation/registry/evidence changes only. SQL/runtime/recommendation/Search source paths are unchanged.

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
BLOCKED UNTIL PR #3 AND SC RECONCILIATION ARE BOTH MERGED
```

Fingerprint implementation additionally stops until Decision `SC-DP1-009` is resolved. Identity mapping implementation/join is outside DP-1.

## No-change assertions

- production Java/Kotlin runtime: no diff
- canonical SQL 01..28/new migration: no diff
- recommendation core/Search modules/IP controls: no diff
- shadow/sampling/account cohort/Search cutover: unchanged
- Data runtime cutover: none
