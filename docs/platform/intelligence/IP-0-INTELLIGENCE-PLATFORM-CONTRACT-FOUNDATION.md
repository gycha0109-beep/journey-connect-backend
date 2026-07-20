# IP-0 Intelligence Platform Contract Foundation

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `IP-0` |
| 계약 ID | `jc-intelligence-platform-contract-foundation-v1` |
| 상태 | `COMPLETE / CONTRACT_ONLY / IMPLEMENTATION_NOT_STARTED` |
| 소유 트랙 | Intelligence Platform |
| 기준 시스템 계약 | `jc-system-contract-v1` + P2 alignment 필요 |
| 기준 Data 계약 | `jc-data-platform-contract-foundation-v1` |
| 기준 추천 | `P1 CLOSED / P2 TECHNICAL CLOSED / PRODUCTION HOLD` |
| 기준 DB | `journey-connect-db-v2.7`, SQL `01..26` |

## 2. 목적

IP-0은 Journey Connect의 지능형 기능을 하나의 거대 엔진으로 합치는 작업이 아니다. 다음 기능이 독립적인 도메인 의미를 유지하면서 공통 실행 증거와 version 규칙을 공유하도록 계약을 고정한다.

- Recommendation Intelligence
- Search Intelligence
- Content Intelligence
- Trip Planning Intelligence

공통 계약은 재사용을 위한 최소 공통분모다. 도메인별 정책, metric, candidate 의미, exposure 의미를 제거하거나 통합하지 않는다.

## 3. 범위

IP-0이 확정하는 범위는 다음과 같다.

1. Intelligence Platform의 책임과 비책임
2. runtime plane과 control plane의 논리 경계
3. 공통 `IntelligenceRun`과 immutable snapshot 계약
4. provenance·version·source authority·replay 계약
5. feature vocabulary namespace 및 authority 분류
6. user/operator/evaluation/debug explanation 분리
7. model/prompt 기반 inference evidence 계약
8. 네 Intelligence 도메인의 입력·출력·failure/fallback 경계
9. 기존 추천 P0/P1/P2의 compatibility adapter 방향
10. DP-0 shadow projection과 consumer 전환 게이트
11. Operations 관측 최소 신호
12. Reliability experiment/evaluation hook 경계
13. IP-1 진입 조건과 권장 범위

## 4. 비범위

IP-0에서는 다음을 구현하거나 소유하지 않는다.

- production Java/Kotlin/SQL 변경
- DB migration 또는 canonical SQL 번호 배정
- 추천 P0/P1/P2 리팩터링
- canonical event ingestion, retry, quarantine, replay infrastructure
- Data-owned dataset 물리 저장 정책
- 검색 엔진·vector DB·embedding provider 도입
- production model 또는 LLM 호출
- trip planner runtime 구현
- 관리자 UI, 운영자 권한, 승인 workflow
- experiment assignment·metric·release 최종 정책 재정의
- SLO, alert threshold, incident escalation 정책
- 사용자 신고·제재 처리
- 기존 P2 row, dataset bytes, content hash 재작성

## 5. 보호 기준선

### 5.1 검증된 상태

| 기준 | 보호 상태 |
|---|---|
| 추천 P1 | `CLOSED` |
| 추천 P2 기술 구현 | `CLOSED` |
| P2 운영 출시 | `HOLD` — 실제 CANARY 표본과 운영 승인 부재 |
| Java Core | P1 17개, P2 23개 계약 시나리오 PASS |
| Backend | 83/83 PASS |
| DB | `journey-connect-db-v2.7/01..26` fresh database PASS |
| P2 통합 | assignment/exposure/evaluation/release PASS |
| P2 runtime assignment | 기본 비활성 |

### 5.2 변경 금지

IP-0 및 IP-0을 근거로 한 후속 설계는 승인된 새 version 없이는 다음을 변경할 수 없다.

