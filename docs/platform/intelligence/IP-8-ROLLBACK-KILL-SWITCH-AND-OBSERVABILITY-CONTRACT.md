# IP-8 Rollback, Kill-switch & Observability Contract

## 문서 정보

| 항목 | 값 |
|---|---|
| budget 계약 ID | `search-shadow-execution-budget-v1` |
| kill-switch 계약 ID | `search-shadow-kill-switch-v1` |
| rollback 계약 ID | `search-shadow-rollback-v1` |
| observability 계약 ID | `search-shadow-observability-retention-v1` |
| 상태 | `ACTIVE CONTRACT / VALUES_AND_OWNERS_UNRESOLVED` |

## 1. 범위

이 문서는 future controlled hook에 필요한 운영 계약을 정의한다. 실제 remote flag, executor, persistent sink, exposure writer를 연결하지 않는다.

## 2. Kill-switch

| Priority | 수단 | Shadow 동작 | Legacy response | 배포 |
|---:|---|---|---|---|
| 1 | global disabled | 즉시 skip | unchanged | provider에 따라 미정 |
| 2 | profile disabled | skip | unchanged | profile 방식에 따라 미정 |
| 3 | sample rate 0 | 전부 제외 | unchanged | 동적 config 승인 시 무배포 가능 |
| 4 | circuit open | shadow only 차단 | unchanged | 불필요 |
| 5 | executor unavailable | submit skip | unchanged | 불필요 |

원칙:

- shadow activation: fail-closed
- legacy response: fail-open
- missing/blank/invalid config: disabled
- propagation delay, owner, audit는 unresolved

## 3. Rollback levels

| Level | 조치 | 배포 | response 영향 | evidence |
|---|---|---:|---:|---|
| L0 | sample 0 | 조건부 없음 | none | 기존 evidence 보존 |
| L1 | mode disabled | 조건부 없음 | none | 기존 evidence 보존 |
| L2 | no-op bean 교체 | 필요 가능 | none | 기존 evidence 보존 |
| L3 | hook call 제거 | 필요 | none | 기존 evidence 보존 |
| L4 | module dependency 제거 | 필요 | none | 기존 evidence 보존 |

각 단계 후 `disabled-mode equivalence`, `ip8SearchRegressionClosure`, backend test를 다시 실행한다.

## 4. Executor/performance contract

다음 production 값은 모두 `UNRESOLVED`다.

- maxConcurrency
- queueCapacity
- taskTimeout
- endToEndShadowBudget
- hook/submission/queue/runtime/comparison/logging duration budget

불변조건:

- bounded queue/concurrency
- common ForkJoinPool/unmanaged thread 금지
- request thread full runtime 금지
- reject/queue full/unavailable/circuit open은 skip
- late result discard
- cancellation/interrupt 정책은 owner 승인 필요

## 5. Latency/error measurement

구분:

```text
hook_dispatch_overhead
executor_submission_overhead
queue_wait
runtime_duration
comparison_duration
logging_duration
total_shadow_duration
```

상태:

```text
submitted / skipped / rejected / queue_full / timed_out / cancelled
runtime_failed / comparison_failed / logging_failed / completed
```

이는 P2 metric, Search exposure metric, API SLO 또는 release gate가 아니다.

## 6. Observability record

허용:

- timestamp/referenceTime
- correlation fingerprint
- mode/sampling/dispatch/runtime/comparison status
- deterministic mismatch/severity/count
- duration bucket
- policy/build version

금지:

- raw query 또는 normalized query text
- full request/response/candidate
- token/raw user/session ID
- precise location/private metadata
- sensitive stack trace

## 7. Retention prerequisite

다음은 `UNRESOLVED`다.

- storage owner
- access roles
- retention duration
- deletion process
- incident hold
- aggregation/anonymization
- sampling
- audit

따라서 current no-op/in-memory port를 persistent observability로 승격할 수 없다.

## 8. Authority

```text
persistenceAuthority = false
exposureAuthority = false
metricAuthority = false
releaseGateAuthority = false
productionCursorAuthority = false
productionActivationAuthority = false
responseAuthority = legacy
```
