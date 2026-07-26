# SC Contract, Module, Namespace and Sequence Registry

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-platform-registry-v1` |
| status | `ACTIVE / RCA2_COMPLETE / SC6_STAGE1_CONDITIONAL` |
| authoritative main/work-start | `b57c344c9b4e332966fe9f6d36a5da66a5faae71` |
| RCA-2 exact-final-head | `511b19f80cdd42bb2fafde0563c7388b4f5b5f48` |
| canonical SQL | `01..52` |
| unallocated SQL | `53+` |

## Workstream registry

| ID | Meaning | Status |
|---|---|---|
| `RCA` | Recommendation Consumer Adoption cross-track workstream | ACTIVE |
| `RCA-0` | contract/fixture alignment | COMPLETE / MERGED |
| `RCA-1` | offline deterministic reconciliation | COMPLETE |
| `RCA-1B` | non-production read-only reconciliation | COMPLETE / MERGED |
| `RCA-2` | controlled runtime dark read | COMPLETE / MERGED |
| `SC-6` | nonzero non-production Stage 1 governance | CONDITIONALLY AUTHORIZED / ENABLEMENT BLOCKED |
| `RP` | Reliability Platform | PROTECTED ACRONYM |

## Retained SC-5 registry markers

```text
RCA2_ENTRY_AUTHORIZED
RCA2_EXECUTION_ENVIRONMENT=ISOLATED_NON_PRODUCTION_RUNTIME
RCA2_RUNTIME_MODEL=ASYNC_POST_RESPONSE_SHADOW
FEATURE_FLAG_DEFAULT=OFF
INITIAL_TRAFFIC_PERCENT=0
PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY
SHADOW_RESULT_AUTHORITY=NONE
SHADOW_RESULT_SERVING=FORBIDDEN
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
AUTHORITY_TRANSFER=FORBIDDEN
```

## Contract registry

| Contract ID | Owner | Status |
|---|---|---|
| `recommendation-shadow-reconciliation-v1` | lane owners | ACTIVE / HISTORICAL |
| `recommendation-runtime-dark-read-boundary-v1` | System Coordination | ACTIVE / RCA-2 |
| `recommendation-runtime-dark-read-query-registry-v1` | Data + lane owners | CONTRACT-ONLY |
| `rca2-nonproduction-stage1-traffic-v1` | System Coordination | ACTIVE / GOVERNANCE ONLY |
| `rca2-nonproduction-stage1-cohort-v1` | Privacy/Security + Operations | REQUIRED / NOT IMPLEMENTED |
| `rca2-nonproduction-stage1-observation-v1` | Reliability + Operations | REQUIRED / NOT EXECUTED |
| `rca2-nonproduction-stage1-rollback-v1` | Operations | REQUIRED / NOT DRILLED |

## Stage 1 registry

```text
TARGET_ENVIRONMENT=ISOLATED_NON_PRODUCTION_RUNTIME
TARGET_TRAFFIC_STAGE=STAGE_1
TARGET_TRAFFIC_PERCENT=1
CURRENT_TRAFFIC_PERCENT=0
TRAFFIC_ENABLEMENT=BLOCKED_PENDING_ALL_CONDITIONS
FEATURE_FLAG_DEFAULT=OFF
MANUAL_ENABLEMENT_REQUIRED=YES
AUTOMATIC_ROLLOUT=FORBIDDEN
COHORT_SELECTION=STABLE_HASH_PERCENTAGE
COHORT_KEY=HASHED_NONPRODUCTION_TEST_SUBJECT_REF
RAW_IDENTITY_COHORT_KEY=FORBIDDEN
PRODUCTION_TRAFFIC_PERCENT=0
```

## Owner registry

- `endpoint` owner: **Operations** (`OPERATIONS`).
- `credential` owner: **Operations** (`OPERATIONS`).
- `identity_allowlist` owner: **Privacy/Security** (`PRIVACY_SECURITY`).
- `alert` owner: **Operations** (`OPERATIONS`).
- `rollback_execution` owner: **Operations** (`OPERATIONS`).

## Authority registry

Current sources remain `RecommendationP1ProfileSource` and `RecommendationP2ObservationSource`. `PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY`, `SHADOW_RESULT_AUTHORITY=NONE`, `SHADOW_RESULT_SERVING=FORBIDDEN`, `AUTHORITY_TRANSFER=FORBIDDEN`.

## DB sequence

`01..52` is protected. `53+` is absent and unallocated. `DB_CHANGE=NONE`; `SQL_ALLOCATION=NOT_REQUIRED`.
