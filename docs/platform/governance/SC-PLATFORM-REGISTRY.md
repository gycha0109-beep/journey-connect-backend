# SC Contract, Module and Namespace Registry

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `sc-platform-registry-v1` |
| 상태 | `ACTIVE` |

## Module/package

| Module | Package root | Owner | Status |
|---|---|---|---|
| `jc-intelligence-contracts` | `com.jc.intelligence.contract` | Intelligence | ACTIVE |
| `jc-data-contracts` | `com.jc.data.contract.v1` | Data | ACTIVE / DP-2 MAIN INTEGRATED |

## Identity schemes

| Scheme | Wire | Status |
|---|---|---|
| `platform_subject_v1` | `subject:<opaque-id>` | ACTIVE |
| `legacy_user_numeric_v1` | `user:<numeric-id>` | PROTECTED COMPATIBILITY |

## Exposure sources

| Source ID | Authority | Status |
|---|---|---|
| `recommendation_general_exposure_v1` | recommendation general exposure tables | ACTIVE |
| `recommendation_behavior_impression_v1` | behavior event impression | ACTIVE / NOT P2 DENOMINATOR |
| `recommendation_p2_experiment_exposure_v1` | `recommendation_p2_experiment_exposure` | PROTECTED AUTHORITY |
| `search_exposure_v1` | not implemented | RESERVED |

## Data contracts

| Contract ID | Status |
|---|---|
| `jc-data-platform-contract-foundation-v1` | ACTIVE |
| `data-platform-architecture-v1` | ACTIVE |
| `platform-event-v1` | ACTIVE / DP-2 PERSISTED |
| `platform-event-canonical-json-v1` | ACTIVE |
| `platform-event-fingerprint-sha256-v1` | ACTIVE / DP-2 IMPLEMENTED |
| `event-idempotency-fingerprint-v1` | ACTIVE / DP-2 IMPLEMENTED |
| `event-retry-quarantine-replay-v1` | ACTIVE / DP-3 IMPLEMENTATION AUTHORIZED |
| `data-lineage-snapshot-v1` | ACTIVE |
| `dp-1-event-domain-types-validation-v1` | ACTIVE / MAIN INTEGRATED |
| `dp-2-postgresql-event-store-idempotency-v1` | ACTIVE / MAIN INTEGRATED |

## Data DB roles

| Role | Authority | Status |
|---|---|---|
| `jc_data_event_writer` | approved Data event-store write boundary only | ACTIVE / DP-2 |
| `jc_data_event_reader` | approved Data read contracts only | ACTIVE / DP-2 |
| `jc_data_retry_processor` | approved retry claim/complete/fail/quarantine procedures only | ASSIGNED TO DP-3 |
| `jc_data_quarantine_reviewer` | append-only review/retain/release-request evidence only | ASSIGNED TO DP-3 |
| `jc_data_replay_executor` | approved replay procedures; no direct canonical mutation | RESERVED / NO EXECUTE GRANT IN DP-3 |

## DB sequence

| Range | Owner/purpose | Status |
|---|---|---|
| `01..26` | existing canonical + Recommendation P2 | PROTECTED |
| `27` | Search projection / Operations eligibility | PROTECTED |
| `28` | SQL 27 smoke test | PROTECTED |
| `29` | Data canonical event store/evidence base | ACTIVE / DP-2 |
| `30` | Data idempotency/atomic ingest/grants | ACTIVE / DP-2 |
| `31` | DP-2 PostgreSQL smoke/contract/concurrency verification | ACTIVE / DP-2 |
| `32` | Data retry schedule/attempt/quarantine evidence | ASSIGNED TO DP-3 |
| `33` | atomic claim/lease/complete/fail/quarantine procedures and grants | ASSIGNED TO DP-3 |
| `34` | DP-3 smoke/concurrency/lease/authority verification | ASSIGNED TO DP-3 |
| `35+` | unallocated | SC ASSIGNMENT REQUIRED |
