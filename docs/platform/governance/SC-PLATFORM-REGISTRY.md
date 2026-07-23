# SC Contract, Module and Namespace Registry

| 항목 | 값 |
|---|---|
| 계약 ID | `sc-platform-registry-v1` |
| 상태 | `ACTIVE / DP-0~DP-7 MAIN INTEGRATED` |
| authoritative main | `c528f6fb0942389b70a348cb9aa672eb7819a392` |

## Modules

| Module | Package | Owner | Status |
|---|---|---|---|
| `jc-intelligence-contracts` | `com.jc.intelligence.contract` | Intelligence | ACTIVE |
| `jc-data-contracts` | `com.jc.data.contract.v1` | Data | ACTIVE / DP1~7 |
| `jc-search-contracts` | Search contract package | Search | ACTIVE / PROTECTED |

## Identity and exposure

| ID | Authority | Status |
|---|---|---|
| `platform_subject_v1` | Data pseudonymous identity | ACTIVE |
| `legacy_user_numeric_v1` | protected P2 identity | ACTIVE / COMPATIBILITY |
| restricted identity mapping | SC-designated owner | UNRESOLVED / NOT IMPLEMENTED |
| `recommendation_general_exposure_v1` | Recommendation | ACTIVE |
| `recommendation_behavior_impression_v1` | behavior fact | ACTIVE / NOT P2 DENOMINATOR |
| `recommendation_p2_experiment_exposure_v1` | protected P2 table | PROTECTED AUTHORITY |
| `search_exposure_v1` | Search | RESERVED / NOT IMPLEMENTED |

## Contracts and fingerprint domains

| Contract ID | Status |
|---|---|
| `jc-data-platform-contract-foundation-v1` | ACTIVE |
| `data-platform-architecture-v1` | ACTIVE |
| `platform-event-v1` | ACTIVE / DP2 PERSISTED |
| `platform-event-canonical-json-v1` | ACTIVE |
| `platform-event-fingerprint-sha256-v1` | ACTIVE |
| `event-idempotency-fingerprint-v1` | ACTIVE |
| `event-retry-quarantine-replay-v1` | ACTIVE / REPLAY NOT AUTHORIZED |
| `data-lineage-snapshot-v1` | ACTIVE |
| `dp-1-event-domain-types-validation-v1` | ACTIVE |
| `dp-2-postgresql-event-store-idempotency-v1` | ACTIVE |
| `dp-4-recommendation-event-adapter-v1` | ACTIVE / SHADOW |
| `dp-4-5-recommendation-adapter-shadow-evidence-v1` | ACTIVE / SHADOW |
| `recommendation-profile-input-v1` | ACTIVE / SHADOW-ONLY |
| `experiment-outcome-input-v1` | ACTIVE / SHADOW-ONLY |
| `data-projection-snapshot-v1` | ACTIVE |
| `data-source-set-sha256-v1` | ACTIVE |
| `data-projection-record-sha256-v1` | ACTIVE |
| `data-projection-snapshot-sha256-v1` | ACTIVE |
| `data-projection-lineage-sha256-v1` | ACTIVE |
| `data-quality-policy-v1` | ACTIVE |
| `data-quality-validation-input-sha256-v1` | ACTIVE |
| `data-quality-check-evidence-sha256-v1` | ACTIVE |
| `data-quality-metric-sha256-v1` | ACTIVE |
| `data-quality-verdict-sha256-v1` | ACTIVE |
| `data-quality-rebuild-comparison-sha256-v1` | ACTIVE |
| `data-quality-late-arrival-observation-sha256-v1` | ACTIVE |
| `data-cross-track-integration-policy-v1` | ACTIVE |
| `data-cross-track-integration-run-v1` | ACTIVE |
| `data-cross-track-integration-check-v1` | ACTIVE |
| `data-cross-track-contract-mapping-v1` | ACTIVE |
| `data-cross-track-authority-matrix-v1` | ACTIVE |
| `data-cross-track-privacy-retention-matrix-v1` | ACTIVE |
| `data-cross-track-integration-verdict-v1` | ACTIVE |
| `integration-input-sha256-v1` | ACTIVE |
| `integration-check-evidence-sha256-v1` | ACTIVE |
| `integration-mapping-sha256-v1` | ACTIVE |
| `integration-verdict-sha256-v1` | ACTIVE |
| `cross-track-contract-matrix-sha256-v1` | ACTIVE |
| `data-platform-technical-baseline-v1` | CLOSURE CANDIDATE |
| `data-platform-authority-closure-v1` | CLOSURE CANDIDATE |
| `data-platform-production-readiness-gaps-v1` | CLOSURE CANDIDATE |
| `data-platform-production-activation-dependencies-v1` | CLOSURE CANDIDATE |
| `data-platform-change-policy-v1` | CLOSURE CANDIDATE |

## Data DB roles

| Role | Authority | Status |
|---|---|---|
| `jc_data_event_writer` | event persistence function | ACTIVE |
| `jc_data_event_reader` | approved read | ACTIVE |
| `jc_data_retry_processor` | retry procedures | ACTIVE / NO SCHEDULER |
| `jc_data_quarantine_reviewer` | review evidence | ACTIVE |
| `jc_data_replay_executor` | replay | RESERVED / NO EXECUTE |
| `jc_data_adapter_evidence_writer` | adapter function | ACTIVE |
| `jc_data_adapter_evidence_reader` | adapter safe view | ACTIVE |
| `jc_data_adapter_evidence_function_owner` | NOLOGIN owner | ACTIVE |
| `jc_data_projection_writer` | projection functions | ACTIVE |
| `jc_data_projection_reader` | projection safe view | ACTIVE |
| `jc_data_projection_function_owner` | NOLOGIN owner | ACTIVE |
| `jc_data_quality_writer` | quality function | ACTIVE |
| `jc_data_quality_reader` | quality safe view | ACTIVE |
| `jc_data_quality_function_owner` | NOLOGIN owner | ACTIVE |
| `jc_data_integration_writer` | integration function | ACTIVE |
| `jc_data_integration_reader` | integration safe view | ACTIVE |
| `jc_data_integration_function_owner` | NOLOGIN owner | ACTIVE |

## DB sequence

| Range | Purpose | Status |
|---|---|---|
| `01..26` | canonical + P2 | PROTECTED |
| `27..28` | Search/Operations projection + validation | PROTECTED |
| `29..31` | DP2 event/idempotency | ACTIVE / IMMUTABLE |
| `32..34` | DP3 retry/quarantine | ACTIVE / IMMUTABLE |
| `35..37` | DP4.5 adapter evidence | ACTIVE / IMMUTABLE |
| `38..42` | DP5 projection/snapshot | ACTIVE / IMMUTABLE |
| `43..47` | DP6 quality | ACTIVE / IMMUTABLE |
| `48..52` | DP7 integration | ACTIVE / IMMUTABLE |
| `53+` | unallocated | SC ASSIGNMENT REQUIRED |
