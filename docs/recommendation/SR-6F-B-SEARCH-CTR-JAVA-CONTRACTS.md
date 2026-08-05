# SR-6F-B Search CTR Java Contracts

## 상태

```text
Stage: SR-6F-B
Metric: search-click-through-rate-v1
Approval: GRANTED_2026-08-05
Java contracts: VERIFIED
Database/SQL/identity bridge: UNCHANGED
Projection writer: DISABLED_PENDING_APPROVAL
Merge/deploy/production activation: NOT_PERFORMED
Overall: VERIFIED_CONTRACTS_HOLD_PERSISTENCE_APPROVAL
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

## 검증

Implementation head:

```text
de3456211f9bc36ebceb88643ecba647be81b684
```

SR Search Recommendation run `30994464410`:

```text
focused Search/PostgreSQL: SUCCESS
protected recommendation contracts: SUCCESS
full backend regression: SUCCESS
focused: 17 suites / 62 tests / failures 0 / errors 0 / skipped 0
SR-6F-B: 10 tests PASS
full: 96 suites / 336 tests / failures 0 / errors 0 / skipped 0
```

Evidence:

```text
focused artifact: 8925590765
focused digest: sha256:cbbf5e99d8d913f71b029a03ef8ed8f59fce82052b13b4bf631043d66244d495
full artifact: 8925696258
full digest: sha256:f63606ae43505cccbe5b564de3229b1f25f1f56a3d346f0831947849b08d354c
```

Recommendation P0 Database CI run `30994464343`:

```text
PostgreSQL 15: SUCCESS
PostgreSQL 18: SUCCESS
PG15 artifact: 8925632453
PG15 digest: sha256:e28ab1a7490a603a2ad81f411770b70fec6988bba5890047d6d37b2ed5e756af
PG18 artifact: 8925639430
PG18 digest: sha256:d68e395f853f4b58a61c43d087dbef48759070e4d95486a4fc415eddaababac1
```

Backend PR CI run `30994464378`:

```text
IP-12.5 full protected readiness: SUCCESS
artifact: 8925678233
digest: sha256:876b7ee3c8132bbe12bdfeef5ad260cfa7c534f5e8ccf1b21e448d9645a8dce0
```

검증 시나리오:

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
