# IP-0 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-0-handoff-v1` |
| 상태 | `COMPLETE` |
| 소유 트랙 | Intelligence Platform |
| 다음 후보 단계 | `IP-1 Common Contract Types, Validation & Recommendation Compatibility Adapter` |

## 1. 완료된 결정

1. Intelligence Platform은 Recommendation, Search, Content Analysis, Trip Planning 네 도메인으로 분리한다.
2. 공통화 대상은 run/snapshot/provenance/version/replay evidence이며 도메인 score·metric·exposure 의미는 통합하지 않는다.
3. `IntelligenceRun V1`은 immutable terminal evidence로 정의한다.
4. model 기반 처리는 `ModelInferenceRecord`와 exact/semantic/evidence replay 등급을 사용한다.
5. feature는 namespace와 `source_fact`, `observed_behavior`, `derived_aggregate`, `model_inference`, `operator_override` authority class를 가진다.
6. 기존 추천 P0/P1/P2는 첫 보호 consumer와 reference implementation이다.
7. 기존 추천은 공통 계약에 맞춰 재설계하지 않고 compatibility adapter로 연결한다.
8. DP-0의 `recommendation-profile-input-v1`, `experiment-outcome-input-v1`은 shadow-only다.
9. P2 experiment exposure authority와 metric 의미를 유지한다.
10. P2 physical writer와 Reliability semantic owner를 분리해 기록한다.

## 2. 보호된 기준선

- 추천 P1: `CLOSED`
- 추천 P2 기술 구현: `CLOSED`
- P2 운영 출시: `HOLD`
- canonical DB: `journey-connect-db-v2.7/01..26`
- Backend: `83/83 PASS`
- P1 Core: `17/17 PASS`
- P2 Core: `23/23 PASS`
- P2 assignment runtime: 기본 비활성
- P2 assignment/exposure/evaluation/release integration: PASS
- pre-P2 Java core, DB v2.6, P0/P1 reports: exact unchanged evidence 확인

IP-0은 위 기준선의 production code, SQL, canonical bytes, hash를 변경하지 않았다.

### 기준선 검증 증거 위치

Batch18 기준선은 다음 실제 파일에서 확인했다.

- `P2_5_TOTAL_REVIEW_FINAL_VALIDATION_REPORT.md`
- `verification/P2_TEST_SUMMARY.txt`
- `verification/P2_BASELINE_INTEGRITY.txt`
- `verification/P2_FINAL_SOURCE_SHA256.txt`
- `P1_5_FINAL_VALIDATION_REPORT.md`
- `verification/P1_TEST_SUMMARY.txt`
- `database/journey-connect-db-v2.7/25_recommendation_p2_evaluation_release.sql`
- `database/journey-connect-db-v2.7/26_recommendation_p2_evaluation_release_smoke_test.sql`
- `jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java`
- `jc-recommendation-core/src/main/java/com/jc/recommendation/p2/P2Policies.java`

첨부로 별도 제공된 System Contract, Track Governance, 추천 설계 명세, 알고리즘 개발 계약은 Batch18 ZIP 내부 복사본과 SHA-256이 동일했다. DP-0 Foundation과 P2 Alignment 문서도 DP-0 ZIP 내부 문서와 SHA-256이 동일했다.

## 3. 생성된 계약

| 계약 | ID |
|---|---|
| IP-0 Foundation | `jc-intelligence-platform-contract-foundation-v1` |
| Platform Architecture | `intelligence-platform-architecture-v1` |
| Common Contracts | `intelligence-common-contracts-v1` |
| Domain Contracts | `intelligence-domain-contracts-v1` |
| P2 Compatibility & DP-0 Integration | `ip-0-p2-compatibility-dp0-integration-v1` |
| Track Change Proposal | `ip-0-track-change-proposal-v1` |
| Handoff | `ip-0-handoff-v1` |

예약 제안 common schema IDs:

- `intelligence-run-v1`
- `intelligence-input-snapshot-v1`
- `intelligence-candidate-snapshot-v1`
- `intelligence-output-snapshot-v1`
- `intelligence-feature-value-v1`
- `intelligence-explanation-v1`
- `model-inference-record-v1`

## 4. 다른 트랙에 제공하는 계약

### Data

- Intelligence가 소비할 dataset/source authority 표
- P1/P2 shadow bridge 전환 게이트
- feature consumption semantics와 Data facts의 경계
- identity mapping 없이는 P2 join 금지

