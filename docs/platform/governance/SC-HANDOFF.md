# System Coordination Handoff

## 상태

`SC_BASELINE_RECONCILIATION_COMPLETE`

## 기준

- initial main HEAD: `b7a613c2c9746c0bc46e6e76fc23dcf94d5029be`
- PR #3 original HEAD: `c54e6f2efbff0664470def6a5917292d91828f77`
- PR #3 updated/verified HEAD: `aaea95946133f518996b7e57c7f5a657e8f161b9`
- PR #3 merge commit: `f38cf56b34ff23fbd5cb20b9013444a8cb2d29f4`
- PR #3 exact-head CI: Backend `29818133726`, Core `29818133742`, PostgreSQL 15/18 `29818133764` — PASS
- SC branch: `codex/sc-dp1-baseline-reconciliation`
- SC branch start: `b7a613c2c9746c0bc46e6e76fc23dcf94d5029be`
- latest main incorporated into SC branch: `f38cf56b34ff23fbd5cb20b9013444a8cb2d29f4`
- SC synchronization merge commit: `72c59299392b86125e77d0b2463ad102f02287b1`
- PR #3 merged: `YES`
- SC PR #4 merged: `NO`

## 완료

- PR #3 merged into `main`; protected IP-12.5 controls are current main authority
- IP-12.5 remains `HOLD_OPERATIONAL_INPUTS_PENDING` and authorizes no production traffic
- current main incorporated into PR #4 without history rewrite
- DB baseline reconciled to `journey-connect-db-v2.7/01..28`
- SQL 27/28 ownership fixed without SQL changes
- authoritative execution sequence and historical recommendation precedence fixed
- DP-0 contract package restored and Foundation inventory matched
- `jc-data-contracts` / `com.jc.data.contract` reserved only
- producer/consumer/build version and compatibility rules fixed
- canonical JSON, idempotency, lineage, snapshot, replay and backfill boundaries fixed
- Decision Register/RACI canonical paths fixed
- documentation-only workflow and machine-readable evidence added
- protected diff limited to governance/data/proposal/evidence/workflow paths

## Explicit unresolved decision

New Data fingerprint algorithm, output encoding, version ID, exact field set and timestamp/build inclusion remain `SC DECISION REQUIRED` under `SC-DP1-009`. This is an explicit DP-1 fingerprint implementation stop condition, not an incomplete reconciliation artifact.

Identity mapping physical owner/deletion policy also remains unresolved and is outside DP-1.

## DP-1 entry

```text
DP-1 entry:
BLOCKED UNTIL SC PR #4 IS MERGED
```

DP-1 exact start SHA is the first `main` HEAD containing both merged PR #3 and merged PR #4. It remains pending until PR #4 merge.

## Protected state

```text
IP-12.5: HOLD_OPERATIONAL_INPUTS_PENDING
Production shadow: DISABLED
Kill switch: true
Effective sampling: 0 BPS
Actual cohort: empty / 0%
Search cutover: NOT STARTED
Production traffic: NOT APPROVED
Go/No-Go: NO_GO_FOR_TRAFFIC
```
