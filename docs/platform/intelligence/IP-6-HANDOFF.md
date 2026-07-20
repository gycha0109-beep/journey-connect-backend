# IP-6 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-6-handoff-v1` |
| 단계 | `IP-6 Search Runtime Integration Boundary` |
| 상태 | `COMPLETE` |
| 다음 후보 | `IP-7 Search Shadow Wiring & Controlled Comparison` |

## 완료

- 독립 모듈 `jc-search-integration`
- backend-facing generic integration port
- disabled-by-default activation과 unknown-mode safe handling
- legacy response pass-through/authority guard
- runtime input provider isolation
- fixture/test-only runtime execution boundary
- deterministic legacy/runtime comparison harness
- mismatch/severity/not-comparable taxonomy
- top-K, entity set/order/count/position/duplicate metrics
- fail-open and supplied-deadline timeout boundary
- privacy-safe, memory-only comparison evidence
- production authority가 없는 immutable integration result

## 기준선

- Recommendation P0/P1/P2 technical: `CLOSED`
- Recommendation P2 production: `HOLD`
- IP-1/IP-1.10: `CLOSED`
- IP-2/IP-3/IP-4/IP-5: `COMPLETE`
- canonical DB: `journey-connect-db-v2.7/01..26`

## 변경 계약 ID

- `search-runtime-integration-boundary-v1`
- `search-shadow-comparison-evidence-v1`
- `ip-6-handoff-v1`

기존 IP-1~IP-5 wire value와 필수 의미는 변경하지 않았다.

## 변경 파일

- 신규 `jc-search-integration`
  - main Java 35개
  - contract runner 1개
  - JSON fixture 12개
- 최소 build 변경
  - `jc-backend/settings.gradle.kts`
- 문서
  - `IP-6-SEARCH-RUNTIME-INTEGRATION-BOUNDARY.md`
  - `IP-6-HANDOFF.md`
  - Intelligence `README.md`
- 검증 증거
  - `verification/ip6/`

## DB 변경

`NONE`

- migration 없음
- 신규 SQL/index 없음
- canonical SQL `01..26` exact match

## Authority 결과

```text
Legacy response authority: TRUE
Shadow runtime authority: FALSE
Client response impact: NONE
Persistence authority: FALSE
Exposure authority: FALSE
Metric authority: FALSE
Release gate authority: FALSE
Production cursor authority: FALSE
API cutover authority: FALSE
```

## 검증 결과

| 대상 | 결과 |
|---|---|
| IP-6 integration | `972 assertions PASS` |
| IP-5 runtime | `850 assertions PASS` |
| IP-4 compatibility | `584 assertions PASS` |
| IP-3 Search contracts | `425 assertions PASS` |
| IP-1 common contracts | `739 assertions PASS` |
| Java 21 compile | `PASS` |
| `-Xlint:all -Werror` | `PASS` |
| JSON fixture | `12/12 PASS` |
| Recommendation direct regression | Foundation/Wave1~7/golden/isolation/P1 17/P2 23 PASS |
| Protected source | `320/320 exact match` |
| SQL | `26/26 exact match` |
| Gradle Wrapper | `BLOCKED before task execution — services.gradle.org DNS resolution failure` |

## 자체 리뷰

- 1차: `10 발견 / 10 수정 / 0 보류`
- 2차: `8 발견 / 8 수정 / 0 보류`
- 보완 후 전체 관련 contract regression과 보호 해시 재검증 PASS

## 호환성과 production 영향

- `/api/v1/explore` Controller/Service/Repository/DTO/JPQL/SecurityConfig 변경 없음
- production Spring bean/profile/property/executor 없음
- legacy compatibility result를 runtime candidate source로 사용하지 않음
- legacy offset을 Search cursor로 변환하지 않음
- legacy latest order를 Search ranking equivalence로 선언하지 않음
- SearchRun/snapshot/comparison/exposure persistence 없음
- Recommendation candidate/ranking/exposure/metric 의존 없음

## 잔여 리스크

1. Gradle distribution 접근 환경에서 Gradle tasks 재실행
2. 실제 retrieval/index source와 authority
3. Operations visibility/eligibility owner
4. SearchRun/snapshot/search exposure writer
5. query and shadow evidence retention policy
6. production cursor signing key와 rotation
7. bounded executor/performance budget
8. activation/rollback authority

## 다음 작업

### 권장

`IP-7 Search Shadow Wiring & Controlled Comparison`

### 진입 판정

`READY — DISABLED SHADOW BOUNDARY ONLY`

### 허용 후보

- explicit disabled-by-default backend hook
- profile/flag gate
- bounded executor abstraction
- no response replacement
- deterministic comparison logging port

### HOLD

- production activation 또는 API cutover
- response replacement/reordering
- DB/index/provider wiring
- SearchRun/snapshot/comparison/exposure persistence
- production cursor issuance
- release gate authority

## 최종 상태

```text
IP-6: COMPLETE
Search Runtime Integration Boundary: IMPLEMENTED
Shadow Execution: IMPLEMENTED / DISABLED_BY_DEFAULT
Legacy Comparison Harness: IMPLEMENTED
Comparison Evidence: IMPLEMENTED / NON_PERSISTENT
Legacy Response Authority: MAINTAINED
Client Response Impact: NONE
Production Search API Wiring: NOT ENABLED
Search API Cutover: NOT STARTED
Search Persistence: NOT IMPLEMENTED
Search Exposure Persistence: NOT IMPLEMENTED
Production Cursor Authority: NOT ENABLED
Release Gate Authority: NOT ENABLED
Legacy /api/v1/explore Behavior: UNCHANGED
Protected Baseline: MAINTAINED
```
