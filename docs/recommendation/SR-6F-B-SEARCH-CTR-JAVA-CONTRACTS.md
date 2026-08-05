# SR-6F-B Search CTR Java Contracts

## 상태

```text
Stage: SR-6F-B
Metric: search-click-through-rate-v1
Approval: GRANTED_2026-08-05
Java contracts: IMPLEMENTED_PENDING_CI
Database/SQL/identity bridge: UNCHANGED
Projection writer: DISABLED_PENDING_APPROVAL
Merge/deploy/production activation: NOT_PERFORMED
```

## 구현

- `SearchCtrContract`
- `SearchCtrModels`
- `SearchCtrAttributor`
- `SearchCtrCanonicalizer`
- `SearchCtrProjectionPort`

## 계약

- denominator는 평가 window `[start, end)`의 authoritative exposure occurrence다.
- 하나의 CLICK은 동일 opaque subject/session/run/post/rank/query/snapshot/policy 후보 중 가장 최근 exposure 하나에만 귀속한다.
- attribution 시간은 `click >= exposure` 및 `click < exposure + 30분`이다.
- 동률은 `exposedAt DESC`, `receivedAt DESC`, `exposureId ASC`다.
- 하나의 exposure에 여러 CLICK이 있어도 numerator는 한 번만 증가한다.
- CTR은 basis points 정수 내림이며 denominator가 0이면 `null`이다.
- finality 미승인 상태이므로 output status는 `PROVISIONAL`만 생성한다.
- canonical projection에는 numeric user ID, subjectRef, sessionId, raw query를 넣지 않는다.
- persistence port는 `DISABLED_PENDING_APPROVAL`을 반환하며 성공 저장을 가장하지 않는다.

## 검증 대상

- 가장 최근 exposure 1:1 attribution
- 30분 하한 inclusive / 상한 exclusive
- multiple clicks → numerator 최대 1
- session/run/rank/consistency mismatch 제외
- exact tie deterministic ordering
- zero denominator null
- canonical golden fixture와 ordering independence
- disabled projection port

## 비범위

- SQL/DDL/Flyway/canonical database package
- identity bridge function 또는 attribution ledger
- projection store/writer
- endpoint/dashboard/alert
- SETTLED/SUPERSEDED finality
