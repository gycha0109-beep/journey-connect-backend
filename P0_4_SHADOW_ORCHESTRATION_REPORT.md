# P0-4 SHADOW Orchestration 결과

## 반영

- 추천 실행 모드 `OFF / SHADOW / CANARY / LIVE` 도입
- 기본값 `OFF`, 인증 사용자의 `/feed` 첫 페이지에서만 SHADOW 실행
- 기존 피드 응답을 먼저 생성하고 추천 실패는 fail-open 처리
- Java Core scoring·diversity·exploration 전체 실행
- ranking input·diversity·exploration·result snapshot 4종 저장
- immutable run·최종 ranked·terminal audit 저장
- `CANARY`, `LIVE` 시작 단계 차단
- DB v2.3 증분 SQL `17~18` 추가
- exploration 삽입을 반영해 입력 분할을 `final-ranked + terminal`로 수정

## 실행 중 보완

- AOP 대상 orchestration 서비스의 `final` 선언 제거
- append-only 추천 이력을 테스트에서 안전하게 초기화하도록 전용 `TRUNCATE` 정리 적용
- 위 두 회귀 조건을 Java 정적 게이트에 고정

## 검증

- PostgreSQL: **15.18 직접 기동**
- backend Java tests: **49/49 PASS**
  - 환경의 단일 호출 45초 제한 때문에 동일 `:test` 대상을 패키지별로 분할 실행
- `p0Verification`: **PASS**
- recommendation Core Foundation~Wave 7: **PASS**
- Java golden fixtures / framework isolation: **PASS**
- DB v2.2 `01~16` → v2.3: **EXACT MATCH**
- DB v2.3 `01~18` → canonical test SQL: **EXACT MATCH**

## 운영 상태

`RECOMMENDATION_MODE` 기본값은 `OFF`입니다. `SHADOW`는 계산과 저장만 수행하며 `/feed` 정렬·응답 계약은 변경하지 않습니다.
