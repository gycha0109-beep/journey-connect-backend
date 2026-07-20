# DP-0 P2 Baseline Alignment

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `dp-0-p2-baseline-alignment-v1` |
| 상태 | `ACTIVE AMENDMENT` |
| 소유 트랙 | Data Platform |
| 원 계약 | `jc-data-platform-contract-foundation-v1` |
| 기준 프로젝트 | `Journey-Connect-P2-Final-Validation-Batch18` |
| 기준 추천 상태 | `P2 TECHNICAL CLOSED / PRODUCTION HOLD` |
| 기준 DB | `journey-connect-db-v2.7`, canonical SQL `01..26` |
| 작성 기준일 | 2026-07-19 |

## 2. 목적

DP-0은 추천 P0 종료 기준선에서 작성되었다. 이후 추천 트랙이 P1과 P2까지 구현되었으므로, 이미 존재하는 P1 프로필과 P2 실험·평가 경로를 Data Platform이 대체하거나 의미를 바꾸지 않도록 호환 기준선을 추가한다.

이 문서는 기존 `platform-event-v1`, P0 adapter, idempotency, retry, quarantine, replay 계약을 변경하지 않는다. P1/P2에 대한 향후형 가정을 현재 구현에 맞게 보정한다.

## 3. 실제 확인한 P2 기준선

첨부 기준 프로젝트에서 다음을 확인했다.

- P1 기술 구현: `CLOSED`
- P2 기술 구현: `CLOSED`
- P2 운영 출시 상태: 실제 CANARY 표본과 운영 승인 부재로 `HOLD`
- Java Core: P1 17개, P2 23개 계약 시나리오 PASS
- Backend 전체: `83/83 PASS`
- P0/P1/P2 계약 게이트 PASS
- PostgreSQL canonical SQL `01..26` fresh database PASS
- P2 assignment/exposure/evaluation/release PostgreSQL integration PASS
- P2 assignment runtime 기본값: 비활성
- P0/P1 기준 파일과 SQL `01..24`: 비변경 검증

## 4. 현재 P1 실행 경로

현재 P1은 Data Platform projection을 소비하지 않는다.

```text
recommendation_behavior_event
+ posts / regions / post_tags / tags
+ recommendation_user_preference
        ↓
RecommendationP1ProfileSource
        ↓
P1 deterministic profile builder
        ↓
recommendation_p1_profile_snapshot
        ↓
P1 policy selection / SHADOW / CANARY
```

현재 고정된 핵심 계약:

- profile table: `recommendation_p1_profile_snapshot`
- segment: `empty`, `explicit_only`, `emerging`, `established`
- profile policy version과 feature vocabulary version 결속
- reference time, event partition count, signal list, SHA-256 fingerprint
- behavior source는 기존 `recommendation_behavior_event`
- profile transform, decay, saturation, feature vocabulary는 Intelligence 소유

따라서 `recommendation-profile-input-v1`은 현재 P1 런타임의 필수 선행조건이 아니다. Data Platform이 이후 생성할 수 있는 **병렬 입력 facts snapshot 또는 차기 profile source 후보**다.

## 5. 현재 P2 실행 경로

```text
recommendation_p2_experiment_assignment
        ↓
recommendation_p2_experiment_exposure
        ↓
recommendation_run
+ recommendation_behavior_event
+ recommendation_p1_profile_snapshot
        ↓
RecommendationP2ObservationSource
        ↓
recommendation-evaluation-dataset-v1
        ↓
recommendation_p2_dataset_snapshot
        ↓
evaluation run / metric result / Gate A~E
        ↓
optional release transition decision
```

현재 고정된 계약:

