# SR-6F Search CTR Approval Request

## 상태

```text
Stage: SR-6F-A
Request: SYSTEM_COORDINATION_REVIEW_REQUIRED
Metric: search-click-through-rate-v1
Runtime/SQL: NOT_STARTED
Merge/deploy: NOT_AUTHORIZED
```

## 승인 요청

| 결정 항목 | 제안 | 승인 필요 역할 |
|---|---|---|
| metric semantic owner | Reliability | System Coordination + Reliability |
| denominator | eligible `search_exposure_event_v1` item occurrence | Reliability |
| numerator | attributed exposure count, CLICK count 아님 | Reliability |
| attribution window | exposure 이후 30분, 상한 exclusive | Reliability |
| click assignment | 가장 최근 eligible exposure 1개 | Reliability + Intelligence/Search |
| identity bridge | aggregate-only `SECURITY DEFINER` | System Coordination + Privacy/Security |
| Reliability mapping access | 직접 SELECT 금지 | System Coordination + Privacy/Security |
| implementation model | aggregate function 또는 identity-free attribution ledger 중 택1 | System Coordination + Data + Reliability |
| finality | late-arrival watermark 별도 확정 | Reliability + Data |
| zero denominator | CTR null | Reliability |
| aggregate retention | 400일 후보 | Reliability + Data |
| DB target/sequence | 미배정 | System Coordination |
| production dashboard | 비활성 | Operations + Reliability |

## 필수 선택 1 — identity bridge

### Option A: aggregate-only function

```text
jc_security_owner function
→ private mapping join
→ aggregate counts only return
```

장점:

- identity pair를 별도 row로 남기지 않음
- Reliability에 mapping 권한이 필요 없음

주의:

- 계산 비용과 재현성
- query fingerprint/version 보호 필요

### Option B: identity-free attribution ledger

```text
search_click_attribution_v1
exposure_id + click_event_id + version + timestamp
```

장점:

- projection 재현성과 증분 처리 용이
- 1:1 attribution을 append-only evidence로 보호 가능

주의:

- 신규 table/writer/retention/sequence 승인 필요
- 잘못된 attribution correction contract 필요

## 필수 선택 2 — late arrival/finality

현재 behavior replay 허용 범위 때문에 다음 중 하나를 결정해야 한다.

1. CTR 대상 CLICK의 별도 late-arrival 제한
2. 긴 provisional 기간 후 settlement
3. settled snapshot에 superseding correction 허용

결정 전에는 aggregate snapshot을 final authority로 활성화하지 않는다.

## 승인 시 구현 가능 범위

### 부분 승인

다음만 가능하다.

- Java metric contract
- deterministic attribution fixture
- zero denominator/30분 경계 테스트
- no-op projection port
- SQL/DB 비변경

### 전체 승인

다음 구현에 추가 승인이 필요하다.

- identity bridge function 또는 attribution ledger
- role/grant
- projection writer/evaluator
- aggregate snapshot
- retention
- PostgreSQL integration

### production 승인

다음이 모두 필요하다.

- SR-6E 실제 프론트 노출 수집 검증
- 충분한 nonproduction exposure/CLICK fixture
- privacy negative test
- Reliability metric acceptance
- Operations dashboard/alert 승인
- full regression
- independent review

## 보호 기준

- 일반 recommendation exposure/P2 exposure 비변경
- behavior IMPRESSION denominator 사용 금지
- numeric user ID/subject_ref 동시 노출 금지
- raw query/user/session segment 금지
- mapping invalidation fail-closed 정책 필요
- 신규 SQL 번호 자체 배정 금지
- merge·deploy·production activation 금지

## 요청 결과 형식

```text
Decision: APPROVED | PARTIALLY_APPROVED | REJECTED
Identity bridge: AGGREGATE_FUNCTION | ATTRIBUTION_LEDGER
Attribution rule: MOST_RECENT_ELIGIBLE_EXPOSURE
Window: 30_MINUTES_EXCLUSIVE_UPPER
Late arrival policy: <value>
Finality policy: <value>
Aggregate retention: <value>
DB target: <value>
SQL sequence: <value>
Physical writer: <value>
Production activation: NOT_AUTHORIZED | AUTHORIZED
```
