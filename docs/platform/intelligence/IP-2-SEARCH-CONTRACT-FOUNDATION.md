# IP-2 Search Contract Foundation

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `IP-2` |
| 단계명 | `Search Contract Foundation` |
| 계약 ID | `jc-search-contract-foundation-v1` |
| 상태 | `COMPLETE / CONTRACT_ONLY` |
| 소유 트랙 | Intelligence Platform / Search Intelligence |
| production 구현 | `NOT STARTED` |
| search exposure persistence | `NOT IMPLEMENTED` |
| DB/SQL | `UNCHANGED` |
| 기준 DB | `journey-connect-db-v2.7/01..26` |

## 2. 기준선과 자료 우선순위

IP-2는 다음 보호 기준선을 변경하지 않는다.

- Recommendation P0: `CLOSED`
- Recommendation P1: `CLOSED`
- Recommendation P2 technical: `CLOSED`
- Recommendation P2 production: `HOLD`
- IP-0: `COMPLETE`
- SC-1: `COMPLETE`
- IP-1: `CLOSED`
- IP-1.10 Integration Regression Closure: `CLOSED`
- Backend: `84/84 PASS`
- Intelligence contract module: `739 assertions PASS`
- Recommendation compatibility adapter: `226 assertions PASS`
- P1 Core: `17/17 PASS`
- P2 Core: `23/23 PASS`
- Recommendation Foundation/Wave1..7/Golden/Isolation: `PASS`
- protected source: `320/320 SHA-256 exact match`
- canonical SQL: `01..26 exact match`

`P2 HOLD`는 기술 실패가 아니라 실제 CANARY 표본과 운영 승인 대기 상태다.

### 2.1 자료 provenance 제한

이 작업 환경에는 요청에 명시된 `Journey-Connect-IP-1.10-Integration-Regression-Closure-Final.zip` 이름의 archive가 직접 마운트되지 않았고, 가장 가까운 전체 프로젝트 archive인 `Journey-Connect-IP-1-Common-Contracts-Validation-Recommendation-Adapter.zip`과 최신 SC-1/IP-0/DP-0 단독 문서가 제공되었다. 따라서 다음 원칙을 적용했다.

1. 이관 프롬프트의 IP-1.10 CLOSED 및 Backend 84/84 상태는 현재 확정 baseline으로 유지한다.
2. 실제 source inventory와 보호 hash는 제공된 전체 프로젝트 archive에서 다시 확인한다.
3. production source와 SQL은 수정하지 않는다.
4. IP-1.10 추가 회귀 실행 자체를 IP-2에서 재실행했다고 주장하지 않는다.

이 제한은 contract-only IP-2 설계 완료를 막지 않지만, source archive provenance로 [IP-2 Handoff](IP-2-HANDOFF.md)에 남긴다.

## 3. 목적

IP-2는 Search runtime을 구현하지 않는다. Journey Connect의 향후 Search Intelligence가 따라야 할 도메인 계약을 먼저 고정한다.

공통 `IntelligenceRunV1`, input/candidate/output snapshot, provenance, version, replay evidence는 IP-1 계약을 보호 consumer로 사용한다. 다만 다음 의미는 Recommendation과 분리한다.

- query와 normalization
- retrieval source와 strategy
- query/user/system/visibility filter
- search relevance ranking과 reranking
- stable ordering
- snapshot-bound pagination과 cursor
- search result exposure
- search explanation
- no-result, fallback, failure
- search replay evidence

## 4. 범위

IP-2가 확정한다.

1. Search 책임과 비책임
2. 현재 검색·탐색 구현 inventory
3. Search request/query/context/filter/sort/scope/surface 계약
4. query normalization V1
5. retrieval candidate와 source/strategy/snapshot 계약
6. filtering 및 Operations visibility 경계
7. ranking/reranking/tie-break 계약
8. snapshot-bound pagination과 cursor 계약
9. SearchRun과 IP-1 common contract mapping
10. `search_exposure_v1` semantic contract와 activation gate
11. explanation audience 분리
12. failure/fallback/no-result 분류
13. replay class 적용 기준
14. 검색어 privacy와 logging 경계
15. IP-3 진입 조건

## 5. 비범위

- Java/Kotlin production code
- Controller/Service/Repository 변경
- DB migration 또는 SQL 번호 생성
- Elasticsearch/OpenSearch/vector DB/embedding/model/provider 도입
- Search runtime 또는 index 구현
- search exposure table/event 구현
- 기존 `/api/v1/explore` 동작 변경
- recommendation ranking/candidate/exposure 재사용
- P2 metric 또는 physical ownership 변경
- Data shadow source cutover
- identity mapping 실제 join

## 6. 현재 구현 inventory

### 6.1 요약 판정

```text
현재 단순 DB keyword/list query
≠
Search Intelligence runtime
```

