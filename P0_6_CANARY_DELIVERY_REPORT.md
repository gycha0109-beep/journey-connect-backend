# P0-6 Controlled CANARY Delivery

## 변경

- CANARY 모드에 명시적 release ID, 1~10000 basis-point 할당, 32바이트 이상 HMAC secret 검증을 추가했다.
- 사용자 할당을 release ID로 namespace한 HMAC-SHA256 결정적 bucket으로 고정했다.
- 추천 커서를 user, JWT session, run, offset, release ID에 서명 결속하고 길이·형식 제한을 적용했다.
- readiness exact replay gate를 통과한 인증 사용자 첫 페이지에서만 CANARY run을 생성한다.
- 첫 페이지 실패는 기존 최신순 피드로 fail-open하고 추천 커서 후속 페이지는 fail-closed한다.
- 후속 페이지는 저장된 run candidate 순서를 사용하며 재랭킹하지 않는다.
- 반환 직전 게시물 공개 상태를 APP 역할 트랜잭션에서 재검증하고 요청 순서를 보존한다.
- 노출 page/candidate canonical payload, fingerprint, replay key를 RECOMMENDATION 역할로 저장한다.
- 같은 run/offset/size 재요청의 exposure 저장을 idempotent하게 처리한다.
- 동일 canonical snapshot content는 기존 immutable row를 재사용한다.
- snapshot dedupe로 실제 metadata/exploration snapshot ID가 변경되면 ranking input을 실제 ID로 재생성해 exact replay binding을 보존한다.
- LIVE 시작 차단을 유지했다.
- DB 스키마 변경 없이 v2.4 canonical SQL 01~20을 유지했다.

## 구현 중 발견·보완

- SHADOW와 CANARY가 동일 metadata snapshot을 공유할 때 ranking input이 요청 ID를 참조해 `invalid_binding`이 발생하던 문제를 수정했다.
- 배포 release 변경 후 이전 커서가 새 release에서 계속 사용될 수 있던 경로를 release-bound cursor로 차단했다.
- 같은 secret을 사용하더라도 release별 CANARY cohort가 분리되도록 allocation material에 release ID를 포함했다.

## 검증 결과

- `compileJava` / `compileTestJava`: PASS
- PostgreSQL 15.18 backend tests: 58/58 PASS
- P0~P0-6 Java verification: 9/9 PASS
- Recommendation Core Foundation~Wave 7, golden fixture, framework isolation: PASS
- PostgreSQL CANARY first page / next page / retry / exact replay / exposure persistence: PASS
- DB v2.4 canonical/test SQL 01~20: EXACT MATCH
- 기존 DB SQL 변경: 없음
