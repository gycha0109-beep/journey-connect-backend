# SR-6F Search CTR Approval Record

## 상태

```text
Decision date: 2026-08-05 KST
Decision: APPROVED_BY_PROJECT_OWNER
Metric: search-click-through-rate-v1
SR-6F-A design: APPROVED
SR-6F-B Java contracts: AUTHORIZED
Database/SQL/identity bridge: NOT_AUTHORIZED_IN_THIS_STAGE
Merge/deploy/production activation: NOT_AUTHORIZED
```

## 승인된 계약

- denominator는 authoritative `search_exposure_event_v1` item occurrence다.
- numerator는 CLICK row 수가 아니라 CLICK이 하나 이상 귀속된 exposure 수다.
- attribution window는 exposure 이후 30분이며 하한 inclusive, 상한 exclusive다.
- 하나의 CLICK은 가장 최근 eligible exposure 하나에만 귀속한다.
- 동률은 `exposedAt DESC`, `receivedAt DESC`, `exposureId ASC` 순서로 결정한다.
- 하나의 exposure에 여러 CLICK이 있어도 numerator는 최대 1이다.
- zero denominator는 0%가 아니라 `null`이다.
- V1 상태는 `PROVISIONAL`만 허용하며 finality 승인 전 `SETTLED`를 생성하지 않는다.
- Reliability는 identity mapping table이나 numeric user ID ↔ subject pair를 직접 읽지 않는다.

## SR-6F-B 허용 범위

- Java metric contract
- deterministic in-memory attribution engine
- canonical projection payload와 SHA-256 fingerprint
- disabled projection port
- golden fixture와 단위 테스트
- DB/Flyway/canonical SQL 비변경

## 계속 금지

- SQL sequence 또는 DB version 자체 배정
- identity bridge function/ledger 구현
- projection writer/store 활성화
- user/subject/session/raw query segment
- production dashboard, alert, merge, deploy