| 영역 | 현재 계약 |
|---|---|
| assignment identity | `(experiment_id, experiment_version, subject_ref)` unique |
| assignment subject | `subject_ref = user:<numeric-user-id>` |
| variant | `baseline`, `treatment` |
| exposure authority | `recommendation_p2_experiment_exposure` |
| exposure binding | assignment, run, user, session, variant 검증 |
| dataset schema | `recommendation-evaluation-dataset-v1` |
| metric definition | `recommendation-metrics-v1` |
| evaluation policy | `recommendation-evaluation-policy-v1` |
| primary metric | `engagement_rate` |
| guardrail metric | `fallback_rate` |
| release states | `draft`, `shadow`, `canary`, `live`, `hold`, `rolled_back` |
| final decision | `canary`, `live`, `hold`, `rollback` |
| persistence | dataset/evaluation/result/gate/decision append-only |

현재 `engagement_rate`는 실제 결속된 exposure 이후 7일 이내 `click`, `like`, `save`, `share` 중 하나가 존재하는 exposed eligible subject 비율이다. `view`, `hide`, `report`를 임의로 engagement에 추가하지 않는다.

현재 `fallback_rate`는 결속된 exposed run 중 `recommendation_run.run_status = fallback`인 run 비율이다.

## 6. Data Platform 적용 결정

### 6.1 기존 P1/P2 runtime 비개입

Data Platform은 다음을 하지 않는다.

- `RecommendationP1ProfileSource`를 즉시 교체
- `recommendation_p1_profile_snapshot`을 재작성
- P2 assignment, exposure, dataset, evaluation, gate, release table에 write
- P2 metric 의미, attribution window, threshold, state machine 변경
- 기존 P2 dataset canonical bytes/hash 재계산 또는 강제 이관
- P1/P2 runtime에 Data Platform 의존성 추가

### 6.2 P1 bridge

Data Platform은 후속 DP-5에서 다음을 shadow-only로 생성할 수 있다.

```text
validated behavior stream
→ user behavior aggregate
→ recommendation-profile-input-v1
```

전환 조건:

1. 현재 P1 source와 동일 관측 범위·event ordering·dedupe 의미를 문서화
2. 현재 feature lookup에 필요한 region/tag facts의 승인된 projection 확보
3. profile builder 입력 비교 fixture 작성
4. P1 17개 core 계약과 P0/P1 전체 회귀 PASS
5. 기존 profile snapshot replay/fingerprint 비변경 확인
6. 신규 profile source/schema version 명시
7. System Coordination과 Intelligence 승인

승인 전 `recommendation-profile-input-v1`은 현재 P1의 authoritative source가 아니다.

### 6.3 P2 bridge

Data Platform은 후속 DP-5에서 다음을 shadow-only로 생성할 수 있다.

```text
assignment read contract
+ authoritative P2 exposure
+ canonical outcome facts
+ run/profile references
→ experiment-outcome-input-v1
```

이 dataset은 현재 `recommendation-evaluation-dataset-v1`을 자동 대체하지 않는다.

전환 조건:

1. assignment/exposure/run/user/session binding exact match
2. 관측 기간 경계 exact match
3. stale unexposed assignment 제외 규칙 exact match
4. `click/like/save/share` engagement semantics exact match
5. fallback run count semantics exact match
6. one observation per experiment/version/subject dedupe exact match
7. metric definition version과 dataset schema version 결속
8. canonical bytes/content hash reproducibility
9. P2 23개 core 및 5개 backend contract gate PASS
10. 기존 release evidence와 상태 전이 비변경
11. Reliability/System Coordination 승인

## 7. source authority와 중복 방지

| 의미 | authoritative source |
|---|---|
| P0/P1 behavior fact | `recommendation_behavior_event` |
| 일반 추천 page exposure | 기존 `recommendation_exposure_event` 경로 |
| P2 실험 exposure | `recommendation_p2_experiment_exposure` |
| P2 assignment | `recommendation_p2_experiment_assignment` |
| P2 segment lookup | 관측 종료 시각 이전 최신 `recommendation_p1_profile_snapshot` |
| P2 fallback | `recommendation_run.run_status` |
| P2 release evidence | P2 dataset/evaluation/metric/gate/release append-only tables |

