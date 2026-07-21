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
| 기준 프로젝트 | `Journey-Connect-P2-Final-Validation-Batch18` |
| 현재 기준 DB | `journey-connect-db-v2.7`, canonical SQL `01..28` |
| 원 DP-0 검증 기준 | `journey-connect-db-v2.7`, canonical SQL `01..26` |
| 작성 기준일 | 2026-07-19 |
| SC 보정일 | 2026-07-21 |

## 2. 단계 목적

DP-0의 목적은 구현 전에 Journey Connect Data Platform이 소유할 공통 행동 이벤트, 식별자, 시간, 멱등성, 중복 제거, 격리, 재처리, 데이터셋 snapshot 및 lineage 계약을 고정하는 것이다.

이번 단계는 문서와 계약만 추가한다. Java/Kotlin production code, Controller, Service, Repository, Flyway, canonical SQL, 신규 테이블은 변경하지 않는다.

## 3. 실제 확인한 현재 기준선

첨부 P2 기준 프로젝트에서 다음을 직접 확인했다.

- 추천 P0: `CLOSED`, 기존 behavior/replay/idempotency/run ownership 의미 유지
- 추천 P1: `CLOSED`
- 추천 P2 기술 구현: `CLOSED`
- P2 운영 출시 상태: 실제 CANARY 표본과 운영 승인 부재로 `HOLD`
- Java Core P1 17개, P2 23개 계약 시나리오 PASS
- Backend 전체 `83/83 PASS`
- P0/P1/P2 backend 계약 게이트 PASS
- PostgreSQL canonical SQL `01..26` fresh database PASS
- P2 assignment/exposure/evaluation/release integration PASS
- P2 runtime assignment 기본값 비활성
- pre-P2 Java core, DB `v2.6/01..24`, P0/P1 보고서 비변경 PASS
- P1은 현재 `recommendation_behavior_event`와 content facts를 직접 읽어 `recommendation_p1_profile_snapshot`을 생성함
- P2는 `recommendation_p2_experiment_assignment`, `recommendation_p2_experiment_exposure`, `recommendation_run`, `recommendation_behavior_event`, `recommendation_p1_profile_snapshot`을 결속해 `recommendation-evaluation-dataset-v1`을 생성함
- P2 metric/evaluation version은 `recommendation-metrics-v1`, `recommendation-evaluation-policy-v1`
- 상세 호환 보정은 [DP-0-P2-BASELINE-ALIGNMENT.md](DP-0-P2-BASELINE-ALIGNMENT.md)를 따른다.

위 `01..26` PASS는 원 DP-0/P2 검증 증거다. 이후 실제 저장소에 추가된 SQL 27/28은 현재 canonical sequence에 포함되지만 이 역사적 검증 문장을 소급 변경하지 않는다.

## 4. 범위

Data Platform이 소유한다.

- 신규 공통 event command와 `platform-event-v1` canonical envelope
- event family/type taxonomy와 validation
- 신규 범용 event idempotency, fingerprint, deduplication
- ingestion attempt, retry, quarantine, replay 계약
- validated behavior stream
- user behavior aggregate
- 추천·검색·실험 입력용 versioned projection 및 dataset snapshot
- data quality rule과 lineage
- P0 `recommendation_behavior_event`를 보존하는 versioned adapter/projection
- 데이터 retention/privacy 기술 정책

## 5. 비범위

Data Platform이 소유하거나 직접 구현하지 않는다.

- 추천 score, rank, diversity, exploration, policy selector
- 검색 retrieval/ranking 의미
- 콘텐츠 moderation 결정과 관리자 권한
- experiment assignment, metric 최종 판정, release/rollback 결정
- 콘텐츠 AI 분석과 여행 일정 생성
- P0 추천 테이블, policy, canonicalization, replay 의미 변경
- Kafka, Redis, Elasticsearch, 별도 warehouse 도입

## 6. 책임 경계

| 데이터/기능 | Write owner | Data Platform 권한 |
|---|---|---|
| 신규 `platform-event-v1` raw canonical event | Data | write/read |
| ingestion/retry/quarantine/replay | Data | write/read |
| versioned projection/dataset snapshot | Data | write/read |
| P0 `recommendation_behavior_event` | Intelligence | read-only adapter source |
| recommendation run/snapshot/exposure/policy | Intelligence | 승인된 projection/read contract만 소비 |
| moderation/eligibility state | Operations | read-only versioned projection 소비 |
| 기존 P2 assignment/exposure/evaluation/release runtime | 현재 recommendation P2 write path, 논리 소유 Reliability | 승인된 read contract만 소비; write 금지 |
| contract registry/DB sequence | System Coordination | 승인 요청만 가능 |

Data Platform은 다른 트랙 테이블에 직접 `INSERT`, `UPDATE`, `DELETE`하지 않는다.

## 7. 선행 계약

- [System Contract](../governance/JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md)
- [Track Governance](../governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md)
- P0 `recommendation-behavior-event-v1`
- `recommendation-p0-post-id-v1` 호환성 예외
- canonical DB `journey-connect-db-v2.7/01..28`
- 기존 `ApiResponse<T>` 및 `ApiErrorResponse`

## 8. DP-0 산출물

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
| [../proposals/DP-0-TRACK-CHANGE-PROPOSAL.md](../proposals/DP-0-TRACK-CHANGE-PROPOSAL.md) | `dp-0-track-change-proposal-v1` |

## 9. 핵심 확정 사항

