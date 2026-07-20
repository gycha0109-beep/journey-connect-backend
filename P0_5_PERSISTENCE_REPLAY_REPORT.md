# P0-5 Persistence Replay & CANARY Readiness

## 변경

- SHADOW run 저장 직후 별도 `jc_recommendation` 트랜잭션에서 exact replay 수행
- ranking input/result canonical bytes와 SHA-256 fingerprint 재검증
- diversity/exploration snapshot, policy binding, ranked/terminal 후보와 provenance JSON까지 대조
- append-only `recommendation_replay_audit` 추가
- 최소 SHADOW 수, exact audit, 성공 상태, p95 지연 기반 CANARY readiness 판정
- readiness는 모드를 변경하지 않으며 CANARY/LIVE 시작 차단은 유지
- DB v2.4 증분 SQL `19~20` 추가

## 검증

- PostgreSQL 15.18 canonical SQL `01~20`: PASS
- Backend tests: 53/53 PASS
- P0~P0-5 Java verification: PASS
- Recommendation Core Foundation~Wave 7 + golden/isolation: PASS
- compileJava / compileTestJava: PASS

## 판정

P0-5 완료. 실제 CANARY 노출은 아직 활성화하지 않았습니다.
