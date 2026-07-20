# IP-4 Existing Search Read Adapter and Compatibility

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `IP-4` |
| 단계명 | `Existing Search Read Adapter & Compatibility` |
| 계약 ID | `legacy-explore-read-adapter-v1` |
| 상태 | `COMPLETE / READ_ONLY_COMPATIBILITY` |
| Search Runtime | `NOT IMPLEMENTED` |
| Search Ranking | `NOT IMPLEMENTED` |
| Search Persistence | `NOT IMPLEMENTED` |
| Search Exposure Persistence | `NOT IMPLEMENTED` |
| Search API Cutover | `NOT STARTED` |
| Legacy Explore Behavior | `UNCHANGED` |
| 기준 DB | `journey-connect-db-v2.7/01..26` |

## 2. 목적

IP-4는 기존 `GET /api/v1/explore`를 Search Runtime으로 교체하지 않는다. 현재 legacy explore 요청과 `PageResponse<PostDtos.Summary>` 결과를 변경하지 않고, 별도 순수 Java compatibility layer에서 Search 계약과 함께 읽을 수 있는 비권위 표현으로 매핑한다.

```text
Legacy Explore Request / Response
        ↓ read-only, no production wiring
jc-search-compatibility
        ↓
Search-compatible query/filter/context + legacy page/item metadata
```

다음 변환은 금지한다.

```text
legacy offset page ≠ SearchCursorV1
legacy latest order ≠ authoritative Search ranking
legacy repository call ≠ SearchRunV1
legacy response delivery ≠ search_exposure_v1
```

## 3. 보호 기준선

- Recommendation P0/P1: `CLOSED`
- Recommendation P2 technical: `CLOSED`
- Recommendation P2 production: `HOLD`
- IP-1/IP-1.10: `CLOSED`
- IP-2: `COMPLETE / CONTRACT_ONLY`
- IP-3: `COMPLETE`
- protected source: `320/320 SHA-256 exact match`
- canonical SQL: `01..26 exact match`
- backend Controller/Service/Repository/JPQL: unchanged

P2 HOLD와 recommendation exposure/metric authority는 변경하지 않았다.

## 4. 실제 legacy explore inventory

### 4.1 HTTP와 요청

| 항목 | 실제 확인 결과 |
|---|---|
| endpoint | `GET /api/v1/explore` |
| 인증 | SecurityConfig에서 `permitAll`; Controller method에 `Jwt` 인자 없음 |
| keyword | optional `String`; `null`/blank → `null`, 그 외 `trim()` |
| region | optional `String`; `null`/blank → `null`, 그 외 `trim()` 후 `Locale.ROOT` lower-case |
| pagination | Spring Data `Pageable`; `@PageableDefault(size = 20)` |
| page base | 프로젝트 override가 없어 Spring Data 기본 zero-based 해석 |
| 명시적 project max | 없음; adapter는 framework boundary와 독립된 compatibility guard로 `2000` 사용 |
| client sort | `Pageable`가 받을 수 있으나 explicit JPQL order와의 최종 조합을 보호하는 전용 테스트가 없음; IP-4는 non-empty sort를 `unsupported` 처리 |
| 사용자별 차이 | 없음; subject/personalization을 입력으로 사용하지 않음 |

### 4.2 Repository predicate

`JourneyPostRepository.explore`는 다음만 조회한다.

- `PostStatus.PUBLISHED`
- `PostVisibility.PUBLIC`
- `PostModerationStatus.VISIBLE`
- author `accountStatus = active`
- keyword가 있으면 title/content/region local/ko/en name의 case-insensitive substring
- region이 있으면 slug/local/ko/en name의 case-insensitive exact match
- `publishedAt DESC, id DESC`

이는 lexical JPQL filter와 deterministic latest ordering이다. retrieval score, ranking feature, model, Search policy selector는 없다.

### 4.3 응답

`PageResponse<PostDtos.Summary>`:

- items
- page
- size
- totalElements
- totalPages
- last

`PostDtos.Summary`:

- id
- title
- regionCode
- regionName
- coverImageUrl
- viewCount
- likeCount
- bookmarkCount
- author(id, nickname, profileImageUrl)
- createdAt

