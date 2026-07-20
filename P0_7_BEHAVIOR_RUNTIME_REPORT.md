# P0-7 Recommendation Behavior Runtime

## 구현

- CANARY 응답에 `recommendationRunId`를 전달하되 비추천 응답의 기존 JSON 계약은 유지했다.
- 인증된 run-bound `view`, `click`, `share`, `hide`, `report` 수집 API를 추가했다.
- 사용자·JWT session·run·ranked candidate 결속을 Java와 PostgreSQL에서 이중 검증한다.
- `like`, `unlike`, `save`, `unsave`를 상태 변경과 behavior event가 같은 APP-role 트랜잭션에서 처리하도록 전환했다.
- 상태 변화가 없는 중복 요청에는 신규 behavior event를 생성하지 않는다.
- 동일 event ID/idempotency key는 advisory transaction lock으로 직렬화한다.
- 동일 key·동일 canonical payload는 duplicate, 동일 key·다른 payload는 `409 IDEMPOTENCY_CONFLICT`로 판정한다.
- occurred-at 허용 범위, run/event ID, metadata 크기·형태를 검증한다.
- `jc_app`의 behavior 테이블 직접 INSERT는 계속 금지하고 보안 함수 실행만 허용한다.
- DB v2.5 증분 SQL `21~22`를 추가하고 기존 `01~20`은 변경하지 않았다.

## 검증

- 직접 event 저장·재시도·충돌: PASS
- run 사용자·세션·candidate 불일치 거부: PASS
- like/unlike/save/unsave 원자성·dedupe·no-change 억제: PASS
- 동시 conflicting idempotency에서 단일 mutation·단일 event: PASS
- PostgreSQL 역할·함수 권한 smoke: PASS
