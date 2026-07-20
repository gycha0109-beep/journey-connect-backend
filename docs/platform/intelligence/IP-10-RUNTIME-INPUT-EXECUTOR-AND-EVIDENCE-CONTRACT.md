# IP-10 Runtime Input, Executor and Evidence Contract

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-10-runtime-input-executor-evidence-v1` |
| 적용 범위 | test/stage shadow only |
| persistence authority | 없음 |
| exposure authority | 없음 |

## Runtime input

- source: deterministic synthetic in-memory catalog
- legacy response reuse: 금지
- SearchRequest/query/filter/version: IP-3 계약 사용
- Search Runtime: IP-5 `DefaultSearchRuntime` 실제 실행
- visibility/eligibility: fixture explicit allow decision이며 Operations authority가 아님
- unsupported: later offset page, custom sort, unsupported parameter

## Activation validation

`StageSearchShadowProperties`는 profile·mode·explicit allow·sample·timeout·queue·concurrency·topK를 검증한다.

- missing/blank/unknown/invalid → fail-closed disabled
- `prod` 또는 `production` 동시 profile → disabled
- mode는 `test_only`만 허용
- sampling `0..10000`
- timeout `1ms..5s`
- queue `1..128`
- concurrency `1..16`
- topK `1..100`

## Execution boundaries

### Outer dispatch

`StageSearchShadowTaskExecutor`는 request thread에서 bounded submit만 수행한다. task completion을 join하지 않는다.

### Runtime timeout

`StageBoundedSearchShadowExecutionPort`가 Search Runtime future에 wall-clock timeout을 적용하고 timeout/interrupt 시 cancellation한다.

### Failure isolation

- provider factory exception: outer executor failed count로 격리
- queue full/rejected/unavailable: typed submission status
- runtime timeout: `timed_out`
- runtime failure: safe code
- comparison/logging failure: IP-6/IP-7 fail-open contract
- fatal JVM `Error`: 의도적으로 삼키지 않음

## Evidence

`InMemoryStageSearchShadowComparisonLogPort`는 최대 1,000개의 privacy-safe structured record만 메모리에 보존한다. DB/file/Kafka/exposure writer는 없다.

## Authority

```text
legacy response authority = true
shadow response authority = false
persistence authority = false
exposure authority = false
release gate authority = false
production cursor authority = false
```
