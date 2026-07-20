# Search Replay, Failure and Evidence V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `search-replay-evidence-v1` |
| 상태 | `ACTIVE DESIGN` |
| replay enum | IP-1 `exact_replay`, `semantic_replay`, `evidence_replay` |

## 2. Failure와 outcome 분리

### 2.1 Request failure

```text
invalid_request
unsupported_filter
```

### 2.2 Dependency/runtime failure

```text
retrieval_unavailable
provider_unavailable
ranking_failed
snapshot_failed
cursor_invalid
cursor_expired
cursor_mismatch
visibility_dependency_unavailable
```

### 2.3 Outcome reason

```text
no_results
insufficient_candidates
```

`no_results`는 valid query의 정상 empty output일 수 있다. `insufficient_candidates`는 policy가 요구한 최소 후보보다 적지만 output이 유효한 경우 outcome/guardrail로 기록한다. 둘을 자동 failed status로 바꾸지 않는다.

## 3. Fallback

예:

```text
primary retrieval unavailable
→ bounded database lexical retrieval
→ deterministic recent ordering
→ status=fallback
→ fallbackCode=retrieval_unavailable
```

fallback 불변조건:

- output snapshot 필수
- fallbackCode 필수
- primary failure evidence reference
- fallback strategy version
- fallback ordering contract
- success로 위장 금지

## 4. Replay evidence set

Search replay에는 최소 다음이 필요하다.

- original query protected ref
- normalized query
- queryNormalizationVersion
- canonical filters/sort/scope/surface
- identity/context/privacy reference
- referenceTime
- visibility/eligibility snapshot or immutable decision refs
- retrieval strategy/source versions
- source/index/provider snapshot refs
- ordered candidate snapshot
- ranking/reranking policy and featureDefinitionVersion
- deterministic seed if any
- output snapshot and content hash
- producerBuildId
- cursor version/binding if page replay
- external provider/model inference evidence if used

## 5. Replay classes

### 5.1 `exact_replay`

허용 후보:

- immutable DB/index/source snapshot 존재
- deterministic normalization/filter/retrieval/ranking/tie-break
- same policy/feature/build/version
- provider/model 비결정성 없음 또는 exact frozen response snapshot 사용
- output/hash exact comparison 가능

현재 `/api/v1/explore` live JPQL query는 SearchRun/source snapshot이 없으므로 exact replay가 아니다.

### 5.2 `semantic_replay`

- external provider/model reranker 등 bit-level 재현 불가
- input/provider version/parameters가 보존됨
- 승인된 semantic invariant와 ordering quality contract를 재검증 가능

### 5.3 `evidence_replay`

- 당시 input/output/version/provider response evidence를 검토 가능
- 재실행 동일성 또는 semantic equivalence를 보장하지 않음

provider response snapshot이 없으면 exact replay를 선언하지 않는다.

## 6. Explanation evidence

### user

허용 reason code 예:

```text
query_match
region_match
tag_match
popularity_signal
freshness_signal
```

### operator

- policy/version
- source availability
- filter/eligibility counts
- fallback/failure code
- latency buckets

### evaluation

- rank component refs
- candidate partitions
- exposure/result outcome binding
- metric-compatible dimensions

### debug

- restricted internal refs
- raw stack trace를 persisted explanation으로 사용하지 않음

`region_match`와 같은 설명은 source fact인지 query-derived match인지 provenance를 표시한다. popularity/freshness는 사실 자체가 아니라 ranking signal일 수 있다.

## 7. Privacy and logging

일반 로그 금지:

- raw free-form query
- JWT/access/refresh token
- exact coordinates
- private account/profile field
- full candidate/result payload
- canonical snapshot payload
- raw provider response

허용 최소 field:

- request/correlation/run ID
- query fingerprint
- query length/language bucket
- surface/scope
- policy/strategy/build version
- result count bucket
- fallback/failure code
- duration
- replay class

검색어 retention, deletion, legal hold, analyst access는 Data/Operations/System Coordination 승인 사항이다.

## 8. Observability

Search runtime 구현 시 권장 metrics:

- `search_runs_total`
- `search_run_duration_seconds`
- `search_no_results_total`
- `search_fallback_total`
- `search_failure_total`
- `search_candidate_count`
- `search_cursor_invalid_total`
- `search_visibility_omitted_total`
- `search_replay_mismatch_total`

metric 이름과 semantics는 implementation 단계에서 versioned registry를 따른다.
