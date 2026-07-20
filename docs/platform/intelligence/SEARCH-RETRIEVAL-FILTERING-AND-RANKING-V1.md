# Search Retrieval, Filtering and Ranking V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `search-retrieval-ranking-v1` |
| 상태 | `ACTIVE DESIGN / STRATEGY NOT SELECTED` |

## 2. Retrieval 계약

### 2.1 `RetrievalRequestV1`

- run/request reference
- normalized query reference
- entity scope
- canonical filters
- retrieval strategy version
- source registry IDs
- referenceTime
- maximum candidate count
- Operations visibility snapshot/port reference
- producerBuildId

### 2.2 `RetrievalCandidateV1`

필수 의미:

- `entityRef`
- `entityType`
- `sourceId`
- `retrievalSource`
- `retrievalScore` optional
- `sourceRank` optional
- `retrievedAt`
- `sourceSnapshotRef`
- `eligibilityState`
- `candidateMetadataRef`
- `retrievalStrategyVersion`

score가 없는 source에 임의 `0`을 넣지 않는다.

### 2.3 `RetrievalSource`

후보 wire vocabulary:

```text
database_post
database_region
database_tag
database_place
database_user
database_crew
search_index
external_provider
```

`search_index`, `external_provider`는 미래 상태다. 현재 구현된 것으로 서술하지 않는다.

### 2.4 `RetrievalStrategy`

후보 version ID:

```text
database_lexical-v1
database_exact-v1
database_prefix-v1
hybrid-retrieval-v1
```

실제 strategy selection은 IP-5 이후 구현 범위다.

## 3. Filtering 계층

적용 순서와 owner를 분리한다.

| 계층 | 예 | owner |
|---|---|---|
| request/query filter | entity scope, date, region | Search |
| user-selected filter | selected tag/category | Search request |
| system filter | contract support, source freshness | Search |
| moderation visibility | hidden/removed/account state | Operations |
| search eligibility | searchable state | Operations + Search consumption semantics |
| availability/freshness | active place, stale provider fact | source owner/Search policy |
| security/ownership | private resource access | application security/Operations |

unknown visibility 또는 dependency unavailable 상태에서 private/hidden candidate를 permissive하게 포함하지 않는다.

## 4. 과거 evidence와 현재 visibility

- SearchRun/candidate/output snapshot은 생성 당시 evidence로 immutable하다.
- 이후 hide/remove/account change가 발생해도 과거 snapshot을 update하지 않는다.
- delivery 시 현재 visibility를 재검증한다.
- 현재 invisible item은 응답에서 omit한다.
- 동일 snapshot page에서 뒤 순위 item을 끌어와 backfill하지 않는다.
- 사용자에게 최신 결과 집합이 필요하면 새 SearchRun을 생성한다.

## 5. Ranking 계약

### 5.1 `SearchRankingPolicyV1`

- `policyVersion`
- supported scopes/surfaces/sorts
- featureDefinitionVersion
- score semantics version
- missing feature policy
- tie-break tuple
- fallback ordering
- producerBuildId

### 5.2 `SearchRankingFeatureV1`

- feature ID/namespace
- value
- authority class
- definitionVersion
- source snapshot/ref
- observed/reference time
- missing/not-applicable state

Recommendation feature weight나 P1 profile feature를 Search ranking에 직접 재사용하지 않는다. 승인된 adapter와 새 feature definition이 필요하다.

### 5.3 score

- finite `double` only
- NaN/Infinity 금지
- score range는 policy별로 명시
- 서로 다른 policy/source score를 동일 scale로 가정하지 않음
- null은 missing/not-applicable 의미이며 zero가 아님
- source retrieval score와 ranking score 분리
- reranking score와 final rank 분리

### 5.4 rank

- `sourceRank`: retrieval source 내부, nullable, 1-based
- `baseRank`: base ranking 결과, 1-based
- `finalRank`: 최종 Search output, 1-based
- rank는 연속이어야 하며 같은 output 내 entityRef 중복 금지

## 6. Deterministic tie-break

정책은 complete ordering을 고정해야 한다. 기본 후보 tuple:

```text
rankingScore DESC
→ sourceRank ASC NULLS LAST
→ entityType ASC (Unicode/code-point fixed registry order)
→ entityRef ASC (binary/code-point order)
```

`recent` sort 예:

```text
publishedAt DESC
→ entityRef ASC
```

`distance` sort 예:

```text
distanceMeters ASC
→ rankingScore DESC
→ entityRef ASC
```

locale collation 또는 입력 순서에 의존하지 않는다.

## 7. Reranking

`SearchRerankingPolicyV1`은 optional stage다.

- base candidate snapshot을 입력으로 받는다.
- policy/model/provider version을 기록한다.
- baseRank/finalRank와 displacement reason을 보존한다.
- external/model reranker는 inference/provider snapshot evidence가 필요하다.
- Recommendation diversity/exploration reranker를 Search에 직접 호출하지 않는다.

## 8. Fallback ordering

primary relevance ranking 실패 시 허용 가능한 bounded fallback 예:

```text
publishedAt DESC → entityRef ASC
```

fallback은 `status=fallback`과 reason code를 기록한다. 일반 ranking 성공으로 위장하지 않는다.

## 9. 현재 DB capability inventory

- post: feed-oriented `(published_at DESC, id DESC)` indexes
- region: lower(name) expression indexes
- place: `normalized_name` B-tree index
- tag: unique slug, post-tag relation index
- no GIN/trigram/tsvector/vector index
- no dedicated Search tables/snapshots

이 inventory는 future strategy가 아니라 현재 물리 상태다.