### Operations

- run/model/fallback/safety/replay 관측 최소 필드
- past run/snapshot 불변 원칙
- operational control read port 경계
- user/operator/debug explanation 분리

### Reliability

- experiment/assignment/exposure hook
- domain run/output/fallback evidence
- metricDefinitionVersion owner 경계
- P2 semantic ownership과 current physical path 구분

### System Coordination

- common contract ID 예약 후보
- identity scheme registry 후보
- exposure source registry 후보
- System Contract P2 baseline amendment 제안

## 5. 다른 트랙에서 받아야 하는 계약

### Data에서 필요

- approved dataset registry implementation contract
- P1/P2 shadow reconciliation report format
- Data snapshot read port와 access control
- restricted identity mapping owner 결정

### Operations에서 필요

- visibility/eligibility projection contract
- stop/hold/override control state contract
- model/policy operation audit contract
- admin authorization boundary

### Reliability에서 필요

- common experiment assignment read port
- search/content/planner metric definition 절차
- release evidence ingestion contract
- exposure/downstream effect authority 승인 방식

### System Coordination에서 필요

- IP common contract IDs 승인
- System Contract P2 amendment
- DB/module/package namespace 배정
- cross-track breaking change 판정

## 6. 미결정 사항

1. identity mapping의 owner, retention, deletion, audit
2. P2 physical package/DB role의 장기 migration 시점
3. 공통 contract type의 코드 위치
4. search exposure authoritative source의 물리 모델
5. provider snapshot retention과 model prompt 보존 범위
6. place facts provider authority/freshness 계약
7. Operations stop/rollback port의 최소 command set

## 7. 총괄방 승인 필요 사항

1. `ip-0-track-change-proposal-v1` 승인 또는 부분 승인
2. `intelligence-*-v1` contract ID registry 예약
3. `platform_subject_v1`과 `legacy_user_numeric_v1` identity scheme 등록
4. P2 physical/semantic ownership matrix 승인
5. IP-1 코드 모듈/package 위치 승인
6. IP-1이 production DB 없이 진행 가능한지 확인

## 8. IP-1 진입 조건

### 필수

- [ ] IP-0 문서 패키지 총괄 승인
- [ ] common contract ID 예약 또는 임시 namespace 승인
- [ ] 기존 recommendation source를 수정하지 않는 adapter-only 범위 승인
- [ ] production DB/SQL 비변경 범위 확인
- [ ] identity mapping은 stub/reference만 사용하고 실제 join 미구현 확인
- [ ] P2 source authority fixture 확보
- [ ] current recommendation run/snapshot/P1/P2 fixture 확보

### HOLD 조건

- System Contract 기준선 충돌을 무시한 채 runtime type을 고정하려는 경우
- 기존 recommendation table/schema를 common schema로 rename하려는 경우
- Data shadow projection을 즉시 authoritative source로 전환하려는 경우
- P2 metric/exposure 의미를 새 common event에 맞춰 변경하려는 경우
- 공통 contract를 명분으로 search/content/planner 구현까지 한 단계에 묶는 경우

## 9. 권장 IP-1 범위

```text
IP-1
Common Contract Types, Validation & Recommendation Compatibility Adapter
```

권장 작업:

1. production DB 변경 없는 Java contract module 또는 승인된 package 생성
2. common ID/time/version/status validator
3. canonical DTO/record와 JSON fixture
4. recommendation current rows/objects → common contract read-only adapter
5. P0/P1/P2 compatibility classification contract test
6. source authority fixture와 identity scheme fixture
7. exact/semantic/evidence replay validation interface
8. existing recommendation 전체 회귀

IP-1에서 하지 않을 것:

- search engine 구현
- model/LLM 호출
- content analysis production 구현
- trip planner 구현
- Data runtime cutover
- P2 ownership migration
- DB migration

## 10. IP-1 진입 판정

`HOLD — SYSTEM COORDINATION APPROVAL PENDING`

기술·문서 기준선은 충족했다. 다만 common contract ID와 System Contract P2 alignment가 공통 트랙에 영향을 주므로 총괄 승인 전 production-facing IP-1 구현은 시작하지 않는다. 제안이 승인되면 별도 재설계 없이 `READY`로 전환할 수 있다.
