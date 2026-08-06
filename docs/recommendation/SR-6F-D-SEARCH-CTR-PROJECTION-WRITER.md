# SR-6F-D Search CTR Projection Snapshot Writer

## 상태

```text
Stage: SR-6F-D
Metric: search-click-through-rate-v1
Authorization: GRANTED_2026-08-06
Implementation: VERIFIED
Verified implementation head: d4915d021927483bd833d8f1895543177408a47a
Projection authority: APPEND_ONLY
Writer: SINGLE_SECURITY_DEFINER
Runtime capability: IMPLEMENTED_NOT_ACTIVATED
Endpoint/finality: NOT_IMPLEMENTED
Merge/deploy/production activation: NOT_PERFORMED
Overall: VERIFIED_PROJECTION_WRITER_HOLD_ACTIVATION_AND_FINALITY
```

## 목적

SR-6F-C의 aggregate-only 결과를 identity-free canonical snapshot으로 저장한다.

애플리케이션은 aggregate 값이나 canonical payload를 직접 제공하지 않는다. `jc_reliability` transaction은 다음 입력만 writer에 전달한다.

```text
windowStart
windowEnd
expectedPredecessorProjectionId
idempotencyKey
producerBuildId
```

DB writer가 내부에서 `evaluate_search_ctr_v1`을 호출하고 count, CTR, watermark, canonical payload, fingerprint, deterministic projection ID를 계산한다.

## Authority

```text
search_ctr_projection_snapshot_v1
```

- append-only
- physical owner: `jc_security_owner`
- direct runtime table access 없음
- status: `PROVISIONAL` only
- raw identity 및 item-level attribution 미저장
- canonical payload에서 `computedAt` 제외
- 실행 시각은 row metadata `computed_at`으로 별도 저장

Canonical payload:

```text
attributedExposureCount
ctrBasisPoints
eligibleExposureCount
metricId
metricVersion
sourceMaxReceivedAt
status
windowEnd
windowStart
```

다음 값은 snapshot, payload, writer result에 포함하지 않는다.

```text
numeric user ID
subjectRef
sessionId
exposureId
clickEventId
raw query
```

## Single writer

```text
write_search_ctr_projection_v1
```

- `SECURITY DEFINER`
- owner: `jc_security_owner`
- caller: `jc_reliability` EXECUTE only
- window별 PostgreSQL advisory transaction lock
- aggregate와 canonicalization을 writer 내부에서 수행
- direct INSERT/UPDATE/DELETE 권한 없음

Writer가 `evaluate_search_ctr_v1`을 내부 호출할 수 있도록 `jc_security_owner`에 해당 함수 `EXECUTE`만 부여한다. APP, AUTH, ADMIN, RECOMMENDATION role에는 권한을 추가하지 않으며 raw table 권한도 부여하지 않는다.

## Write result

```text
STORED
DUPLICATE
IDEMPOTENCY_CONFLICT
PREDECESSOR_CONFLICT
```

### `STORED`

- 최초 window는 predecessor 없이 root row 저장
- 변경된 semantic payload는 현재 head와 expected predecessor가 일치할 때만 새 row 저장

### `DUPLICATE`

- 같은 idempotency key와 같은 payload
- 또는 다른 idempotency key라도 현재 head의 canonical payload가 동일한 경우
- 새 snapshot을 생성하지 않는다.

### `IDEMPOTENCY_CONFLICT`

- 같은 idempotency key가 다른 window, payload, producer build과 결속된 경우

### `PREDECESSOR_CONFLICT`

- 변경 payload인데 expected predecessor가 현재 head와 일치하지 않는 경우
- current head를 반환하고 저장하지 않는다.

## Lineage

`predecessor_projection_id`는 append-only replacement lineage다.

```text
root → replacement → replacement
```

이는 finality 상태가 아니다. `SUPERSEDED` 상태를 생성하거나 기존 row를 수정하지 않는다.

## Canonical package

```text
database/journey-connect-db-v2.8/
├─ 06_search_ctr_projection_writer.sql
├─ 06a_search_ctr_writer_owner_dependency.sql
└─ 07_search_ctr_projection_writer_smoke_test.sql
```

Testcontainers bootstrap:

```text
06  ↔ 59_search_ctr_projection_writer.sql
06a ↔ 59a_search_ctr_writer_owner_dependency.sql
07  ↔ 60_search_ctr_projection_writer_smoke_test.sql
```

Source package와 bootstrap SQL의 byte equality를 계약 테스트로 검증한다. Flyway auto-discovery migration은 추가하지 않는다.

## Java integration

- `DatabaseRole.RELIABILITY`
- `DatabaseRole.requiredAtStartup`
- `SearchCtrProjectionPort.WriteCommand`
- `JdbcSearchCtrProjectionStore`
- `SearchCtrProjectionStoreIntegrationTest`

Java adapter는 DB aggregate 결과를 변경하거나 canonical payload를 생성하지 않고 writer 결과만 역직렬화한다.

`jc_reliability`는 허용된 routed role이지만 현재 startup 필수 capability가 아니다. 실제 runtime writer 활성화 전에 restricted backend login membership과 startup verification 활성화를 별도 승인해야 한다.

## 검증 시나리오

- 1/1 → 10000bp root 저장
- 동일 semantic payload → duplicate, row count 유지
- source 변경 후 동일 idempotency key → idempotency conflict
- source 변경 후 predecessor 누락 → predecessor conflict
- expected predecessor 일치 → 2/1, 5000bp replacement 저장
- root/replacement 정확히 2개 row
- append-only UPDATE 거절
- Reliability projection table SELECT/INSERT 거절
- identity-bearing writer result column 부재
- payload identity/computedAt 부재
- zero denominator Java writer integration
- physical owner aggregate EXECUTE 최소 권한
- restricted backend login의 RELIABILITY optional capability
- PostgreSQL 15·18 canonical bootstrap
- focused Search 및 full backend regression

## 구현 중 교정

1. SR-6F-C와 SR-6F-D smoke snapshot이 동일 content uniqueness를 사용하던 문제를 SR-6F-D 전용 canonicalization namespace로 분리했다.
2. `SECURITY DEFINER` writer owner가 aggregate 함수를 호출할 수 없던 문제를 `06a/59a` 최소 EXECUTE 패키지로 교정했다.
3. RELIABILITY를 즉시 startup 필수 role로 강제하던 문제를 선택 capability로 분리해 구현과 운영 활성화를 분리했다.

## CI 검증

Verified implementation head:

```text
d4915d021927483bd833d8f1895543177408a47a
```

### SR Search Recommendation — run `31063302112`

```text
focused Search/PostgreSQL: SUCCESS
protected recommendation contracts: SUCCESS
full backend regression: SUCCESS
focused: 20 suites / 75 tests / failures 0 / errors 0 / skipped 0
full: 99 suites / 349 tests / failures 0 / errors 0 / skipped 0
```

Evidence:

```text
focused artifact: 8952949788
focused digest: sha256:571a2a6fe794fafb6fd00a62b336fe2e6e875a6af3a7266afab172ec5f1e0ee7
full artifact: 8953027730
full digest: sha256:3a1e78a78bca426adf956b5625c07ef6b2e75a070f4387f564d9dea0b1b8a498
```

### Recommendation P0 Database CI — run `31063302036`

```text
PostgreSQL 15: SUCCESS
PostgreSQL 18: SUCCESS
Java/SQL integrity: SUCCESS
canonical PostgreSQL integration: SUCCESS
PG15 artifact: 8952981730
PG15 digest: sha256:1ec9b75c18a394c2e0a6ab4a1485430bddd755124477928c46f404edf12e49ee
PG18 artifact: 8952983200
PG18 digest: sha256:a5f280c0df98430c427cf7d1e5ac7b54ec5727c078a107f909eaebe585f4c920
```

### Backend PR CI — run `31063302095`

```text
IP-12.5 full protected readiness: SUCCESS
artifact: 8953007760
digest: sha256:322e461fe07e4fc18dc0d45228e930fdc692fedc5a2a8609deda155d15ad94a5
```

## 비범위

- public/internal endpoint
- scheduler/cron activation
- restricted backend login의 `jc_reliability` membership 활성화
- dashboard/alert
- `SETTLED`·`SUPERSEDED` finality state
- merge/deploy/production activation