- P0 deterministic recommendation core와 기존 wire value
- P1 profile snapshot 생성·정책 선택 경로
- P2 assignment·experiment exposure·dataset·evaluation·release 체인
- 기존 recommendation snapshot, replay, policy version 의미
- canonical SQL `01..26`
- 기존 P2 row와 `recommendation-evaluation-dataset-v1` hash material
- `recommendation_p2_experiment_exposure`의 실험 분모 authority
- `recommendation-metrics-v1`의 `engagement_rate`, `fallback_rate` 의미
- Batch18 검증 결과와 보호 해시

## 6. 플랫폼 책임

### 6.1 Intelligence가 소유하는 의미

- 후보 생성·검색 retrieval·ranking·reranking 의미
- 추천 score/diversity/exploration/fallback 의미
- 콘텐츠 분석 feature/classification 의미
- 일정 생성과 constraint evaluation 의미
- feature consumption semantics와 feature vocabulary
- policy/model/prompt version 선택
- run/output/explanation provenance 의미
- 도메인별 failure/fallback code
- 공통 계약을 도메인 객체에 적용하는 adapter

### 6.2 Intelligence가 소유하지 않는 의미

| 영역 | semantic owner |
|---|---|
| canonical raw event와 ingestion lifecycle | Data |
| dataset lineage·retention·privacy 기술 정책 | Data |
| 운영자 승인·중단·rollback interface와 audit action | Operations |
| experiment definition·assignment policy·metric governance·release gate | Reliability |
| 공통 contract registry·DB sequence·breaking change 승인 | System Coordination |

현재 P2 assignment/evaluation/release의 물리적 구현이 recommendation package와 recommendation DB role에 존재하더라도 semantic owner는 Reliability다. 물리 이전은 IP-0 범위가 아니다.

## 7. 핵심 결정

### D1. 기존 추천은 reference implementation이며 첫 보호 consumer다

기존 추천을 공통 객체에 맞춰 재작성하지 않는다. 공통 계약과의 관계는 `exact compatible`, `adapter compatible`, `future version migration required`, `intentionally domain-specific` 중 하나로 명시한다.

### D2. 공통 계약은 의미 통합이 아니라 evidence 통합이다

Recommendation, Search, Content Analysis, Trip Planning은 `run`, `snapshot`, `version`, `provenance`, `replay class`를 공유한다. score, confidence, relevance, constraint, exposure는 도메인별 의미를 유지한다.

### D3. 공통 `IntelligenceRun` V1은 immutable terminal evidence다

V1 run은 실행 종료 시점의 증거 레코드다. 비동기 queued/running 상태를 같은 row에서 갱신하는 계약이 아니다. 필요하면 도메인별 append-only state transition 또는 attempt record를 사용한다.

### D4. Data projection은 shadow-only다

`recommendation-profile-input-v1`과 `experiment-outcome-input-v1`은 현재 P1/P2 authoritative source가 아니다. reconciliation, 회귀, 승인 전 runtime cutover를 금지한다.

### D5. exposure family를 합치지 않는다

- 일반 추천 exposure
- recommendation behavior `impression`
- P2 experiment exposure
- future search exposure

각 source의 authority와 metric 목적을 분리한다. 공통 이름이나 공통 테이블을 이유로 합산하지 않는다.

### D6. identity는 강제 통합하지 않는다

Data의 `subject:<opaque-id>`와 현재 P2의 `user:<numeric-id>`는 동일 문자열 공간이 아니다. restricted mapping port와 audit가 없는 join을 금지한다.

### D7. model output은 사실이 아니다

모델·LLM·embedding·reranker 결과는 `model_inference` 또는 `derived_inference` provenance를 가진 파생 증거다. 사용자 원문, 장소 원천 사실, 운영자 확정값을 덮어쓰지 않는다.

### D8. replay 보장을 세 등급으로 분리한다

- `exact_replay`
- `semantic_replay`
- `evidence_replay`

