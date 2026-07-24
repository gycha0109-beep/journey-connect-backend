# SC Contract, Module, Namespace and Sequence Registry

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-platform-registry-v1` |
| status | `ACTIVE / RCA0_COMPLETE / RCA1_ENTRY_AUTHORIZED` |
| authoritative main | `f802a105e46a62718616acaa7a3db6c172e7ed10` |
| RCA-0 exact-final-head | `d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d` |
| canonical SQL | `01..52` |
| unallocated SQL | `53+` |

## Modules and packages

| Module / boundary | Package root | Owner | Status |
|---|---|---|---|
| `jc-intelligence-contracts` | `com.jc.intelligence.contract` | Intelligence | ACTIVE |
| `jc-data-contracts` | `com.jc.data.contract.v1` | Data | ACTIVE / DP-1..DP-7 CLOSED |
| RCA consumer boundary | `com.jc.backend.recommendation.dataadoption` | Intelligence lead; P2 semantics Reliability | ACTIVE / PURE JAVA / NO SPRING OR DB |
| RCA-1 implementation target | same approved boundary or test-only adjacent package | joint Intelligence/Reliability | RESERVED / SEPARATE PR |

## Workstream and phase identifiers

| ID | Meaning | Status |
|---|---|---|
| `RCA` | Recommendation Consumer Adoption cross-track workstream | ACTIVE |
| `RCA-0` | Recommendation Data Consumer Contract & Fixture Alignment | COMPLETE / MERGED |
| `RCA-1` | Recommendation Data Shadow Reconciliation | ENTRY AUTHORIZED / MODEL A |
| `RCA-1B` | Non-production read-only reconciliation | DEFERRED / SEPARATE SC APPROVAL |
| `RCA-2` | Controlled Runtime Dark Read | RESERVED / NOT AUTHORIZED |
| `RP` | Reliability Platform | PROTECTED ACRONYM |

## RCA contracts

| Contract ID | Owner | Status |
|---|---|---|
| `recommendation-data-consumer-alignment-v1` | SC coordination | ACTIVE / RCA-0 |
| `recommendation-profile-input-consumer-v1` | Intelligence | ACTIVE / RCA-0 |
| `experiment-outcome-input-consumer-v1` | Reliability semantics | ACTIVE / RCA-0 |
| `recommendation-data-consumer-fixture-v1` | Intelligence lead + Reliability | ACTIVE / RCA-0 |
| `recommendation-shadow-reconciliation-v1` | SC scope; lane semantics split | RESERVED / RCA-1 IMPLEMENTATION ALLOWED AFTER SC-3 MERGE |
| `recommendation-shadow-reconciliation-evidence-v1` | SC + Reliability integrity | RESERVED / REDACTED OFFLINE EVIDENCE |
| `recommendation-shadow-reconciliation-fixture-v1` | Intelligence lead + Reliability P2 approval | RESERVED / SYNTHETIC ONLY |

These registrations authorize no DB object, production read, runtime activation or authority transfer.

## Identity schemes and RCA-1 mode

| Scheme / mode | Wire / value | Status |
|---|---|---|
| `platform_subject_v1` | `subject:<opaque-id>` | ACTIVE |
| `legacy_user_numeric_v1` | `user:<numeric-id>` | PROTECTED COMPATIBILITY |
| `RCA1_IDENTITY_MODE` | `SYNTHETIC_ONLY` | APPROVED |
| physical mapping owner | none for Model A; real owner unresolved | DEFERRED |

No identity repository, join, persistent mapping or production identity evidence is authorized.

## Exposure sources

| Source ID | Authority | Status |
|---|---|---|
| `recommendation_general_exposure_v1` | general Recommendation exposure | ACTIVE / NOT P2 DENOMINATOR |
| `recommendation_behavior_impression_v1` | behavior `impression` | ACTIVE / NOT P2 DENOMINATOR |
| `recommendation_p2_experiment_exposure_v1` | `recommendation_p2_experiment_exposure` | PROTECTED AUTHORITY |
| `search_exposure_v1` | not implemented | RESERVED |

## Data candidate contracts

| Contract ID | Status |
|---|---|
| `recommendation-profile-input-v1` | ACTIVE / SHADOW-ONLY / NON-AUTHORITATIVE |
| `experiment-outcome-input-v1` | ACTIVE / SHADOW-ONLY / NON-AUTHORITATIVE |
| `data-projection-snapshot-v1` | ACTIVE / DP-5 IMPLEMENTED |
| `data-source-set-sha256-v1` | ACTIVE / PROTECTED |
| `data-projection-record-sha256-v1` | ACTIVE / PROTECTED |
| `data-projection-snapshot-sha256-v1` | ACTIVE / PROTECTED |
| `data-projection-lineage-sha256-v1` | ACTIVE / PROTECTED |
| `data-cross-track-integration-policy-v1` | ACTIVE / DP-7 IMPLEMENTED |

## Current Recommendation authority registry

| Meaning | Authority |
|---|---|
| P1 source | `RecommendationP1ProfileSource` |
| P1 result | `recommendation_p1_profile_snapshot` |
| P2 source | `RecommendationP2ObservationSource` |
| P2 exposure | `recommendation_p2_experiment_exposure` |
| P2 dataset | `recommendation-evaluation-dataset-v1` |
| P2 metrics | `engagement_rate`, `fallback_rate` |

## Data DB roles

Existing Data roles remain ACTIVE and protected. RCA-1 allocates no role and no grant.

## DB sequence

| Range | Owner/purpose | Status |
|---|---|---|
| `01..26` | canonical + Recommendation P2 | PROTECTED |
| `27..28` | Search/Operations baseline | PROTECTED |
| `29..52` | Data Platform DP-2..DP-7 | ACTIVE / PROTECTED |
| `53+` | unallocated | SC ASSIGNMENT REQUIRED |

RCA-1 decision:

```text
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=NO
NEW_GRANT_REQUIRED=NO
```