Repository ordering key는 `publishedAt`, 응답 timestamp는 `createdAt`이다. Adapter는 이 둘을 동일한 값으로 추정하지 않는다.

### 4.4 조회 특성 및 테스트

- repository query는 `author`, `region` EntityGraph를 사용한다.
- like/bookmark count는 page item IDs 기준 bulk count query로 조립한다.
- cover image는 Summary 변환에서 읽지만 explore 전용 query-count 테스트는 없다.
- `PostListQueryIntegrationTest`는 feed 목록의 고정 query 수를 검증하며 explore 전용 동등성 테스트는 존재하지 않는다.
- 현재 SearchRun, Search snapshot, Search cursor, search exposure writer/table은 없다.

## 5. 모듈과 dependency

신규 모듈:

```text
jc-search-compatibility
  → jc-search-contracts
      → jc-intelligence-contracts
```

금지 dependency:

- `jc-backend`
- `jc-recommendation-core`
- Spring/JPA/HTTP/DB

별도 모듈을 선택한 이유:

1. production DTO/Controller annotation 수정 방지
2. backend/Spring 역의존 방지
3. Recommendation compatibility와 의미 분리
4. standalone `javac` 및 contract test 가능
5. component scanning과 runtime bean 자동 활성화 차단

`LegacyExploreReadPort`는 인터페이스만 존재하며 production implementation과 bean은 없다.

## 6. adapter architecture

### 6.1 legacy mirror read model

- `LegacyExploreRequestView`
- `LegacyExplorePageView`
- `LegacyExploreItemView`
- `LegacyExploreAuthorView`
- `LegacyExploreSortOrderView`

Mirror는 실제 request/response 필드만 반영한다. production DTO를 수정하거나 Search annotation을 붙이지 않는다.

### 6.2 역할 분리

| 타입 | 책임 |
|---|---|
| `LegacyExploreRequestMapper` | keyword/region/context/legacy page 요청을 비권위 Search 표현으로 변환 |
| `LegacyExplorePageMapper` | offset page metadata 검증·보존 |
| `LegacyExploreResultMapper` | item order/ID 보존과 Search entityRef 생성 |
| `LegacyExploreCompatibilityAdapter` | 단계 orchestration 및 typed result/evidence 생성 |
| `LegacyExploreFingerprintV1` | request/response memory evidence SHA-256 |
| `LegacyExploreJsonCodecV1` | mirror fixture JSON load/round-trip |

단일 거대 mapper를 사용하지 않는다.

## 7. request mapping

| legacy | mapped representation | authority |
|---|---|---|
| null/blank keyword | `SearchQueryMode.BROWSE`, raw/normalized query absent | compatibility only |
| text keyword | IP-3 `SearchQueryCanonicalizerV1` | canonical query contract |
| region | `SearchFilterV1(REGION, USER_SELECTED)` | request fact only |
| default repository order | `SearchSortV1(RECENT, legacy-explore-order-v1)` | legacy-order descriptor; Search ranking authority 없음 |
| page/size | legacy compatibility metadata | offset only |
| request/session/referenceTime | `SearchContextV1(EXPLORE, POST)` | adapter context |

`SearchRequestV1`은 생성하지 않는다. Legacy request에는 snapshot-bound cursor와 authoritative ranking/feature version이 없기 때문이다.

Client-provided non-empty Pageable sort와 endpoint에 없는 추가 filter는 조용히 무시하지 않고 `unsupported` typed result로 반환한다.

## 8. result mapping

각 item에서 확인 가능한 값만 매핑한다.

| 필드 | 처리 |
|---|---|
| ID | `post:<positive-id>` |
| entity type | `post` |
| source position | legacy list index + 1 |
| original fields/order | exact preservation |
| final position | `null` |
| retrieval score | `null` |
| retrievedAt | `null` |
| source snapshot | `null` |
| eligibility | `unknown` |
| visibility evidence | `unknown` |

Legacy repository가 visible predicate를 적용한다는 사실은 inventory로 보존하지만, response payload 자체에 per-item visibility/eligibility snapshot이 없으므로 adapter output에서 confirmed authority로 승격하지 않는다.

