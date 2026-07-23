# SC Contract, Module and Namespace Registry

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `sc-platform-registry-v1` |
| 상태 | `ACTIVE / DP-6 MAIN INTEGRATED / DP-7 ALLOCATION PROPOSED` |

## Module/package

| Module | Package root | Owner | Status |
|---|---|---|---|
| `jc-intelligence-contracts` | `com.jc.intelligence.contract` | Intelligence | ACTIVE |
| `jc-data-contracts` | `com.jc.data.contract.v1` | Data | ACTIVE / DP-6 MAIN INTEGRATED; DP-7 INTEGRATION PACKAGE PROPOSED |

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
| `recommendation-profile-input-v1` | SHADOW-ONLY / DP-5 MAIN INTEGRATED |
| `experiment-outcome-input-v1` | SHADOW-ONLY / DP-5 MAIN INTEGRATED |
| `data-projection-snapshot-v1` | DP-5 MAIN INTEGRATED |
| `data-source-set-sha256-v1` | DP-5 MAIN INTEGRATED |
| `data-projection-record-sha256-v1` | DP-5 MAIN INTEGRATED |
| `data-projection-snapshot-sha256-v1` | DP-5 MAIN INTEGRATED |
| `data-projection-lineage-sha256-v1` | DP-5 MAIN INTEGRATED |
| `data-quality-policy-v1` | ACTIVE / DP-6 MAIN INTEGRATED |
| `data-quality-validation-input-sha256-v1` | ACTIVE / DP-6 MAIN INTEGRATED |
| `data-quality-check-evidence-sha256-v1` | ACTIVE / DP-6 MAIN INTEGRATED |
| `data-quality-metric-sha256-v1` | ACTIVE / DP-6 MAIN INTEGRATED |
| `data-quality-verdict-sha256-v1` | ACTIVE / DP-6 MAIN INTEGRATED |
| `data-quality-rebuild-comparison-sha256-v1` | ACTIVE / DP-6 MAIN INTEGRATED |
| `data-quality-late-arrival-observation-sha256-v1` | ACTIVE / DP-6 MAIN INTEGRATED |
| `data-cross-track-integration-policy-v1` | PROPOSED / DP-7 ALLOCATION MERGE REQUIRED |
| `data-cross-track-integration-run-v1` | PROPOSED / DP-7 ALLOCATION MERGE REQUIRED |
| `data-cross-track-integration-check-v1` | PROPOSED / DP-7 ALLOCATION MERGE REQUIRED |
| `data-cross-track-contract-mapping-v1` | PROPOSED / DP-7 ALLOCATION MERGE REQUIRED |
| `data-cross-track-authority-matrix-v1` | PROPOSED / DP-7 ALLOCATION MERGE REQUIRED |
| `data-cross-track-privacy-retention-matrix-v1` | PROPOSED / DP-7 ALLOCATION MERGE REQUIRED |
| `data-cross-track-integration-verdict-v1` | PROPOSED / DP-7 ALLOCATION MERGE REQUIRED |
| `integration-input-sha256-v1` | PROPOSED / DP-7 ALLOCATION MERGE REQUIRED |
| `integration-check-evidence-sha256-v1` | PROPOSED / DP-7 ALLOCATION MERGE REQUIRED |
| `integration-mapping-sha256-v1` | PROPOSED / DP-7 ALLOCATION MERGE REQUIRED |
| `integration-verdict-sha256-v1` | PROPOSED / DP-7 ALLOCATION MERGE REQUIRED |
| `cross-track-contract-matrix-sha256-v1` | PROPOSED / DP-7 ALLOCATION MERGE REQUIRED |

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
| `jc_data_projection_writer` | execute DP-5 atomic persistence only | ACTIVE / DP-5 MAIN INTEGRATED |
| `jc_data_projection_reader` | select DP-5 aggregate safe view only | ACTIVE / DP-5 MAIN INTEGRATED |
| `jc_data_projection_function_owner` | NOLOGIN DP-5 function owner | ACTIVE / DP-5 MAIN INTEGRATED |
| `jc_data_quality_writer` | approved DP-6 validation/verdict function only | ACTIVE / DP-6 MAIN INTEGRATED |
| `jc_data_quality_reader` | aggregate quality safe-view SELECT only | ACTIVE / DP-6 MAIN INTEGRATED |
| `jc_data_quality_function_owner` | NOLOGIN DP-6 function owner | ACTIVE / DP-6 MAIN INTEGRATED |
| `jc_data_integration_writer` | proposed DP-7 atomic persistence execute-only boundary | PROPOSED / NOT CREATED |
| `jc_data_integration_reader` | proposed DP-7 aggregate safe-view SELECT only | PROPOSED / NOT CREATED |
| `jc_data_integration_function_owner` | proposed NOLOGIN DP-7 function owner | PROPOSED / NOT CREATED |

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
| `38` | projection run/checkpoint/snapshot/lineage foundation | ACTIVE / DP-5 MAIN INTEGRATED |
| `39` | recommendation profile input projection | ACTIVE / DP-5 MAIN INTEGRATED |
| `40` | experiment outcome input projection | ACTIVE / DP-5 MAIN INTEGRATED |
| `41` | atomic persistence/roles/safe view | ACTIVE / DP-5 MAIN INTEGRATED |
| `42` | DP-5 PostgreSQL 15/18 validation | ACTIVE / DP-5 MAIN INTEGRATED |
| `43` | quality validation run/check/anomaly foundation | ACTIVE / DP-6 MAIN INTEGRATED |
| `44` | quality metrics/verdict/late-arrival evidence | ACTIVE / DP-6 MAIN INTEGRATED |
| `45` | atomic quality persistence and roles | ACTIVE / DP-6 MAIN INTEGRATED |
| `46` | rebuild comparison and safe aggregate views | ACTIVE / DP-6 MAIN INTEGRATED |
| `47` | DP-6 PostgreSQL 15/18 validation | ACTIVE / DP-6 MAIN INTEGRATED |
| `48` | proposed cross-track integration run/check foundation | PROPOSED / NOT ALLOCATED UNTIL MERGE |
| `49` | proposed mapping and authority/privacy/retention evidence | PROPOSED / NOT ALLOCATED UNTIL MERGE |
| `50` | proposed integration verdict and conflict evidence | PROPOSED / NOT ALLOCATED UNTIL MERGE |
| `51` | proposed atomic persistence, roles and aggregate safe view | PROPOSED / NOT ALLOCATED UNTIL MERGE |
| `52` | proposed PostgreSQL 15/18 DP-7 validation | PROPOSED / NOT ALLOCATED UNTIL MERGE |
| `53+` | unallocated | SC ASSIGNMENT REQUIRED |

The proposed SQL `48..52`, roles and contract IDs are not implementation authority until the DP-7 allocation PR is explicitly reviewed and merged. This allocation-only PR must contain no SQL `48+`, role creation or Java DP-7 implementation.
