# P2-1 Experiment & Metric Contract Report

## 목적

P1 CANARY 결과를 통계적으로 비교할 수 있도록 결정론적 실험 배정, 실제 노출 결속, 버전 고정된 지표 정의를 추가한다. 기존 P1 CANARY 경로는 기본 설정에서 그대로 유지한다.

## 기준선

- P1 Final Validation Batch17
- P0/P1 정책·replay·snapshot 불변
- P2 assignment runtime 기본값: 비활성
- P2 대상: `recommendation-p1 / experiment-v1`

## 변경 파일

- `jc-recommendation-core/src/main/java/com/jc/recommendation/p2/P2ExperimentAssigner.java`
- `jc-recommendation-core/src/main/java/com/jc/recommendation/p2/P2EvaluationContracts.java`
- `jc-recommendation-core/src/main/java/com/jc/recommendation/p2/P2Policies.java`
- `jc-backend/src/main/java/com/jc/backend/recommendation/config/RecommendationP2Properties.java`
- `jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2AssignmentService.java`
- `jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2AssignmentStore.java`
- `jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationP1RuntimeService.java`
- `database/journey-connect-db-v2.7/25_recommendation_p2_evaluation_release.sql`

## 구현 내용

1. `experimentId + experimentVersion + subjectRef + assignmentSalt`의 SHA-256으로 0~9999 bucket을 계산한다.
2. 동일 실험 버전과 subject는 항상 동일 variant를 받는다.
3. assignment는 `(experiment_id, experiment_version, subject_ref)` 단위로 append-only 저장한다.
4. baseline과 treatment 모두 실제로 반환된 `runId`와 함께 exposure를 저장한다.
5. treatment exposure는 `recommendation_p1_policy_assignment.treatment_run_id`에 존재하는 실행에만 결속한다.
6. metric definition은 다음 의미를 명시적으로 고정한다.
   - `engagement_rate`: 노출 후 7일 내 click/like/save/share가 존재하는 subject 비율
   - `fallback_rate`: 노출된 추천 run 중 fallback run 비율
7. 각 metric은 numerator, denominator, eligibility, deduplication, attribution window를 버전 계약에 포함한다.

## 검증 결과

- 결정론적 배정 및 0%/100% 경계 검증 PASS
- assignment binding conflict 검증 PASS
- baseline/treatment exposure 결속 검증 PASS
- P1 assignment 기반 treatment run 판정 SQL smoke PASS
- P2 비활성 시 기존 P1 CANARY 분기 보존 PASS

## 보완 사항

- 최초 구현의 `ranking_policy_version LIKE ...` 판정을 제거했다.
- treatment run 여부를 P1 정책 배정 증거로 판정하여 신규 P1 정책 버전에도 대응하도록 보완했다.
- metric definition의 임시 설명 문자열을 제거하고 실제 분자·분모·적격·중복 제거 규칙을 계약에 포함했다.

## 잔여 리스크

- 운영 활성화 전 assignment salt, allocation, experiment version을 운영 승인으로 고정해야 한다.
- 실험 중 allocation 또는 salt 변경은 동일 experiment version에서 금지한다.
