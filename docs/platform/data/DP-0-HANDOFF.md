# DP-0 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `dp-0-handoff-v1` |
| 상태 | `COMPLETE / P2_ALIGNED / RECOVERED / IMPLEMENTATION_NOT_STARTED` |
| 소유 | Data Platform |
| canonical DB | `journey-connect-db-v2.7/01..28` |

## Completed contracts

- `jc-data-platform-contract-foundation-v1`
- `data-platform-architecture-v1`
- `platform-event-v1`
- `behavior-event-taxonomy-v1`
- `event-idempotency-fingerprint-v1`
- `event-retry-quarantine-replay-v1`
- `data-lineage-snapshot-v1`
- `data-retention-privacy-v1`
- `p0-recommendation-event-adapter-v1`
- `dp-0-p2-baseline-alignment-v1`
- `dp-0-track-change-proposal-v1`

## Baseline/protection

- P0/P1 closed; P2 technical closed/production hold
- canonical SQL 01..28 protected
- SQL 27 Search projection + Operations eligibility; SQL 28 smoke
- no production Java/Kotlin/SQL/Flyway/runtime change
- no direct cross-track write
- P1/P2 Data inputs remain shadow-only
- P2 experiment exposure and metric meanings unchanged
- `subject:<opaque-id>` and `user:<numeric-id>` remain separate

## DP-1 reservation

```text
module: jc-data-contracts
package: com.jc.data.contract
status: RESERVED / NOT IMPLEMENTED
```

DP-1 is contract types/validation/canonicalization fixtures only; no DB/runtime/ingestion/mapping/cutover.

## Blocking decisions

- Data fingerprint algorithm, encoding, version ID, exact fields and timestamp/build inclusion: `SC DECISION REQUIRED`
- identity mapping physical owner/deletion policy: unresolved
- retention production values: legal/Operations/Security approval required

## Entry

```text
DP-1 entry:
BLOCKED UNTIL PR #3 AND SC RECONCILIATION ARE BOTH MERGED
```

The exact start SHA is the first main HEAD containing both merged changes.
