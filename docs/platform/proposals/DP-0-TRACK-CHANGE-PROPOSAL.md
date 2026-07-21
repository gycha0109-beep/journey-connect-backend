# DP-0 Track Change Proposal

## 문서 정보

| 항목 | 값 |
|---|---|
| 제안 ID | `dp-0-track-change-proposal-v1` |
| 상태 | `PARTIALLY APPROVED / RECOVERED / IMPLEMENTATION NOT STARTED` |
| 위험도 | HIGH |
| DB/runtime change | none |

## Requested/registered contracts

- `platform-event-v1`
- `behavior-event-taxonomy-v1`
- `event-idempotency-fingerprint-v1`
- `event-retry-quarantine-replay-v1`
- `data-lineage-snapshot-v1`
- `data-retention-privacy-v1`
- `p0-recommendation-event-adapter-v1`
- shadow datasets: `validated-behavior-stream-v1`, `user-behavior-aggregate-v1`, `recommendation-profile-input-v1`, `search-analytics-input-v1`, `experiment-outcome-input-v1`, `data-quality-report-v1`

## Approved boundaries

- command/canonical event separation
- append-only evidence
- separate Data canonicalization domain
- duplicate/conflict idempotency distinction
- Data single write ownership for future Data objects only
- P0 read-only adapter and initial dual-write prohibition
- identity namespace separation
- P1/P2 shadow-only bridge
- no DB until DP-2 and no sequence without SC allocation

## SC reconciliation decisions

- baseline is `journey-connect-db-v2.7/01..28`
- SQL 27/28 retain Search/Operations ownership
- `jc-data-contracts` and `com.jc.data.contract` are reserved, not implemented
- DP-2 SQL is allocated by SC after 28
- authoritative sequence is IP baseline end → DP → OP → RP → cross-track validation
- old DP-1/IP-1 parallel recommendation is historical only

## Still required

- fingerprint algorithm, encoding, version wire ID, exact input set and timestamp/build inclusion
- identity mapping owner/deletion/audit implementation contract
- production retention values and legal/privacy approval

These unresolved items must not be guessed in DP-1.
