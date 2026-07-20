# IP-1 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 단계 | `IP-1` |
| 상태 | `IMPLEMENTED / FULL GRADLE REGRESSION HOLD` |
| 신규 모듈 | `jc-intelligence-contracts` |
| DB/SQL 변경 | 없음 |
| 다음 후보 | `IP-2` |

## 1. 완료

- SC-1 예약 7개 common contract Java 구현
- ID/version/ref/time/hash/run/replay validator
- strict JSON parser, codec, common fixture 9개
- identity scheme와 exposure source registry
- backend read-only recommendation compatibility adapter
- P0 numeric post boundary adapter
- P1 profile, P2 assignment/exposure compatibility view
- recommendation compatibility matrix
- P2 metric protection fixture
- IP-1 문서와 verification evidence

## 2. 보호 결과

- `jc-recommendation-core/src/main/**`: 변경 없음
- 기존 `com.jc.backend.recommendation/**`: 변경 없음
- canonical SQL `01..26`: 변경 없음
- protected source/hash 320개 before/after exact match
- 기존 P1/P2 fixture와 test expectation: 변경 없음
- identity mapping/join: 없음
- Data shadow cutover: 없음
- P2 physical ownership migration: 없음
- model/search/content/planner runtime: 없음

## 3. 검증 결과

| 검증 | 결과 |
|---|---|
| contract compile | PASS, Java 21, lint warning 0 |
| contract executable | PASS, 739 assertions |
| adapter compile | PASS, Java 21, lint warning 0 |
| adapter executable | PASS, 226 assertions |
| recommendation core foundation/Wave1..7/golden/isolation | PASS |
| P1 Core | PASS, 17 scenarios |
| P2 Core | PASS, 23 scenarios |
| backend/root Gradle | BLOCKED — Gradle 8.14.5 distribution DNS |
| PostgreSQL | 미실행 — DB/SQL 비변경 |

## 4. 자체 리뷰

- 1차: 발견 4 / 수정 4 / 보류 0
- 2차: 발견 3 / 수정 3 / 보류 0
- 보완 후 신규 contract/adapter와 recommendation core 재검증 PASS

상세 내용은 [IP-1 본문](IP-1-COMMON-CONTRACT-TYPES-VALIDATION-AND-RECOMMENDATION-ADAPTER.md)을 따른다.

## 5. 다른 트랙 제공

### Data

- identity scheme validator
- exposure source registry
- P1/P2 shadow non-authority fixture
- source reference와 privacy classification 계약

### Reliability

- P2 assignment/exposure read-only compatibility view
- authoritative experiment exposure ID
- engagement/fallback protection fixture
- replay class contract

### Operations

- terminal run status, version, fallback/failure, replay evidence 타입
- user/operator/evaluation/debug explanation 분리

### System Coordination

- SC-1 registry와 코드 구현 대응 증거
- protected source hash comparison
- Gradle blocker와 IP-2 HOLD 판정

## 6. 잔여 리스크

1. Spring backend/JUnit/Testcontainers 전체 회귀를 현재 환경에서 재실행하지 못함
2. actual repository projection과 compatibility input 연결은 후속 read port 단계에서 필요
3. identity mapping physical owner는 보류
4. search exposure persistence는 reserved
5. P2 physical writer migration은 별도 High-risk 단계

## 7. IP-2 진입 판정

`HOLD`

해제 조건:

1. Gradle 8.14.5 distribution과 dependency cache가 사용 가능한 환경
2. `:jc-intelligence-contracts:check`
3. `:jc-backend:test`
4. `p1Verification`
5. `p2Verification`
6. root `test`
7. 모두 PASS 후 protected hash exact match 재확인

권장 다음 범위는 신규 기능이 아니라 **IP-1 Integration Regression Closure**다. 이 게이트가 닫힌 뒤 IP-2 Search Contract Foundation 또는 provider-independent search domain type 단계로 진입한다.