현재 production에는 SearchRun, SearchInputSnapshot, SearchCandidateSnapshot, SearchOutputSnapshot, search cursor, `search_exposure_v1` persistence, 통합 검색 orchestration이 없다.

### 6.2 실제 확인 결과

| 영역 | 현재 구현 | 현재 동작 | Search Intelligence 판정 |
|---|---|---|---|
| 게시글 탐색 | `GET /api/v1/explore` | optional `keyword`, `region`, Spring Data `Pageable` | legacy DB explore query |
| 게시글 keyword | `JourneyPostRepository.explore` | title/content/region display name `lower(...) like %keyword%` | lexical repository filter, relevance 없음 |
| 게시글 eligibility | same JPQL | published/public/moderation visible/active author | current application visibility predicate |
| 게시글 ordering | same JPQL | `publishedAt DESC, id DESC` | 최신순, ranking policy 아님 |
| 게시글 pagination | `PageResponse` | offset/page/size/total count | Search cursor 아님 |
| 지역 keyword | `GET /api/v1/regions?keyword=` | active region name substring, 최대 50 | independent region lookup |
| 지역 nearby | `/regions/nearby` | Haversine distance | discovery/geo lookup, text search 아님 |
| 크루 | `GET /api/v1/crews` | recruiting 최신순 | keyword 검색 없음 |
| 사용자 | profile/posts/bookmark API | 검색 endpoint 없음 | 미구현 |
| 장소 | `PlaceRepository.findAllById` | ID resolve only | 검색 endpoint 없음 |
| 태그 | DB `tags`, `post_tags`와 추천 read path | Search API/JPA model 없음 | 검색 미구현 |
| 통합 검색 | 없음 | 없음 | 미구현 |
| frontend search | 없음 | Vite placeholder 수준 | 미구현 |
| QueryDSL | 없음 | Spring Data JPA/JPQL 사용 | 미도입 |
| full-text/trigram/vector index | 없음 | region lower index, place normalized-name index, feed index만 존재 | Search index 미구현 |
| search behavior | persistence enum에 `search` 존재 | public recommendation event API는 post-bound VIEW/CLICK/SHARE/HIDE/REPORT만 허용 | 현재 search producer 확인 안 됨 |
| search exposure | `search_exposure_v1` ID 예약 | table/API/writer 없음 | `RESERVED`, authority 미활성 |

### 6.3 feed/explore/search 경계

- `/feed`: recommendation runtime 또는 legacy feed pagination.
- `/feed/page`: legacy latest feed compatibility.
- `/explore`: current latest-first post browse with keyword/region predicates.
- future Search Intelligence: versioned query/retrieval/ranking/run/snapshot/cursor/exposure evidence.

`/explore`를 이미 완성된 Search runtime으로 재해석하지 않는다.

## 7. 핵심 결정

### D1. Search와 Recommendation은 별도 도메인이다

Recommendation policy, comparator, candidate type, exposure source, metric은 Search에서 직접 재사용하지 않는다. 공통 evidence는 adapter/composition으로만 공유한다.

### D2. Query 원문과 normalized query를 분리한다

원문은 user intent evidence이며 normalized query는 retrieval key다. normalized query가 원문을 덮어쓰지 않는다.

### D3. Search V1 pagination은 snapshot-bound다

향후 Search runtime V1은 첫 실행에서 result ordering을 immutable snapshot으로 고정하고 cursor는 run/result snapshot에 결속한다. 현재 `/explore` offset pagination은 legacy compatibility이며 V1 cursor 계약이 아니다.

### D4. Search result rank는 1-based다

source rank, base rank, final rank를 분리한다. 동일 점수 tie-break는 locale 비종속 complete ordering을 가져야 한다.

### D5. Visibility authority는 Operations다

Search는 visibility/eligibility 결정을 생성하지 않는다. 과거 run/snapshot은 수정하지 않고 delivery 시 현재 visibility를 재검증한다.

### D6. `search_exposure_v1`은 semantic contract만 확정한다

SC-1 예약 ID를 사용한다. persistence와 physical writer는 아직 미구현이며 activation gate 전에는 authoritative runtime source로 주장하지 않는다.

### D7. no-results는 정상 결과다

valid request와 정상 retrieval/ranking의 결과가 0건이면 `succeeded` + empty output이다. `no_results`는 outcome reason이며 시스템 failure가 아니다.

### D8. Replay 보장을 증거 수준보다 강하게 선언하지 않는다

immutable source snapshot이 없는 live DB query나 외부 provider 결과는 exact replay가 아니다.

## 8. 산출물

