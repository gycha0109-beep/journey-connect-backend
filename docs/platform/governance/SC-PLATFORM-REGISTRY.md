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
| `jc-data-contracts` | `com.jc.data.contract` | Data | RESERVED / NOT IMPLEMENTED |

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
| `data-platform-architecture-v1` | RECOVERED |
| `platform-event-v1` | RECOVERED |
| `event-idempotency-fingerprint-v1` | RECOVERED / FINGERPRINT BLOCKED |
| `data-lineage-snapshot-v1` | RECOVERED |
| `dp-0-handoff-v1` | RECOVERED |
| `dp-0-track-change-proposal-v1` | RECOVERED |

## DB sequence

| Range | Owner/purpose | Status |
|---|---|---|
| `01..26` | existing canonical + Recommendation P2 | PROTECTED |
| `27` | Search projection / Operations eligibility | PROTECTED |
| `28` | SQL 27 smoke test | PROTECTED |
| `29+` | unallocated | SC ASSIGNMENT REQUIRED |
