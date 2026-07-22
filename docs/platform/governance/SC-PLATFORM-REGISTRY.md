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
| `jc-data-contracts` | `com.jc.data.contract.v1` | Data | ACTIVE / DP-4.5 MAIN INTEGRATED |

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
| `event-retry-quarantine-replay-v1` | ACTIVE / DP-3 IMPLEMENTED |
| `data-lineage-snapshot-v1` | ACTIVE |
| `dp-1-event-domain-types-validation-v1` | ACTIVE / MAIN INTEGRATED |
| `dp-2-postgresql-event-store-idempotency-v1` | ACTIVE / MAIN INTEGRATED |
| `dp-4-recommendation-event-adapter-v1` | ACTIVE / MAIN INTEGRATED |
| `dp-4-5-recommendation-adapter-shadow-evidence-v1` | ACTIVE / MAIN INTEGRATED |
| `recommendation-profile-input-v1` | SHADOW-ONLY / DP-5 IMPLEMENTED PENDING CI |
| `experiment-outcome-input-v1` | SHADOW-ONLY / DP-5 IMPLEMENTED PENDING CI |
| `data-projection-snapshot-v1` | DP-5 IMPLEMENTED PENDING CI |
| `data-source-set-sha256-v1` | DP-5 IMPLEMENTED PENDING CI |
| `data-projection-record-sha256-v1` | DP-5 IMPLEMENTED PENDING CI |
| `data-projection-snapshot-sha256-v1` | DP-5 IMPLEMENTED PENDING CI |
| `data-projection-lineage-sha256-v1` | DP-5 IMPLEMENTED PENDING CI |

## Data DB roles

| Role | Authority | Status |
|---|---|---|
| `jc_data_event_writer` | approved Data event-store write boundary only | ACTIVE / DP-2 |
| `jc_data_event_reader` | approved Data read contracts only | ACTIVE / DP-2 |
| `jc_data_retry_processor` | approved retry procedures only | ACTIVE / DP-3 |
| `jc_data_quarantine_reviewer` | append-only review evidence only | ACTIVE / DP-3 |
| `jc_data_replay_executor` | approved replay procedures; no direct canonical mutation | RESERVED / NO EXECUTE GRANT |
| `jc_data_adapter_evidence_writer` | execute DP-4.5 persistence function only | ACTIVE / DP-4.5 |
| `jc_data_adapter_evidence_reader` | select DP-4.5 safe aggregate view only | ACTIVE / DP-4.5 |
| `jc_data_adapter_evidence_function_owner` | NOLOGIN DP-4.5 function owner | ACTIVE / DP-4.5 |
| `jc_data_projection_writer` | execute DP-5 atomic persistence only | IMPLEMENTED PENDING CI |
| `jc_data_projection_reader` | select DP-5 aggregate safe view only | IMPLEMENTED PENDING CI |
| `jc_data_projection_function_owner` | NOLOGIN DP-5 function owner | IMPLEMENTED PENDING CI |

## DB sequence

| Range | Owner/purpose | Status |
|---|---|---|
| `01..26` | existing canonical + Recommendation P2 | PROTECTED |
| `27` | Search projection / Operations eligibility | PROTECTED |
| `28` | SQL 27 smoke test | PROTECTED |
| `29` | Data canonical event store/evidence base | ACTIVE / DP-2 |
| `30` | Data idempotency/atomic ingest/grants | ACTIVE / DP-2 |
| `31` | DP-2 PostgreSQL validation | ACTIVE / DP-2 |
| `32` | Data retry/quarantine evidence | ACTIVE / DP-3 |
| `33` | retry claim/lease/procedures/grants | ACTIVE / DP-3 |
| `34` | DP-3 validation | ACTIVE / DP-3 |
| `35` | Recommendation adapter shadow evidence | ACTIVE / DP-4.5 |
| `36` | adapter atomic persistence/roles/safe view | ACTIVE / DP-4.5 |
| `37` | DP-4.5 validation | ACTIVE / DP-4.5 |
| `38` | projection run/checkpoint/snapshot/lineage foundation | IMPLEMENTED PENDING CI |
| `39` | recommendation profile input projection | IMPLEMENTED PENDING CI |
| `40` | experiment outcome input projection | IMPLEMENTED PENDING CI |
| `41` | atomic persistence/roles/safe view | IMPLEMENTED PENDING CI |
| `42` | DP-5 PostgreSQL 15/18 validation | IMPLEMENTED PENDING CI |
| `43+` | unallocated | SC ASSIGNMENT REQUIRED |
