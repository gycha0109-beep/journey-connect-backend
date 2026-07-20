# DB v2.5 — P0-7 Behavior Runtime

## 증분 SQL

- `21_recommendation_behavior_runtime.sql`
- `22_recommendation_behavior_runtime_smoke_test.sql`

## 보장

- run user·session·ranked candidate 결속
- event ID·idempotency key 동시성 직렬화
- 동일 key·동일 payload 재시도 dedupe
- 동일 key·다른 payload 충돌
- like·unlike·save·unsave 상태 변경과 behavior 기록 원자성
- 상태 변화 없는 중복 요청의 신규 behavior 억제
- `jc_app` 직접 behavior INSERT 금지 및 보안 함수 실행만 허용
- append-only 추천 이력 유지

## 적용

- 빈 DB: `01~22`
- DB v2.4: `21~22`
