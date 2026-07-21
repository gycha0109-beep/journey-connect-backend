# DP-0 Data Platform Contract Foundation

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `DP-0` |
| 단계명 | `Data Platform Contract Foundation` |
| 계약 ID | `jc-data-platform-contract-foundation-v1` |
| 상태 | `COMPLETE / P2_BASELINE_ALIGNED / IMPLEMENTATION_NOT_STARTED` |
| 소유 트랙 | Data Platform |
| 기준 시스템 계약 | `jc-system-contract-v1` |
| 기준 추천 상태 | `P2 TECHNICAL CLOSED / PRODUCTION HOLD` |
| 기준 DB | `journey-connect-db-v2.7/01..28` |
| 보정일 | 2026-07-21 |

## 2. 목적

DP-0은 구현 전에 공통 행동 이벤트, 식별자, 시간, 멱등성, 중복 제거, 격리, 재처리, snapshot, lineage와 privacy 기술 계약을 고정한다. Java/Kotlin production code, Controller, Service, Repository, Flyway, canonical SQL과 신규 table은 변경하지 않는다.

## 3. 보호 기준선

- P0/P1: `CLOSED`
- P2 technical: `CLOSED`
- P2 production: `HOLD`
- P1 current source: `recommendation_behavior_event` + content facts + explicit preferences
- P2 exposure authority: `recommendation_p2_experiment_exposure`
- P2 dataset: `recommendation-evaluation-dataset-v1`
- P2 metric/evaluation: `recommendation-metrics-v1`, `recommendation-evaluation-policy-v1`
- canonical DB: `journey-connect-db-v2.7/01..28`

SQL 27/28은 Search/Operations 소유의 보호된 canonical sequence다. Data migration이 아니며 DP-1은 이를 수정하지 않는다.

## 4. 범위

Data가 소유한다.

- `ClientEventCommandV1`과 `PlatformEventEnvelopeV1`
- event family/type taxonomy와 validation
- 신규 Data idempotency/fingerprint/deduplication 계약
- ingestion attempt, retry, quarantine, replay
- validated behavior stream와 aggregate
- versioned projection/dataset snapshot
- data quality와 lineage
- P0 source를 보존하는 read-only adapter/projection
- retention/privacy 기술 정책

## 5. 비범위

- recommendation score/rank/diversity/exploration
- Search retrieval/ranking
- moderation/eligibility decision
- experiment assignment/metric/release decision
- production runtime/DB migration
- P0/P1/P2 source cutover
- Kafka, 별도 stream processor 또는 warehouse의 선행 도입

## 6. 책임 경계

| 데이터/기능 | Write owner | Data 권한 |
|---|---|---|
| future `platform-event-v1` raw event | Data | write/read after DP-2 |
| Data attempt/replay/projection/snapshot | Data | write/read |
| `recommendation_behavior_event` | Intelligence | read-only adapter source |
| recommendation run/profile/general exposure | Intelligence | approved read only |
| P2 assignment/exposure/evaluation/release | Reliability semantic/current protected physical path | approved read only; write 금지 |
| visibility/operational eligibility | Operations | approved read only; write 금지 |
| Search projection | Intelligence/Search | direct write 금지 |
| registry/DB sequence | SC | proposal only |

타 트랙 table direct `INSERT/UPDATE/DELETE`를 금지한다.

## 7. 선행 계약

- [System Contract](../governance/JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md)
- [Track Governance](../governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md)
- `recommendation-behavior-event-v1`
- `recommendation-p0-post-id-v1`
- canonical DB `journey-connect-db-v2.7/01..28`

## 8. 산출물

