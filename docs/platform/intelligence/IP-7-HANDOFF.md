# IP-7 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-7-handoff-v1` |
| 상태 | `COMPLETE` |
| 다음 후보 | `IP-8 Search Shadow Activation Readiness & Regression Closure` |

## 완료

- `jc-search-shadow-wiring` 독립 모듈
- backend-facing generic shadow hook와 no-op default
- test-only profile/explicit activation gate
- deterministic default-zero sampling
- finite executor/circuit/backpressure/timeout/cancellation contract
- IP-6 dispatcher와 response identity guard
- non-persistent logging port와 privacy-safe record
- 16 JSON fixture와 독립 contract runner

## 기준선

- Recommendation P0/P1/P2 technical: `CLOSED`
- P2 production: `HOLD`
- IP-2..IP-6: `COMPLETE`
- protected source: `320/320`
- canonical SQL: `01..26`

## 변경 계약 ID

- `search-shadow-wiring-v1`
- `search-shadow-structured-record-v1`
- `ip-7-handoff-v1`

## 변경 파일

- 신규 `jc-search-shadow-wiring/**`
- `jc-backend/settings.gradle.kts`: 신규 모듈 등록만 추가
- Intelligence README index
- IP-7 본문/Handoff/verification 증거

## DB 변경

없음.

## Authority 결과

- legacy response authority: 유지
- response mutation: false
- persistence/exposure/release/metric/cursor/activation/cutover authority: false
- production hook: 미삽입
- production config activation: 없음

## 검증 결과

- IP-7: `1700 assertions PASS`
- IP-6: `972 PASS`
- IP-5: `850 PASS`
- IP-4: `584 PASS`
- IP-3: `425 PASS`
- IP-1: `739 PASS`
- Recommendation Foundation/Wave1~7/Golden/Isolation: PASS
- P1 Core: 17 PASS
- P2 Core: 23 PASS
- Java 21 `-Xlint:all -Werror`: PASS
- protected source: `320/320 exact`
- SQL: `26/26 exact`

Gradle wrapper는 `services.gradle.org` DNS 해석 실패로 distribution을 내려받지 못했다. Gradle PASS는 선언하지 않았으며 직접 compile/test runner로 보완했다.

## 자체 리뷰

- 1차: `3 발견 / 3 수정 / 0 보류`
- 2차: `4 발견 / 4 수정 / 0 보류`
- 보완 후 전체 관련 회귀와 해시 재검증 PASS

## 호환성/production 영향

- `/api/v1/explore` controller/service/repository/DTO/response 동작 변경 없음
- IP-4 compatibility output은 runtime candidate source가 아님
- production Spring bean/executor/profile 설정 없음
- Search/Recommendation dependency 분리 유지

## 잔여 리스크

- runtime input source와 retrieval/index 전략
- visibility/eligibility owner
- persistence/exposure writer
- retention/privacy owner
- executor/performance budget
- production activation/rollback authority

## 다음 작업

### 진입 판정

`READY — ACTIVATION READINESS / REGRESSION SCOPE ONLY`

### 허용 후보

- protected source hook 필요성·proposal 검토
- disabled-mode production-equivalence test
- bounded executor/performance budget 계약
- kill-switch/rollback·logging retention 계약
- full backend regression

### HOLD

- production shadow activation
- Search API cutover/response replacement
- DB/provider/index/persistence/exposure
- production cursor/release gate

## 최종 상태

```text
IP-7: COMPLETE
Shadow Wiring Capability: IMPLEMENTED
Shadow Activation: DISABLED_BY_DEFAULT
Production Hook/Activation: NOT ENABLED
Legacy /api/v1/explore: UNCHANGED
Protected Baseline: MAINTAINED
```
