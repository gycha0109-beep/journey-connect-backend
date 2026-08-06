# SR-6F-D Search CTR Projection Snapshot Writer

## 상태

```text
Stage: SR-6F-D
Metric: search-click-through-rate-v1
Authorization: GRANTED_2026-08-06
Implementation: IMPLEMENTED_PENDING_CI
Projection authority: APPEND_ONLY
Writer: SINGLE_SECURITY_DEFINER
Endpoint/finality: NOT_IMPLEMENTED
Merge/deploy/production activation: NOT_PERFORMED
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
└─ 07_search_ctr_projection_writer_smoke_test.sql
```

Testcontainers bootstrap:

```text
06 ↔ 59_search_ctr_projection_writer.sql
07 ↔ 60_search_ctr_projection_writer_smoke_test.sql
```

Source package와 bootstrap SQL의 blob/byte equality를 유지한다. Flyway auto-discovery migration은 추가하지 않는다.

## Java integration

- `DatabaseRole.RELIABILITY`
- `SearchCtrProjectionPort.WriteCommand`
- `JdbcSearchCtrProjectionStore`
- `SearchCtrProjectionStoreIntegrationTest`

Java adapter는 DB aggregate 결과를 변경하거나 canonical payload를 생성하지 않고 writer 결과만 역직렬화한다.

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
- PostgreSQL 15·18 canonical bootstrap
- focused Search 및 full backend regression

## 비범위

- public/internal endpoint
- scheduler/cron activation
- dashboard/alert
- `SETTLED`·`SUPERSEDED` finality state
- merge/deploy/production activation