| 문서 | 계약 ID |
|---|---|
| [DATA-PLATFORM-ARCHITECTURE-V1.md](DATA-PLATFORM-ARCHITECTURE-V1.md) | `data-platform-architecture-v1` |
| [PLATFORM-EVENT-CONTRACT-V1.md](PLATFORM-EVENT-CONTRACT-V1.md) | `platform-event-v1` |
| [BEHAVIOR-EVENT-TAXONOMY-V1.md](BEHAVIOR-EVENT-TAXONOMY-V1.md) | `behavior-event-taxonomy-v1` |
| [EVENT-IDEMPOTENCY-AND-FINGERPRINT-V1.md](EVENT-IDEMPOTENCY-AND-FINGERPRINT-V1.md) | `event-idempotency-fingerprint-v1` |
| [EVENT-RETRY-QUARANTINE-REPLAY-V1.md](EVENT-RETRY-QUARANTINE-REPLAY-V1.md) | `event-retry-quarantine-replay-v1` |
| [DATA-LINEAGE-AND-SNAPSHOT-V1.md](DATA-LINEAGE-AND-SNAPSHOT-V1.md) | `data-lineage-snapshot-v1` |
| [DATA-RETENTION-AND-PRIVACY-V1.md](DATA-RETENTION-AND-PRIVACY-V1.md) | `data-retention-privacy-v1` |
| [P0-RECOMMENDATION-EVENT-ADAPTER-V1.md](P0-RECOMMENDATION-EVENT-ADAPTER-V1.md) | `p0-recommendation-event-adapter-v1` |
| [DP-0-HANDOFF.md](DP-0-HANDOFF.md) | `dp-0-handoff-v1` |
| [DP-0-P2-BASELINE-ALIGNMENT.md](DP-0-P2-BASELINE-ALIGNMENT.md) | `dp-0-p2-baseline-alignment-v1` |
| [DP-0-TRACK-CHANGE-PROPOSAL.md](../proposals/DP-0-TRACK-CHANGE-PROPOSAL.md) | `dp-0-track-change-proposal-v1` |

## 9. 확정 사항

1. Client command와 canonical event는 별도 타입이다.
2. client는 canonical ID, actor, permission, family/type, session binding, receive time, producer, fingerprint를 결정하지 못한다.
3. raw event와 attempt/evidence는 append-only다.
4. 신규 Data canonicalization/fingerprint는 기존 P0 것을 재사용하거나 변경하지 않는다.
5. P0 source는 dual-write하지 않고 adapter/projection으로 소비한다.
6. `subject:<opaque-id>`와 `user:<numeric-id>`는 자동 결합하지 않는다.
7. `recommendation-profile-input-v1`과 `experiment-outcome-input-v1`은 shadow-only다.
8. native Data event store는 DP-2 이전에 생성하지 않는다.

## 10. DP-1 reservation

- module: `jc-data-contracts`
- package: `com.jc.data.contract`
- status: `RESERVED / NOT IMPLEMENTED`

DP-1은 DB 없는 Java contract type, validator, canonicalizer interface, fixture와 contract test만 구현한다. 실제 module/source 생성은 이번 reconciliation 범위가 아니다.

## 11. DP 단계

```text
DP-1 Event Domain Types & Validation
DP-2 PostgreSQL Event Store & Idempotency
DP-3 Quarantine / Retry / Replay
DP-4 P0 Recommendation Adapter
DP-5 Versioned Projections & Dataset Snapshot
DP-6 Data Quality & Lineage
DP-7 Cross-track Integration Validation
```

DP-2 이후 신규 SQL은 SC가 `28` 이후로 별도 배정한다.

## 12. 잔여 결정/리스크

- 신규 Data fingerprint algorithm, output encoding, version ID, exact inclusion set과 timestamp/build 포함 여부는 `SC DECISION REQUIRED`다.
- identity mapping physical owner/deletion policy는 미결정이다.
- retention 기간은 기술 제안이며 법무/운영 승인이 필요하다.
- general recommendation exposure, behavior impression, P2 experiment exposure를 이중 집계하지 않는다.
- P1/P2 shadow cutover는 새 source/schema/consumer version, reconciliation, replay, 전체 회귀와 SC/owner 승인이 필요하다.
