# P2-4 Runtime & Persistence Report

## 목적

실험 배정부터 노출, 관측 dataset, 평가 결과, 게이트, release decision까지 재현 가능한 저장 체인을 구성한다.

## 변경 파일

- `jc-backend/src/main/java/com/jc/backend/recommendation/p2/*`
- `database/journey-connect-db-v2.7/25_recommendation_p2_evaluation_release.sql`
- `database/journey-connect-db-v2.7/26_recommendation_p2_evaluation_release_smoke_test.sql`
- `jc-backend/src/test/resources/db/canonical/25_*.sql`
- `jc-backend/src/test/resources/db/canonical/26_*.sql`
- canonical bootstrap/cleanup test files

## 저장 체인

```text
experiment assignment
→ bound run exposure
→ subject-level observation
→ canonical dataset bytes + SHA-256
→ evaluation run
→ segment metric results
→ Gate A~E results
→ optional release transition decision
```

## 주요 제약

- assignment uniqueness: experiment/version/subject
- exposure uniqueness: assignment/run
- server-owned user/session/run binding
- dataset canonical bytes/hash/size 검증
- metric version 의미 충돌 검출
- evaluation time은 observedTo 이후
- result/gate/decision append-only
- evaluation evidence 개수 재검증

## PostgreSQL 검증

- PostgreSQL **15.18**
- canonical SQL `01..26` 빈 DB 순차 적용 PASS
- SQL `26` smoke PASS
- assignment/exposure/evaluation/release binding PASS
- 권한: `jc_recommendation` SELECT/INSERT, UPDATE/DELETE/TRUNCATE 금지
- P2 수치 검증 함수 실행 권한 PASS

## 보완 사항

- 최초 권한 설계에서 `recommendation_p2_is_finite(double precision)` 실행 권한이 누락되어 role insert가 실패했다.
- `jc_recommendation`, `jc_admin` execute 권한을 명시적으로 추가했다.
- 동일 metric version에 다른 의미가 저장되는 경우 application에서 즉시 실패하도록 보완했다.
- stale unexposed assignment가 무기한 분모에 남지 않도록 관측 기간 필터를 추가했다.

## 잔여 리스크

- 대규모 dataset canonical payload는 현재 16 MiB 제한이다. 운영 규모가 이를 넘으면 versioned partition/snapshot 전략이 필요하다.
- 장기 실험에서는 관측 쿼리 성능을 실제 데이터량으로 벤치마크해야 한다.