중복 post ID, missing required field, negative count, invalid page metadata는 empty success로 삼키지 않고 전체 mapping failure로 반환한다. Partial result를 success로 노출하지 않는다.

## 9. pagination compatibility

선택: **A — legacy pagination metadata를 compatibility extension으로 보존**.

- page, size, offset, totalElements, totalPages, last 보존
- `cursorAvailable = false`
- SearchCursor 생성 없음
- query/filter/policy/run/snapshot binding 없음
- legacy page mutation 안정성을 Search V1 snapshot 안정성과 동일시하지 않음

Same `publishedAt` candidate ordering은 repository `id DESC` tie-break로 현재 source order에 반영되지만, 응답 DTO에 `publishedAt`이 없으므로 adapter가 ordering tuple을 생성하지 않는다.

## 10. explanation

확정 가능한 compatibility fact만 남긴다.

- legacy query predicate가 요청됨
- legacy region predicate가 요청됨
- repository default order가 `publishedAt DESC, id DESC`

생성하지 않는 설명:

- match field
- relevance score
- semantic similarity
- personalization
- popularity boost

`searchRankingAuthority=false`, `matchFieldAuthority=false`를 생성자 불변조건으로 고정한다.

## 11. failure/result contract

상태:

- `success`
- `mapping_failure`
- `unsupported`
- `invalid_input`

주요 stable failure code:

- `invalid_legacy_request`
- `unsupported_legacy_filter`
- `unsupported_legacy_sort`
- `missing_required_legacy_field`
- `invalid_page_metadata`
- `invalid_timestamp`
- `contract_validation_failure`
- `legacy_payload_inconsistency`
- `duplicate_item_reference`

예외 message를 client 분기 기준으로 사용하지 않는다.

## 12. compatibility evidence

Memory-only evidence:

- adapterVersion
- legacyEndpointId
- legacyRequestFingerprint
- legacyResponseFingerprint
- mappingPolicyVersion
- mappedAt
- producerBuildId
- source/mapped/rejected item count
- warningCodes
- runtime/persistence/replay/exposure authority flags

모든 authority flag는 반드시 `false`다. Evidence는 DB/event/log persistence를 구현하지 않는다. Fingerprint는 raw query/response를 저장하지 않는 domain-separated SHA-256이다.

## 13. fixtures

16개:

1. empty query first page
2. keyword query first page
3. keyword query later page
4. empty result
5. multiple items same createdAt
6. unsupported filter
7. invalid page
8. null optional field
9. hidden/deleted excluded legacy response
10. legacy response field missing
11. maximum page size
12. Unicode Korean query
13. whitespace-heavy query
14. mixed-case Latin query
15. duplicate item reference
16. unsupported sort

Fixture item schema는 실제 `PostDtos.Summary` 필드와 일치한다.

## 14. 단계별 기록

### IP-4.1 Baseline & Legacy Inventory

- 목적: `/explore` 실제 request/query/response/visibility/pagination 확인
- 변경 파일: 조사·verification 문서
- 구현: 없음
- 검증: Controller/Service/Repository/DTO/SecurityConfig/test 대조
- 보완: response `createdAt`을 repository ordering `publishedAt`으로 오인하지 않도록 분리
- 잔여 리스크: client Pageable sort의 실제 SQL 조합 전용 테스트 없음

### IP-4.2 Compatibility Module Foundation

- 목적: backend production wiring 없는 독립 module
- 변경: `jc-search-compatibility`, settings module 등록
- 구현: pure Java 21, Search contract dependency only
- 검증: forbidden dependency 및 runtime annotation scan
- 보완: backend 내부 package 대신 별도 module 선택
- 잔여 리스크: Gradle distribution 접근 가능한 환경의 task 재실행 필요

### IP-4.3 Read Model & Request Mapping

- 목적: legacy request 의미를 손실 없이 읽기
- 구현: mirror types, raw/normalized query, region filter, context, legacy recent descriptor
- 검증: blank/Unicode/whitespace/case/max boundary/unsupported cases
- 보완: `SearchRequestV1` 및 fake cursor 생성 금지
- 잔여 리스크: actual runtime request/correlation extraction은 production wiring 범위