1. Client Event Command와 Canonical Platform Event는 별도 타입이다.
2. client는 actor, 권한, canonical event family/type, server session binding, fingerprint를 결정하지 못한다.
3. canonical raw event와 모든 처리 이력은 append-only다.
4. 신규 platform fingerprint는 P0 fingerprint를 재사용하지 않는다.
5. P0 source event는 dual-write하지 않고 versioned adapter/projection으로 소비한다.
6. P0 source row의 `event_id`, `idempotency_key`, `payload_fingerprint`, `canonical_payload` 의미는 유지한다.
7. platform actor는 기본적으로 `subject:<opaque-id>`로 pseudonymize한다.
8. 현재 P1 CLOSED runtime의 direct source 경로는 보호한다. `recommendation-profile-input-v1`은 후속 Data shadow bridge이며 승인 전 authoritative source가 아니다.
9. 현재 P2 CLOSED runtime은 `recommendation-evaluation-dataset-v1`을 사용한다. `experiment-outcome-input-v1`은 후속 Data shadow bridge이며 기존 P2 dataset을 자동 대체하지 않는다.
10. native platform event store와 DB는 DP-2 이전에 생성하지 않는다.

## 10. 검증 항목과 결과

| 검증 | 결과 | 근거 |
|---|---|---|
| 필수 문서 존재 | 원 DP-0 PASS | SC reconciliation에서 repository file existence 재검증 |
| 문서 링크 존재 | 원 DP-0 PASS | SC workflow 재검증 |
| 계약 ID 중복 없음 | 원 DP-0 PASS | SC registry/static 재검증 |
| event type 중복 없음 | 원 DP-0 PASS | taxonomy registry |
| wire value lowercase snake_case | 원 DP-0 PASS | contract rule |
| 식별자/시간/버전 규칙 일치 | 원 DP-0 PASS | 문서 교차 검토 |
| P0 source 파일 변경 없음 | PASS | documentation-only adapter strategy |
| P0 DB SQL 변경 없음 | PASS | SC protected diff |
| P0 event 의미 변경 없음 | PASS | adapter-only 전략 |
| 다른 트랙 direct write 없음 | PASS | architecture/adapter contract |
| P2 baseline 검증 증거 확인 | PASS | Batch18 evidence |
| P1/P2 runtime source path 확인 | PASS | source inventory |
| P2 실행 테스트 재실행 | 원 DP-0 미실행 | document-only stage |
| SC reconciliation runtime test | NOT_EXECUTED | production/SQL diff 없음; static/protected diff 수행 |

## 11. 완료 조건

- [x] Data Platform 책임과 비책임 명확화
- [x] Client Command와 Canonical Event 분리
- [x] `platform-event-v1` 정의
- [x] behavior taxonomy 정의
- [x] idempotency/fingerprint 정의
- [x] retry/quarantine/replay 정의
- [x] lineage/snapshot 정의
- [x] retention/privacy 정의
- [x] P0 recommendation adapter 정의
- [x] 기존 P0 비변경 확인
- [x] 타 트랙 direct write 금지
- [x] 후속 구현 단계 분리
- [x] DP-0 handoff 작성

## 12. 후속 단계

```text
DP-1 Event Domain Types & Validation
DP-2 PostgreSQL Event Store & Idempotency
DP-3 Quarantine / Retry / Replay
DP-4 P0 Recommendation Adapter
DP-5 Versioned Projections & Dataset Snapshot
DP-6 Data Quality & Lineage
DP-7 Cross-track Integration Validation
```

DP-1은 production DB 변경 없이 Java domain type, validator, canonicalizer, fixture, contract test만 구현한다. DP-1 module/package는 `jc-data-contracts` / `com.jc.data.contract`로 예약하며 이번 reconciliation에서는 생성하지 않는다. DB 변경은 SC가 canonical DB version과 sequence를 배정한 후 DP-2에서 시작한다. 현재 canonical 기준은 `journey-connect-db-v2.7/01..28`이며 신규 번호는 그 이후로 배정받아야 한다.

## 13. 잔여 리스크

- deterministic adapter `eventId` 형식의 contract registry 승인 필요
- `subject:<opaque-id>` mapping 소유권과 삭제 처리 승인 필요
- retention 기간은 기술 기본값이며 법무·운영 검토 전 production 확정 불가
- P0 recommendation impression은 behavior row보다 일반 exposure store가 authoritative하므로 DP-4에서 cross-source dedupe contract 필요
- P2 experiment exposure는 별도 `recommendation_p2_experiment_exposure`가 authoritative하므로 일반 exposure와 실험 exposure를 이중 집계하지 않아야 함
- 현재 P1/P2 direct source 경로를 Data projection으로 전환하려면 신규 source/dataset/consumer version과 전체 회귀가 필요
- 현재 P2 물리적 write path와 Reliability 논리 소유권의 정합화는 SC 결정 필요
- P0의 `tag_click`, `crew_join`, `crew_leave`는 현재 public API producer가 확인되지 않아 adapter fixture 확보 필요
- P0 metadata는 schema-less JSON이므로 adapter allowlist와 quarantine 규칙을 DP-4에서 실제 fixture로 검증해야 함
- platform event DB DDL, grant, sequence는 아직 미배정
- 신규 Data fingerprint의 algorithm, output encoding, version ID, exact input set 및 `occurredAt/receivedAt/producerBuildId` 포함 여부는 `SC DECISION REQUIRED`; DP-1은 fingerprint 구현 전에 중단한다.

## 14. SQL 27/28 ownership

- `search_document_projection_v1`: Intelligence/Search-owned rebuildable derived projection; canonical source/Data authority 아님; Data direct write 금지
- `search_document_operational_eligibility_v1`: Operations semantic authority; missing row fail-closed; Data direct write 금지
- SQL 27/28은 current canonical `01..28`의 일부이며 DP-1이 수정하거나 재분류하지 않는다.
