# P2-2 Statistical Evaluation Report

## 목적

P1 baseline과 treatment를 재현 가능한 dataset snapshot으로 고정하고, 효과·불확실성·세그먼트 결과를 결정론적으로 계산한다.

## 변경 파일

- `jc-recommendation-core/src/main/java/com/jc/recommendation/p2/P2EvaluationEngine.java`
- `jc-recommendation-core/src/main/java/com/jc/recommendation/p2/P2Canonical.java`
- `jc-recommendation-core/src/test/java/com/jc/recommendation/p2/P2CoreContractTest.java`
- `jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java`
- `jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2EvaluationService.java`

## 구현 내용

- baseline/treatment mean 및 raw/oriented effect
- pooled standard deviation 기반 effect size
- 고정 seed bootstrap confidence interval
- p-value 및 Holm family-wise multiple-comparison correction
- `all` 및 정렬된 segment별 평가
- 최소 전체·세그먼트 표본 수
- common support 및 missing metric 품질 판정
- 입력 순서 독립성과 결과 fingerprint
- 동일 입력·seed·정책의 동일 결과 보장

## 지표 정책 V1

| 지표 | 역할 | 방향 | 기준 |
|---|---|---|---|
| `engagement_rate` | primary | higher is better | 최소 개선 0.01 |
| `fallback_rate` | guardrail | lower is better | 최대 회귀 0.02 |

기본 평가 정책:

- 전체 variant별 최소 100명
- segment variant별 최소 30명
- common support 0.80 이상
- 95% confidence interval
- bootstrap 10,000회
- family-wise alpha 0.05
- 최소 절대 effect size 0.10

## 검증 결과

P2 core contract **23/23 PASS**:

- 결정론·입력 순서 독립
- CANARY/LIVE 승격
- 운영 승인 누락 HOLD
- 잘못된 상태 전이 HOLD
- 표본 부족 HOLD
- variant 불균형·missing metric·기간 외 데이터 FAIL
- 미노출 assignment 평가 제외
- lower-is-better 방향 보정
- severe guardrail regression rollback
- 표본 부족 severe regression은 rollback하지 않고 HOLD
- 세그먼트 표본 게이트
- 중복 observation/subject/metric 및 혼합 metric version 거부
- dataset fingerprint 결속

## 보완 사항

- 최초 severe-regression 판정은 작은 표본에서도 rollback할 수 있었다.
- `sampleSufficient + dataQualityPass + regression CI upper < 0`을 충족할 때만 통계적 rollback으로 판정하도록 보완했다.
- metric semantics를 core record에 포함하여 동일 metric version의 의미 변경을 차단했다.

## 잔여 리스크

- 현재 z 근사 p-value는 V1 평가 계약이다. 데이터 분포에 따라 permutation test 또는 exact test가 필요하면 새 evaluation-policy version으로 추가한다.
- 실제 성과 판정은 운영 표본이 확보된 dataset snapshot에서만 가능하다.
