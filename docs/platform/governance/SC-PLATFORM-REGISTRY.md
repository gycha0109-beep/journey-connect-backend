# SC Contract, Module, Namespace and Sequence Registry

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-platform-registry-v1` |
| status | `ACTIVE / DATA_PLATFORM_CLOSED / RCA-0 RESERVED` |
| authoritative main | `95dad33fd56a54d69e2497c11dc4e2e77d8d3a77` |
| canonical SQL | `01..52` |
| unallocated SQL | `53+` |

## Modules and packages

| Module | Package root | Owner | Status |
|---|---|---|---|
| `jc-intelligence-contracts` | `com.jc.intelligence.contract` | Intelligence | ACTIVE |
| `jc-data-contracts` | `com.jc.data.contract.v1` | Data | ACTIVE / DP-1..DP-7 CLOSED BASELINE |
| RCA-0 backend boundary | `com.jc.backend.recommendation.dataadoption` | Intelligence implementation lead; P2 semantics Reliability | RESERVED / NO SPRING OR DB WIRING |

## Workstream and phase identifiers

| ID | Meaning | Status |
|---|---|---|
| `RCA` | Recommendation Consumer Adoption cross-track workstream | RESERVED |
| `RCA-0` | Recommendation Data Consumer Contract & Fixture Alignment | CONDITIONAL ENTRY AFTER SC-2 MERGE |
| `RCA-1` | shadow reconciliation | RESERVED / NOT AUTHORIZED |
| `RP` | Reliability Platform | PROTECTED ACRONYM |

## Identity schemes

| Scheme | Wire | Status |
|---|---|---|
| `platform_subject_v1` | `subject:<opaque-id>` | ACTIVE |
| `legacy_user_numeric_v1` | `user:<numeric-id>` | PROTECTED COMPATIBILITY |

Identity mapping physical owner, deletion and retention remain unresolved. No repository or join is authorized.

## Exposure sources

| Source ID | Authority | Status |
|---|---|---|
| `recommendation_general_exposure_v1` | recommendation general exposure rows | ACTIVE |
| `recommendation_behavior_impression_v1` | behavior event `impression` | ACTIVE / NOT P2 DENOMINATOR |
| `recommendation_p2_experiment_exposure_v1` | `recommendation_p2_experiment_exposure` | PROTECTED AUTHORITY |
| `search_exposure_v1` | not implemented | RESERVED |

## Intelligence contracts

| Contract ID | Status |
|---|---|
| `intelligence-run-v1` | ACTIVE |
| `intelligence-input-snapshot-v1` | ACTIVE |
| `intelligence-candidate-snapshot-v1` | ACTIVE |
| `intelligence-output-snapshot-v1` | ACTIVE |
| `intelligence-feature-value-v1` | ACTIVE |
| `intelligence-explanation-v1` | ACTIVE |
| `model-inference-record-v1` | ACTIVE |

## Data contracts

| Contract ID | Status |
|---|---|
| `jc-data-platform-contract-foundation-v1` | ACTIVE / CLOSED BASELINE |
| `data-platform-architecture-v1` | ACTIVE / CLOSED BASELINE |
| `platform-event-v1` | ACTIVE / DP-2 IMPLEMENTED |
| `platform-event-canonical-json-v1` | ACTIVE |
| `platform-event-fingerprint-sha256-v1` | ACTIVE / DP-2 IMPLEMENTED |
| `event-idempotency-fingerprint-v1` | ACTIVE / DP-2 IMPLEMENTED |
| `event-retry-quarantine-replay-v1` | ACTIVE / DP-3 IMPLEMENTED |
| `data-lineage-snapshot-v1` | ACTIVE |
| `dp-1-event-domain-types-validation-v1` | ACTIVE / IMPLEMENTED |
| `dp-2-postgresql-event-store-idempotency-v1` | ACTIVE / IMPLEMENTED |
| `dp-4-recommendation-event-adapter-v1` | ACTIVE / IMPLEMENTED / SHADOW |
| `dp-4-5-recommendation-adapter-shadow-evidence-v1` | ACTIVE / IMPLEMENTED / SHADOW |
| `recommendation-profile-input-v1` | ACTIVE / SHADOW-ONLY / CONDITIONALLY_COMPATIBLE |
| `experiment-outcome-input-v1` | ACTIVE / SHADOW-ONLY / CONDITIONALLY_COMPATIBLE |
| `data-projection-snapshot-v1` | ACTIVE / DP-5 IMPLEMENTED |
| `data-source-set-sha256-v1` | ACTIVE / DP-5 IMPLEMENTED |
| `data-projection-record-sha256-v1` | ACTIVE / DP-5 IMPLEMENTED |
| `data-projection-snapshot-sha256-v1` | ACTIVE / DP-5 IMPLEMENTED |
| `data-projection-lineage-sha256-v1` | ACTIVE / DP-5 IMPLEMENTED |
| `data-quality-policy-v1` | ACTIVE / DP-6 IMPLEMENTED |
| `data-quality-validation-input-sha256-v1` | ACTIVE / DP-6 IMPLEMENTED |
| `data-quality-check-evidence-sha256-v1` | ACTIVE / DP-6 IMPLEMENTED |
| `data-quality-metric-sha256-v1` | ACTIVE / DP-6 IMPLEMENTED |
| `data-quality-verdict-sha256-v1` | ACTIVE / DP-6 IMPLEMENTED |
| `data-quality-rebuild-comparison-sha256-v1` | ACTIVE / DP-6 IMPLEMENTED |
| `data-quality-late-arrival-observation-sha256-v1` | ACTIVE / DP-6 IMPLEMENTED |
| `data-cross-track-integration-policy-v1` | ACTIVE / DP-7 IMPLEMENTED |
| `data-cross-track-integration-run-v1` | ACTIVE / DP-7 IMPLEMENTED |
| `data-cross-track-integration-check-v1` | ACTIVE / DP-7 IMPLEMENTED |
| `data-cross-track-contract-mapping-v1` | ACTIVE / DP-7 IMPLEMENTED |
| `data-cross-track-authority-matrix-v1` | ACTIVE / DP-7 IMPLEMENTED |
| `data-cross-track-privacy-retention-matrix-v1` | ACTIVE / DP-7 IMPLEMENTED |
| `data-cross-track-integration-verdict-v1` | ACTIVE / DP-7 IMPLEMENTED |
| `integration-input-sha256-v1` | ACTIVE / DP-7 IMPLEMENTED |
| `integration-check-evidence-sha256-v1` | ACTIVE / DP-7 IMPLEMENTED |
| `integration-mapping-sha256-v1` | ACTIVE / DP-7 IMPLEMENTED |
| `integration-verdict-sha256-v1` | ACTIVE / DP-7 IMPLEMENTED |
| `cross-track-contract-matrix-sha256-v1` | ACTIVE / DP-7 IMPLEMENTED |
| `data-platform-technical-baseline-v1` | ACTIVE / TECHNICAL CLOSURE |
| `data-platform-authority-closure-v1` | ACTIVE / TECHNICAL CLOSURE |
| `data-platform-production-readiness-gaps-v1` | ACTIVE HANDOFF |
| `data-platform-production-activation-dependencies-v1` | ACTIVE HANDOFF |
| `data-platform-change-policy-v1` | ACTIVE |

## RCA-0 contracts

| Contract ID | Owner | Status |
|---|---|---|
| `recommendation-data-consumer-alignment-v1` | SC coordination | RESERVED / IMPLEMENTATION ALLOWED AFTER MERGE |
| `recommendation-profile-input-consumer-v1` | Intelligence | RESERVED / RCA-0 |
| `experiment-outcome-input-consumer-v1` | Reliability semantics | RESERVED / RCA-0 |
| `recommendation-data-consumer-fixture-v1` | Intelligence lead + Reliability P2 approval | RESERVED / RCA-0 |

These reservations authorize no DB object, source replacement, runtime activation, production write or authority transfer.

## Data DB roles

| Role | Authority | Status |
|---|---|---|
| `jc_data_event_writer` | Data event-store function boundary | ACTIVE |
| `jc_data_event_reader` | approved Data reads | ACTIVE |
| `jc_data_retry_processor` | retry procedures | ACTIVE |
| `jc_data_quarantine_reviewer` | append-only review evidence | ACTIVE |
| `jc_data_replay_executor` | replay contract only | RESERVED / NO EXECUTE GRANT |
| `jc_data_adapter_evidence_writer` | adapter evidence persistence | ACTIVE |
| `jc_data_adapter_evidence_reader` | adapter aggregate view | ACTIVE |
| `jc_data_adapter_evidence_function_owner` | NOLOGIN function owner | ACTIVE |
| `jc_data_projection_writer` | projection atomic persistence | ACTIVE |
| `jc_data_projection_reader` | projection aggregate view | ACTIVE |
| `jc_data_projection_function_owner` | NOLOGIN function owner | ACTIVE |
| `jc_data_quality_writer` | quality function boundary | ACTIVE |
| `jc_data_quality_reader` | quality aggregate view | ACTIVE |
| `jc_data_quality_function_owner` | NOLOGIN function owner | ACTIVE |
| `jc_data_integration_writer` | integration persistence boundary | ACTIVE |
| `jc_data_integration_reader` | integration aggregate view | ACTIVE |
| `jc_data_integration_function_owner` | NOLOGIN function owner | ACTIVE |

## DB sequence

| Range | Owner/purpose | Status |
|---|---|---|
| `01..26` | original canonical + Recommendation P2 | PROTECTED |
| `27..28` | Search projection / Operations eligibility and validation | PROTECTED |
| `29..31` | Data event store/idempotency/validation | ACTIVE / PROTECTED |
| `32..34` | retry/quarantine/validation | ACTIVE / PROTECTED |
| `35..37` | adapter evidence/roles/validation | ACTIVE / PROTECTED |
| `38..42` | projection/profile/outcome/persistence/validation | ACTIVE / PROTECTED |
| `43..47` | quality/lineage/persistence/rebuild/validation | ACTIVE / PROTECTED |
| `48..52` | cross-track integration evidence/persistence/validation | ACTIVE / PROTECTED |
| `53+` | unallocated | SC ASSIGNMENT REQUIRED |

RCA-0 decision: `DB_CHANGE_NOT_REQUIRED`.
