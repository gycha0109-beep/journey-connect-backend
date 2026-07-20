# IP-5 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-5-handoff-v1` |
| 단계 | `IP-5 Search Runtime Foundation` |
| 상태 | `COMPLETE` |
| 다음 후보 | `IP-6 Search Runtime Integration Boundary` |

## 완료

- 독립 모듈 `jc-search-runtime`
- in-memory `SearchRuntime` / `DefaultSearchRuntime`
- retrieval, filtering, eligibility, visibility ports
- Search-only ranking/reranking ports
- deterministic ordering
- immutable ephemeral result snapshot
- ephemeral SearchRun/IntelligenceRun mapping
- bounded ranking fallback
- typed runtime failure
- memory-only privacy-safe evidence
- snapshot-bound test-only cursor invariant
- JSON fixture 14개와 runtime contract runner

## 기준선

- Recommendation P0/P1/P2 technical: `CLOSED`
- Recommendation P2 production: `HOLD`
- IP-1/IP-1.10: `CLOSED`
- IP-2: `COMPLETE / CONTRACT_ONLY`
- IP-3: `COMPLETE`
- IP-4: `COMPLETE`
- canonical DB: `journey-connect-db-v2.7/01..26`

## 변경 계약 ID

- `search-runtime-foundation-v1`
- `ip-5-handoff-v1`

기존 IP-1/IP-2/IP-3 contract ID와 wire enum은 변경하지 않았다.

## 변경 파일

- 신규 `jc-search-runtime` 모듈
  - main Java 41개
  - contract test runner 1개
  - JSON fixture 14개
- 최소 build 변경
  - `jc-backend/settings.gradle.kts`
- 문서
  - `IP-5-SEARCH-RUNTIME-FOUNDATION.md`
  - `IP-5-HANDOFF.md`
  - Intelligence `README.md`
- 검증 증거
  - `verification/ip5/`

## DB 변경

`NONE`

- migration 없음
- 신규 SQL 없음
- canonical SQL `01..26` exact match

## 다른 트랙 영향

- Data: runtime cutover 없음; query retention/persistence owner 결정 필요
- Operations: visibility/eligibility의 실제 decision port는 미연결
- Reliability: search metric/exposure activation 없음
- System Coordination: persistence writer, exposure writer, cursor key owner 미결정
- Recommendation: 타입·정렬·노출·metric 변경 없음

## 검증 결과

| 대상 | 결과 |
|---|---|
| IP-5 runtime | `850 assertions PASS` |
| IP-4 compatibility | `584 assertions PASS` |
| IP-3 Search contract | `425 assertions PASS` |
| IP-1 common contract | `739 assertions PASS` |
| Java 21 compile | `PASS` |
| `-Xlint:all -Werror` | `PASS` |
| JSON fixture | `14/14 PASS` |
| Recommendation direct regression | Foundation/Wave1~7/golden/isolation/P1 17/P2 23 PASS |
| Protected source | `320/320 exact match` |
| SQL | `26/26 exact match` |
| Gradle Wrapper | `BLOCKED before task execution — services.gradle.org DNS resolution failure` |

## 자체 리뷰

- 1차: `9 발견 / 9 수정 / 0 보류`
- 2차: `10 발견 / 10 수정 / 0 보류`
- 보완 후 전체 관련 contract test와 보호 해시 재검증 PASS

## 호환성 결과

- IP-4 compatibility result를 retrieval source로 사용하지 않음
- Recommendation candidate/comparator/exposure 사용 없음
- Legacy offset을 Search cursor로 변환하지 않음
- runtime result는 persistence/API/exposure authority가 없음
- test cursor는 `test_only_unsigned`, production authority false
- `/api/v1/explore` 동작 변경 없음

## 잔여 리스크

1. Gradle distribution 접근 환경에서 Gradle tasks 재실행
2. Operations visibility/eligibility contract와 owner
3. retrieval/index strategy와 source authority
4. SearchRun/snapshot persistence writer와 schema
5. `search_exposure_v1` writer 및 metric gate
6. query privacy retention/access/deletion
7. production cursor signing key와 rotation

## 다음 통합 작업

### 권장

`IP-6 Search Runtime Integration Boundary`

- backend-facing port
- disabled-by-default shadow execution
- legacy comparison harness
- response replacement 없음
- persistence/exposure 없음

### 진입 판정

`READY — FOUNDATION SCOPE ONLY`

### HOLD

- production API cutover
- DB/index/provider wiring
- persistent SearchRun/result/exposure
- production cursor authority
- Operations authority 미확정 상태의 production filtering

## 최종 상태

```text
IP-5: COMPLETE
Search Runtime Foundation: IMPLEMENTED / IN_MEMORY
Search Persistence: NOT IMPLEMENTED
Search Exposure Persistence: NOT IMPLEMENTED
Production Cursor Authority: NOT ENABLED
Search API Wiring: NOT IMPLEMENTED
Search API Cutover: NOT STARTED
Legacy /api/v1/explore Behavior: UNCHANGED
Protected Baseline: MAINTAINED
```