| 문서 | 계약 ID |
|---|---|
| [IP-2 Foundation](IP-2-SEARCH-CONTRACT-FOUNDATION.md) | `jc-search-contract-foundation-v1` |
| [Search Domain Contract](SEARCH-DOMAIN-CONTRACT-V1.md) | `search-domain-contract-v1` |
| [Query & Normalization](SEARCH-QUERY-AND-NORMALIZATION-V1.md) | `search-query-normalization-v1` |
| [Retrieval, Filtering & Ranking](SEARCH-RETRIEVAL-FILTERING-AND-RANKING-V1.md) | `search-retrieval-ranking-v1` |
| [Pagination & Cursor](SEARCH-PAGINATION-AND-CURSOR-V1.md) | `search-pagination-cursor-v1` |
| [Exposure Contract](SEARCH-EXPOSURE-CONTRACT-V1.md) | `search-exposure-v1` |
| [Replay, Failure & Evidence](SEARCH-REPLAY-FAILURE-AND-EVIDENCE-V1.md) | `search-replay-evidence-v1` |
| [IP-2 Handoff](IP-2-HANDOFF.md) | `ip-2-handoff-v1` |

`search_exposure_v1`은 SC-1에서 이미 예약된 exposure source ID를 구체화하며 새로운 중복 ID가 아니다.

## 9. 단계별 누적 기록

### IP-2.1 Baseline and Source Inventory

- 목적: IP-1 보호 기준선과 현재 search-like 구현 확인
- 변경 파일: 문서·verification only
- 설계 내용: current DB explore/region lookup과 future Search runtime 분리
- 검증: controller/service/repository/DTO/DB index/frontend/search behavior 조사
- 보완: search persistence enum 존재를 production search logging으로 오인하지 않도록 public producer 부재 명시
- 잔여 리스크: IP-1.10 named archive가 active runtime에 직접 마운트되지 않음

### IP-2.2 Search Domain Boundary

- 목적: Search 책임/비책임과 Recommendation 분리
- 설계: domain-specific request/retrieval/ranking/cursor/exposure/replay
- 검증: IP-0 architecture, SC-1 exposure registry, IP-1 common types 대조
- 보완: common candidate evidence가 domain score scale을 통합하지 않도록 extension 필수화
- 잔여 리스크: Operations visibility port 미구현

### IP-2.3 Query and Normalization

- 목적: 원문/normalized query 및 filter canonicalization 고정
- 설계: versioned NFKC/whitespace/Locale.ROOT baseline, browse/text query 분리
- 검증: current `blankToNull`, region lower-case 처리와 미래 계약 구분
- 보완: 초성/형태소/오타 교정은 V1 필수에서 제외
- 잔여 리스크: 검색어 retention 기간 미승인

### IP-2.4 Retrieval, Filtering and Ranking

- 목적: candidate source/rank/score/eligibility 의미 고정
- 설계: source rank와 final rank 분리, complete tie-break, Operations authority
- 검증: 현재 JPQL visibility predicate와 DB index inventory
- 보완: null score를 0으로 coercion하지 않도록 명시
- 잔여 리스크: 실제 retrieval/index strategy 미선정

### IP-2.5 Pagination and Cursor

- 목적: mutation에 강한 stable pagination 정의
- 설계: snapshot-bound cursor, query/filter/policy/run/user/session binding
- 검증: current feed Base64 cursor 및 `/explore` offset과 명시적 분리
- 보완: visibility 변경 시 omit-without-backfill 원칙 추가
- 잔여 리스크: cursor signing/encryption key owner 미정

### IP-2.6 Search Exposure

- 목적: `search_exposure_v1` 의미와 activation gate 고정
- 설계: rendered/delivered/exposed 분리, page/item evidence, append-only/dedupe
- 검증: recommendation exposure source 3종과 분리
- 보완: reserved ID를 active authoritative source로 과장하지 않도록 상태 명시
- 잔여 리스크: physical writer/persistence schema는 IP-6 이전 미결정

### IP-2.7 Replay, Failure and Privacy

- 목적: failure/fallback/no-result/replay/privacy 경계
- 설계: exact/semantic/evidence 조건, raw query logging 금지
- 검증: IP-1 ReplayClass와 run status 불변조건 대조
- 보완: no-results를 failure code 목록에서 제거하고 outcome reason으로 분리
- 잔여 리스크: Data/Operations retention 승인 필요

### IP-2.8 Validation and Independent Review

- 구조/링크/ID/wire enum/owner/authority 검증
- production source와 SQL hash 비교
- 2회 자체 리뷰 후 발견사항 직접 보완
- 결과는 [IP-2 Handoff](IP-2-HANDOFF.md) 및 `verification/ip2/`에 기록

## 10. 완료 판정

- current search implementation inventory: COMPLETE
- Search 책임/비책임: FIXED
- query/normalization: FIXED
- retrieval/filtering/ranking: FIXED
- stable pagination/cursor: FIXED
- common contract mapping: FIXED
- search exposure semantic contract: FIXED / persistence not implemented
- replay/failure/privacy: FIXED
- production code/SQL: UNCHANGED
- Recommendation P0/P1/P2: PROTECTED

`IP-2: COMPLETE / CONTRACT_ONLY`
