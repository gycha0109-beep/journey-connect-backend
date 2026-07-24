# SC Contract, Module, Namespace and Sequence Registry

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-platform-registry-v1` |
| status | `ACTIVE / RCA1_COMPLETE / RCA1B_ENTRY_AUTHORIZED` |
| authoritative main | `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4` |
| RCA-1 exact-final-head | `38896b2a37180633870282e9d9e305d9c9fbbf8a` |
| canonical SQL | `01..52` |
| unallocated SQL | `53+` |

Historical status marker `ACTIVE / RCA0_COMPLETE / RCA1_ENTRY_AUTHORIZED` remains preserved by the completed phases.

## Modules and boundaries

| Boundary | Package/object family | Owner | Status |
|---|---|---|---|
| RCA-0 consumer boundary | `com.jc.backend.recommendation.dataadoption` | Intelligence lead / Reliability P2 semantics | ACTIVE / PURE JAVA |
| RCA-1 comparator boundary | `.dataadoption.reconciliation` | joint Intelligence/Reliability | ACTIVE / MODEL A COMPLETE |
| RCA-1B test implementation | test-only adjacent package/resources | joint lane owners + Operations | RESERVED / SEPARATE PR |
| RCA-1B DB environment | CI ephemeral PostgreSQL | Operations | ENTRY AUTHORIZED / NOT IMPLEMENTED |

## Workstream and phase identifiers

| ID | Meaning | Status |
|---|---|---|
| `RCA` | Recommendation Consumer Adoption cross-track workstream | ACTIVE |
| `RCA-0` | contract/fixture alignment | COMPLETE / MERGED |
| `RCA-1` | offline deterministic reconciliation | COMPLETE / MODEL A |
| `RCA-1B` | non-production read-only DB reconciliation | ENTRY AUTHORIZED |
| `RCA-2` | controlled runtime dark read | RESERVED / NOT AUTHORIZED |
| `RP` | Reliability Platform | PROTECTED ACRONYM |

`RP` is reserved for Reliability Platform and is not a Recommendation workstream name.

## RCA contracts

| Contract ID | Owner | Status |
|---|---|---|
| `recommendation-data-consumer-alignment-v1` | SC coordination | ACTIVE / RCA-0 |
| `recommendation-profile-input-consumer-v1` | Intelligence | ACTIVE / RCA-0 |
| `experiment-outcome-input-consumer-v1` | Reliability semantics | ACTIVE / RCA-0 |
| `recommendation-data-consumer-fixture-v1` | joint | ACTIVE / RCA-0 |
| `recommendation-shadow-reconciliation-v1` | lane split | ACTIVE / RCA-1 |
| `recommendation-shadow-reconciliation-evidence-v1` | Reliability integrity | ACTIVE / RCA-1 |
| `recommendation-shadow-reconciliation-fixture-v1` | joint | ACTIVE / RCA-1 |

No additional contract ID is allocated by SC-4. RCA-1B reuses the RCA-1 taxonomy/evidence contract and registers query IDs in governance evidence.

## RCA-1B query registry

| Query ID | Lane | Status |
|---|---|---|
| `P1_AUTHORITATIVE_REFERENCE_V1` | P1 | APPROVED / NOT IMPLEMENTED |
| `P1_DATA_CANDIDATE_V1` | P1 | APPROVED / NOT IMPLEMENTED |
| `P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1` | P2 | APPROVED / NOT IMPLEMENTED |
| `P2_DATA_CANDIDATE_V1` | P2 | APPROVED / NOT IMPLEMENTED |
| `SOURCE_CHECKPOINT_V1` | common | APPROVED / NOT IMPLEMENTED |
| `SOURCE_LINEAGE_V1` | common | APPROVED / NOT IMPLEMENTED |
| `BOUNDED_ROW_COUNT_V1` | common | APPROVED / NOT IMPLEMENTED |

Every query requires a version-controlled fingerprint, prepared parameters, deterministic ordering and a finite bound.

## Identity modes

| Mode | Status |
|---|---|
| `SYNTHETIC_ONLY` | APPROVED / RCA-1B |
| pseudonymized non-production binding | DEFERRED |
| actual identity mapping | BLOCKED |

No identity repository, persistent mapping or actual-user evidence is authorized.

## Execution environment registry

```text
RCA1B_EXECUTION_ENVIRONMENT=CI_EPHEMERAL_POSTGRESQL
POSTGRESQL_VERSION_MATRIX=15,18
RCA1B_DATASET_MODE=DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE
TRANSACTION_READ_ONLY=REQUIRED
PRODUCTION_DB=FORBIDDEN
```

PostGIS/extensions are not required. Environment B is deferred; production replica/derived environments are blocked.

## Current Recommendation authority

| Meaning | Authority |
|---|---|
| P1 source | `RecommendationP1ProfileSource` |
| P1 result | `recommendation_p1_profile_snapshot` |
| P2 source | `RecommendationP2ObservationSource` |
| P2 exposure | `recommendation_p2_experiment_exposure` |
| P2 dataset | `recommendation-evaluation-dataset-v1` |
| P2 metrics | `engagement_rate`, `fallback_rate` |

`CURRENT_P1_P2_AUTHORITY_UNCHANGED`.

## Role and grant registry

| Item | Status |
|---|---|
| `rca1b_readonly` | RESERVED / EPHEMERAL TEST ONLY |
| persistent RCA role | NOT ALLOCATED |
| schema usage | EXPLICIT ALLOWLIST ONLY |
| table select | EXPLICIT ALLOWLIST ONLY |
| write grant | FORBIDDEN |
| `BYPASSRLS` | FORBIDDEN |
| owner/superuser use | FORBIDDEN |

## DB sequence

| Range | Owner/purpose | Status |
|---|---|---|
| `01..26` | canonical + Recommendation P2 | PROTECTED |
| `27..28` | Search/Operations baseline | PROTECTED |
| `29..52` | Data Platform | PROTECTED |
| `53+` | unallocated | SC ASSIGNMENT REQUIRED |

```text
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=YES_EPHEMERAL_TEST_ONLY
NEW_GRANT_REQUIRED=YES_EPHEMERAL_TEST_ONLY
TEST_FIXTURE_SQL_REQUIRED=YES_NONCANONICAL_TEST_ONLY
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
```
