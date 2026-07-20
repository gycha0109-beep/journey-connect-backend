# P1-2 Versioned Policy Selection

## 목적

기존 P0/Java v1 정책을 수정하지 않고 사용자 세그먼트·surface·세션 상태에 따라 P1 v2 정책을 병렬 선택하며, 선택 이유와 버전 벡터를 추적 가능하게 저장한다.

## 주요 변경 파일

- `jc-recommendation-core/.../p1/policy/*`
- `jc-backend/.../recommendation/application/RecommendationP1ModeDecider.java`
- `jc-backend/.../recommendation/config/RecommendationP1Properties.java`
- `jc-backend/.../recommendation/p1/RecommendationP1EvidenceStore.java`
- `database/journey-connect-db-v2.6/23_recommendation_p1_profile_policy.sql`

## 구현

- 세그먼트별 `ranking-policy-v2-*`
- surface별 `diversity-policy-*-v2`
- `retrieval-policy-v2`, `low-exposure-policy-v2`, `exploration-policy-v2`
- `p1-policy-bundle-v1:<surface>:<segment>` 병렬 버전
- treatment assignment만 P1 selector에 허용
- 선택 이유, profile/vocabulary/retrieval/score/diversity/exploration 버전 저장
- 사용자 기반 deterministic CANARY cohort와 OFF/SHADOW/CANARY 모드
- 과거 assignment 수를 session context로 명시 입력

## 검증 및 보완

- 4개 세그먼트 × 전체 지원 surface 선택 행렬 PASS
- baseline assignment의 P1 selector 진입 거부 PASS
- 동일 입력의 정책 선택과 reasons 결정론 PASS
- 기존 v1과 혼동되던 정책 명칭을 명시적 v2로 정규화
- assignment/run/profile 결속 trigger와 append-only 검증 PASS

## 잔여 리스크

- 정책 자동 학습이나 자동 승격은 구현하지 않았다. 버전 승격과 rollout 비율 변경은 명시적 운영 승인 대상이다.
