# P1-1 Behavior Profile & Explicit Preference

## 목적

행동 이벤트를 점수에 직접 투입하지 않고, 검증·중복 제거·귀속·감쇠·포화 처리를 거쳐 결정론적 사용자 프로필 snapshot으로 변환한다. 행동 이력이 없는 사용자는 인증된 명시적 선호를 통해 cold-start 프로필을 구성한다.

## 주요 변경 파일

- `jc-recommendation-core/.../p1/profile/*`
- `jc-backend/.../recommendation/p1/RecommendationP1ProfileSource.java`
- `jc-backend/.../recommendation/application/RecommendationPreferenceService.java`
- `jc-backend/.../recommendation/api/RecommendationPreferenceController.java`
- `database/journey-connect-db-v2.6/23_recommendation_p1_profile_policy.sql`
- `jc-backend/.../RecommendationP1BehaviorProfileIntegrationTest.java`
- `jc-backend/.../RecommendationPreferenceIntegrationTest.java`

## 구현

- event ID 기준 정렬·dedupe와 동일 ID 상충 payload 거부
- 기준 시각 이후 및 lookback 밖 이벤트 제외
- 이벤트 유형별 가중치와 half-life 감쇠
- 등록된 지역·테마·활동·동행 feature만 귀속
- explicit/behavior signal 병합 및 bounded saturation
- `EMPTY`, `EXPLICIT_ONLY`, `EMERGING`, `ESTABLISHED` 세그먼트
- profile version, vocabulary version, reference time, fingerprint 고정
- 인증 사용자 바인딩 `GET/PUT /api/v1/recommendation/preferences`
- `SECURITY DEFINER` DB 함수의 atomic replace와 recommendation role 직접 DML 차단
- profile snapshot append-only 저장

## 검증 및 보완

- P1 Core contract의 profile 관련 정상·경계·상충 시나리오 PASS
- 실제 behavior event → 지역·테마·활동 signal 변환 PASS
- 명시적 선호 replace·cross-user 차단·미등록 feature 거부 PASS
- 중복 explicit preference가 fingerprint count를 오염시키던 문제 보완
- policy effective time 이전 profile 생성 거부 추가
- 동일 event ID 상충 payload fail-closed 추가

## 잔여 리스크

- 이벤트 가중치·half-life·signal threshold는 P1 정책값이며, 실제 성과 승격은 P2 통계 게이트에서 판단한다.