### IP-4.4 Result/Page Mapping

- 목적: item count/order/ID/page metadata 보존
- 구현: source position, post entityRef, unknown authority states
- 검증: duplicate/missing field/same timestamp/later page/empty result
- 보완: score/snapshot/final rank/retrievedAt 생성 금지
- 잔여 리스크: per-item visibility snapshot은 Operations contract 이후 필요

### IP-4.5 Failure & Evidence

- 목적: invalid/unsupported/mapping failure 명시
- 구현: typed result, stable code, fail-fast no partial success, memory evidence
- 검증: deterministic fingerprints, warning order, count consistency
- 보완: request 단계 실패 item을 rejected로 오집계하지 않도록 stage별 evidence 분리
- 잔여 리스크: evidence retention/persistence는 미구현

### IP-4.6 Regression & Review

- 목적: IP-3/IP-1 및 protected baseline 유지
- 검증: compatibility 584, IP-3 425, IP-1 739 assertions
- 보호: 320/320 source, SQL 26/26 exact
- Gradle: wrapper 실행 시 `UnknownHostException: services.gradle.org`; PASS 미선언
- 대체: Java 21 `javac --release 21 -Xlint:all -Werror` 및 direct main runners

## 15. 자체 리뷰

### 리뷰 1 — 계약/아키텍처

발견 8 / 수정 8 / 보류 0

1. backend DTO direct dependency 가능성 → mirror read model과 별도 module
2. legacy page를 SearchCursor로 변환할 위험 → cursor field 자체 미생성 및 false invariant
3. legacy call을 SearchRun으로 위장할 위험 → SearchRun 미생성, authority false
4. repository visibility predicate를 payload evidence로 과장 → states `unknown`
5. missing/duplicate item partial success 가능성 → fail-fast typed mapping failure
6. non-empty Pageable sort 의미 불확실 → unsupported fail-closed
7. compatibility explanation의 relevance 과장 → predicate/order fact만 허용
8. Spring bean 자동 활성화 위험 → framework dependency/annotation 전부 금지

### 리뷰 2 — 구현 품질/결정론/보안

발견 10 / 수정 10 / 보류 0

1. request-stage failure가 response items를 rejected로 오집계 → stage별 count 수정
2. request context validator exception이 typed result 밖으로 누출 가능 → typed contract failure로 변환
3. page total보다 item 수가 큰 payload 허용 → consistency check 추가
4. page count/offset arithmetic overflow 가능성 → overflow-safe 계산
5. invalid payload fingerprint를 all-zero 값으로 표시 → domain-separated unavailable fingerprint
6. JSON fractional number가 integer field로 truncation 가능 → integral validation

보완 후 584/425/739 assertions와 보호 hash를 재검증했다.

## 16. 검증 결과

| 검증 | 결과 |
|---|---|
| main/test compile | PASS — Java 21, `-Xlint:all -Werror` |
| compatibility contract | 584 assertions PASS |
| IP-3 Search contract | 425 assertions PASS |
| IP-1 common contract | 739 assertions PASS |
| fixture | 16/16 load/expected status PASS |
| order/item/page preservation | PASS |
| fake cursor/run/exposure | 없음 |
| deterministic mapping/fingerprint | PASS |
| runtime bean | 없음 |
| Controller/Service/Repository/JPQL | unchanged |
| protected source | 320/320 exact |
| canonical SQL | 26/26 exact |
| Gradle Wrapper | 실행 실패 — distribution DNS; PASS 미선언 |

## 17. 잔여 리스크와 IP-5 gate

- production request/response adapter wiring 미구현
- client Pageable sort 최종 SQL 의미 미고정
- immutable Search result snapshot 미구현
- visibility eligibility port/snapshot 미구현
- Search exposure persistence 미구현
- Gradle distribution 접근 가능한 환경에서 전용 task 재실행 필요

IP-5는 no production API wiring, no DB persistence 범위의 in-memory Search Runtime Foundation으로만 진입한다.