동일한 recommendation exposure를 일반 exposure, P0 behavior `impression`, P2 experiment exposure에서 동시에 분모로 합산하지 않는다. 각 dataset은 목적별 authority와 dedupe key를 명시한다.

## 8. identity 호환성

신규 Data canonical event actor는 `subject:<opaque-id>`를 기본으로 하지만 현재 P2 assignment는 DB 제약상 `user:<numeric-user-id>`를 사용한다.

- 기존 P2 row를 rewrite하지 않는다.
- 기존 P2 dataset을 opaque subject로 조용히 재해석하지 않는다.
- Data dataset과 P2 dataset을 연결할 때 restricted identity mapping port를 사용한다.
- opaque subject로 P2 평가 입력을 전환하면 새 dataset/schema version과 migration/replay 계획이 필요하다.
- 기존 evaluation snapshot의 subject identity material과 content hash는 보존한다.

## 9. 소유권 배치 충돌

Track Governance는 experiment assignment/evaluation/release를 Reliability 소유로 정의한다. 현재 구현은 다음 위치에 존재한다.

- package: `com.jc.backend.recommendation.p2`
- DB objects: `recommendation_p2_*`
- runtime DB role: recommendation role

이는 Data Platform이 해결하거나 임의로 이전할 문제가 아니다.

판정:

- 기능 의미의 최종 소유권은 기존 Governance에 따라 Reliability
- 현재 물리적 구현·write path는 P2 CLOSED 기준선으로 보호
- ownership 이전 또는 port 분리는 별도 High-risk handoff와 전체 회귀가 필요한 System Coordination 사안
- Data Platform은 현재 object에 read-only 승인 없이 접근하지 않고, 절대 write하지 않는다

## 10. DB 기준선 영향

- 현재 canonical DB 기준은 `journey-connect-db-v2.7/01..26`
- Data Platform은 SQL `25..26`을 수정하지 않는다.
- DP-2 신규 DB 번호는 System Coordination이 `v2.7/26` 이후 기준으로 배정해야 한다.
- 신규 Data schema가 같은 DB에 추가되어도 P1/P2 table write owner와 role/grant를 침범하지 않는다.

## 11. DP 단계 보정

| 단계 | P2 기준선 반영 |
|---|---|
| DP-1 | P1/P2 식별자·버전 fixture를 검증하되 runtime code 변경 없음 |
| DP-2 | canonical DB `v2.7/26` 이후 번호 배정 필요 |
| DP-3 | replay가 기존 P1/P2 evidence를 수정하지 않는지 검증 |
| DP-4 | P0 adapter + P1 profile/P2 outcome 비회귀 contract fixture |
| DP-5 | P1/P2 bridge dataset은 shadow-only로 구축 |
| DP-6 | P1/P2 source-to-shadow reconciliation과 lineage 품질 규칙 |
| DP-7 | 기존 P1/P2 runtime과 신규 Data dataset의 cross-track contract test |

## 12. 검증 결과

| 검증 | 결과 |
|---|---|
| P2 reports 및 verification summary 확인 | PASS |
| P1 direct behavior source 확인 | PASS |
| P2 assignment/exposure/observation/evaluation 저장 경로 확인 | PASS |
| dataset/metric/evaluation version 확인 | PASS |
| P2 subjectRef 형식 확인 | PASS |
| 기존 DP-0 P0 adapter 의미 영향 없음 | PASS |
| production code 변경 없음 | PASS |
| SQL 변경 없음 | PASS |
| P2 테스트 재실행 | 미실행 — 문서 보정 단계, 제공된 Batch18 검증 증거 확인 |

## 13. 잔여 승인 항목

- 현재 P2 물리적 write path와 Reliability 논리 소유권 정합화
- `user:<id>` ↔ `subject:<opaque>` identity bridge 소유권
- P1/P2 shadow bridge의 read 권한과 projection contract
- DP-2 canonical DB version/SQL sequence
- 운영 P2 assignment config와 관리자 승인 경로
