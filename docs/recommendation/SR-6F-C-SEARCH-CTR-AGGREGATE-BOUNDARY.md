# SR-6F-C Search CTR Aggregate Boundary

## 상태

```text
Stage: SR-6F-C
Metric: search-click-through-rate-v1
Authorization: GRANTED_2026-08-06
Implementation: VERIFIED
Boundary: AGGREGATE_ONLY_SECURITY_DEFINER
Projection writer/endpoint: NOT_IMPLEMENTED
Merge/deploy/production activation: NOT_PERFORMED
Overall: VERIFIED_AGGREGATE_BOUNDARY_HOLD_PROJECTION_WRITER
```

## 목적

`search_exposure_event_v1`의 opaque `subject_ref`와 `recommendation_behavior_event`의 numeric `user_id`를 Reliability에 노출하지 않고 내부에서만 결속해, identity-free Search CTR 집계 한 행만 반환한다.

신규 raw attribution ledger는 만들지 않는다.

## DB 경계

```text
jc_reliability
  → EXECUTE evaluate_search_ctr_v1(windowStart, windowEnd, requester)
  → jc_security_owner SECURITY DEFINER
  → private identity mapping + raw exposure + raw CLICK
  → identity-free aggregate result
```

### `jc_reliability`

- `NOLOGIN`
- `NOSUPERUSER`
- `NOCREATEDB`
- `NOCREATEROLE`
- `NOREPLICATION`
- `NOBYPASSRLS`
- 다른 role 상속·피상속 금지
- mapping, exposure, behavior, audit table 직접 권한 없음
- aggregate 함수 EXECUTE만 허용

### 반환 계약

```text
metricId
metricVersion
windowStart
windowEnd
status
eligibleExposureCount
attributedExposureCount
ctrBasisPoints
computedAt
sourceMaxReceivedAt
```

다음 값은 반환하지 않는다.

```text
numeric user ID
subjectRef
sessionId
exposureId
clickEventId
raw query
```

## 집계 규칙

Denominator:

- `[windowStart, windowEnd)`의 authoritative `search_exposure_event_v1`
- `surface = search`
- `result_entity_type = post`
- `search-item-visible-v1`
- visible ratio 50% 이상
- dwell 1,000ms 이상

Numerator:

- `search-behavior-event-v1`의 `CLICK`
- `surface = search`, `source = search-result-api`
- subject/user mapping, session, run, post, absolute rank, query fingerprint, snapshot fingerprint, policy version exact match
- `click.occurredAt >= exposure.exposedAt`
- `click.occurredAt < exposure.exposedAt + 30 minutes`
- 하나의 CLICK은 가장 최근 exposure 하나에만 결속
- 동일 exposure에 CLICK이 여러 개여도 numerator 최대 1

Tie-break:

```text
exposedAt DESC
receivedAt DESC
exposureId ASC
```

Zero denominator는 `ctrBasisPoints = null`이다. 상태는 finality 승인 전까지 `PROVISIONAL`만 반환한다.

## Identity invalidation

평가 window에 invalidated mapping과 결속된 exposure가 하나라도 존재하면 부분 집계를 반환하지 않는다.

```text
SQLSTATE 23514
search CTR identity bridge unavailable for invalidated mapping
```

## 접근 감사

`search_ctr_evaluation_access_audit_v1`은 다음만 기록한다.

- metric/version
- requester
- window start/end
- accessed/retention timestamps

identity-bearing 값은 기록하지 않으며 30일 controlled retention purge를 사용한다.

## Canonical package

```text
database/journey-connect-db-v2.8/
├─ 04_search_ctr_aggregate_boundary.sql
└─ 05_search_ctr_aggregate_boundary_smoke_test.sql
```

Testcontainers bootstrap:

```text
04 ↔ 57_search_ctr_aggregate_boundary.sql
05 ↔ 58_search_ctr_aggregate_boundary_smoke_test.sql
```

source package와 bootstrap SQL의 byte equality를 계약 테스트로 검증한다. Flyway auto-discovery migration은 추가하지 않는다.

## 검증

Implementation head:

```text
6dc519263df67455b3d37fa6813bd6dae75855ee
```

SR Search Recommendation run `31059853966`:

```text
focused Search/PostgreSQL: SUCCESS
protected recommendation contracts: SUCCESS
full backend regression: SUCCESS
focused: 18 suites / 67 tests / failures 0 / errors 0 / skipped 0
SR-6F-C SQL contracts: 5 PASS
full: 97 suites / 341 tests / failures 0 / errors 0 / skipped 0
```

Evidence:

```text
focused artifact: 8951697788
focused digest: sha256:f90a81654eadbddb30c3eb6997e3e21a318edfbb34684660a7cfd982ddb76e1e
full artifact: 8951776783
full digest: sha256:8811d16055860bb9c9a1a6abeb19291a976598657aacad9639cb713dce00e11b
```

Recommendation P0 Database CI run `31059853959`:

```text
PostgreSQL 15: SUCCESS
PostgreSQL 18: SUCCESS
Java/SQL integrity: SUCCESS
canonical PostgreSQL integration: SUCCESS
PG15 artifact: 8951708778
PG15 digest: sha256:c3eaecabbaa2c7449e6802634ab49a04996db8074c4149d74a124464b2a25142
PG18 artifact: 8951733930
PG18 digest: sha256:c2de3bb620bcbd814a329f0e2dd2bfef2809b579eddc0c4fdda0083c19e0af8a
```

Backend PR CI run `31059854016`:

```text
IP-12.5 full protected readiness: SUCCESS
artifact: 8951756815
digest: sha256:54acd6300e7df3b990e679f9743e7a987362d9c88198d76aa0c506525b85e089
```

검증 시나리오:

- 3 exposure / 1 attributed exposure → 3333 basis points
- exactly 30 minutes CLICK은 upper-exclusive로 제외
- zero denominator → `null`
- wrong requester → `42501`
- invalidated mapping window → `23514`
- Reliability raw table SELECT 거절
- Reliability audit table SELECT 거절
- Reliability purge EXECUTE 거절
- identity-bearing 반환 컬럼 부재
- unexpired audit purge 0건
- PostgreSQL 15·18 canonical bootstrap
- focused Search 및 full backend regression

## 비범위

- projection snapshot table
- projection writer/store
- evaluation endpoint
- dashboard/alert
- `SETTLED`·`SUPERSEDED` finality
- merge/deploy/production activation