비결정적 모델에 bit-level 재현을 거짓으로 약속하지 않는다.

## 8. 설계 원칙

### 8.1 Deterministic boundary

다음은 가능한 한 결정론적으로 고정한다.

- 입력 정규화
- feature 계산과 bounded aggregate 소비
- eligibility와 규칙 판정
- candidate stable ordering과 tie-break
- snapshot canonicalization과 hash
- deterministic assignment/seed
- constraint check
- adapter mapping

모델 호출은 입력 snapshot, model/prompt/tool version, parameters, safety version, producer build를 결속한다.

### 8.2 Immutable versioning

다음 version은 의미 변경 시 반드시 새 값을 사용한다.

- `schemaVersion`
- `policyVersion`
- `featureDefinitionVersion`
- `modelVersion`
- `promptVersion`
- `metricDefinitionVersion`
- `canonicalizationVersion`
- `producerBuildId`

`latest`, `current`, `default`를 영속 version ID로 사용하지 않는다.

### 8.3 Append-only evidence

run, input/output/candidate/explanation snapshot, inference record, exposure link, evaluation evidence는 append-only가 기본이다. 정정은 superseding record 또는 새 version으로 남긴다.

### 8.4 Source authority

shadow projection, cache, search index, model inference는 원천 authority를 자동 획득하지 않는다. 각 소비자는 authority와 snapshot version을 명시한다.

### 8.5 No hidden coupling

도메인 간 내부 JPA entity, table, repository, service class를 공유하지 않는다. 공유는 versioned contract와 adapter로만 허용한다.

## 9. 현재 상태와 목표 상태

| 영역 | 현재 상태 | IP 목표 상태 |
|---|---|---|
| Recommendation | P0/P1/P2 구현·검증 완료, 운영 P2 HOLD | 보호 adapter를 통해 common evidence 계약에 연결 가능 |
| Search | 별도 Intelligence runtime 미구현 | SearchRequest/Retrieval/Ranking/Result/Exposure 계약 고정 |
| Content Analysis | 제품 요구만 존재, production runtime 미구현 | 원문과 분리된 versioned analysis result 계약 고정 |
| Trip Planning | 제품 요구만 존재, production runtime 미구현 | 구조화 itinerary + constraint + provenance 계약 고정 |
| Data | DP-0 계약 완료, 구현 미시작 | shadow projection과 authority 경계 명시 |
| Reliability | P2 물리 구현은 recommendation 내부 | 공통 hook과 semantic ownership 명시, 이전은 보류 |
| Operations | 설계 트랙 | 최소 관측·중단·audit 신호만 정의 |

## 10. 미결정 사항

1. `user:<id>` ↔ `subject:<opaque>` restricted identity mapping의 물리 owner와 삭제 처리
2. 현재 P2 physical write path를 언제 Reliability port로 분리할지
3. 공통 contract registry를 코드, 문서, DB 중 어디에 먼저 구현할지
4. model provider별 retention·zero-data 정책 승인 기준
5. search exposure와 recommendation exposure의 공통 envelope 사용 범위
6. place facts의 authoritative provider와 freshness SLA
7. Operations가 model/policy 실행을 중단할 control port의 최소 인터페이스

## 11. 완료 기준

- [x] Intelligence 책임과 비책임 명시
- [x] 네 도메인 경계 분리
- [x] common run/snapshot/provenance/version 계약 정의
- [x] feature namespace와 authority class 정의
- [x] replay 등급 정의
- [x] 기존 P1/P2 보호 경계 정의
- [x] DP-0 shadow-only 원칙 반영
- [x] P2 exposure authority와 metric 의미 보존
- [x] identity 충돌 명시
- [x] physical owner와 semantic owner 구분
- [x] Operations 관측 최소 신호 정의
- [x] Reliability hook 경계 정의
- [x] production code/SQL 비변경
- [x] IP-1 진입 조건과 총괄 승인 항목 분리
