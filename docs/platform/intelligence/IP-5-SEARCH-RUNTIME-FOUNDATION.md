# IP-5 Search Runtime Foundation

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `IP-5` |
| 단계명 | `Search Runtime Foundation` |
| 계약 ID | `search-runtime-foundation-v1` |
| 상태 | `COMPLETE / IN_MEMORY_FOUNDATION / NON_PERSISTENT` |
| 소유 트랙 | Intelligence Platform / Search |
| 기준 단계 | `IP-2 COMPLETE`, `IP-3 COMPLETE`, `IP-4 COMPLETE` |
| production API | 변경 없음 |
| DB/SQL | 변경 없음 |

## 2. 목적

IP-5는 검증된 `SearchRequestV1`을 받아 retrieval, filtering, eligibility, visibility, ranking, reranking, deterministic ordering, immutable in-memory snapshot 생성까지 수행할 수 있는 독립 Search Runtime 기반을 구현한다.

이 단계는 `/api/v1/explore`를 호출하거나 교체하지 않는다. 생성 결과는 production persistence, search exposure, API response 또는 운영 cursor의 authoritative source가 아니다.

```text
Validated SearchRequestV1
  → SearchRuntime
  → SearchRetrievalPort
  → SearchCandidateFilter / SearchEligibilityPort / SearchVisibilityPort
  → SearchRankingPort / SearchRerankingPort
  → SearchDeterministicOrdering
  → SearchResultSnapshotV1 [ephemeral]
  → SearchRuntimeResultV1 [foundation_only]
```

## 3. 실제 시작 기준선

- Recommendation P0/P1/P2 technical: `CLOSED`
- Recommendation P2 production: `HOLD`
- IP-1 common contracts: `CLOSED`
- IP-2 Search contracts: `COMPLETE / CONTRACT_ONLY`
- IP-3 Search domain type/validation: `COMPLETE`
- IP-4 legacy explore read adapter: `COMPLETE / READ_ONLY`
- Search runtime/persistence/exposure/API cutover: 미구현
- protected source: 320개 SHA-256 보호 목록
- canonical SQL: `journey-connect-db-v2.7/01..26`

IP-4 compatibility result는 legacy read evidence이며 IP-5 retrieval source로 사용하지 않는다.

## 4. 단계별 작업 기록

### 4.1 IP-5.1 Baseline, Module and Dependency Inventory

**목적**

기존 Search 계약과 build 구조를 확인하고 중복 타입과 금지 의존을 차단한다.

**변경 파일**

- `jc-backend/settings.gradle.kts`
- `jc-search-runtime/build.gradle.kts`

**구현 내용**

- 신규 독립 모듈 `jc-search-runtime` 등록
- 의존성은 `jc-search-contracts` 하나로 제한
- compatibility, backend, recommendation, Spring, JPA, JDBC, HTTP, provider SDK 의존 없음
- `searchRuntimeContractTest` 전용 Gradle task 추가

**검증 결과**

- main/test Java 21 직접 컴파일 PASS
- `-Xlint:all -Werror` PASS
- forbidden dependency 문자열·import scan PASS

**보완 사항**

- runtime entry point를 `SearchRuntime` 인터페이스와 `DefaultSearchRuntime` 구현으로 분리했다.

**잔여 리스크**

- Gradle distribution 다운로드가 현재 환경 DNS 문제로 실행 전 차단된다.

### 4.2 IP-5.2 Runtime Lifecycle and Authority

**목적**

실행 lifecycle과 non-authoritative foundation 경계를 타입으로 고정한다.

**변경 파일**

- `SearchRuntime.java`
- `DefaultSearchRuntime.java`
- `SearchRuntimeExecutionRequestV1.java`
- `SearchRuntimeResultV1.java`
- `SearchRuntimeAuthorityV1.java`
- `SearchRuntimeStatus.java`

**구현 내용**

실행 단계:

1. IP-3 request validation과 canonical query 결속 확인
2. `RetrievalRequestV1` 생성
3. retrieval 실행과 candidate contract 검증
4. filter, eligibility, visibility 판단
5. ranking과 optional reranking
6. deterministic final ordering
7. immutable in-memory snapshot 생성
8. first-page projection 및 optional test-only cursor 생성
9. ephemeral `SearchRunV1`/`IntelligenceRunV1` evidence 생성
10. foundation-only authority result 반환

`SearchRuntimeAuthorityV1`은 다음을 항상 false로 고정한다.

- persistence authority
- exposure authority
- API authority
- production cursor authority

**검증 결과**

- runtime result/status/authority invariant PASS
- failure result에 run/snapshot/page authority가 포함되지 않음 PASS
- `NO_RESULTS`는 empty immutable snapshot을 가진 정상 종료로 검증

**보완 사항**

- status와 result shape의 교차 불변조건을 생성자에서 검증하도록 강화했다.

**잔여 리스크**

- backend-facing port와 shadow wiring은 IP-5 범위 밖이다.

### 4.3 IP-5.3 Retrieval and Candidate Validation

**목적**

candidate source와 final ordering authority를 분리한다.

**변경 파일**

- `port/SearchRetrievalPort.java`
- `port/SearchRetrievalResultV1.java`
- `port/SearchRetrievalStatus.java`
- `fixture/InMemorySearchRetrievalPort.java`

**구현 내용**

- retrieval port는 final rank를 결정하지 않는다.
- candidate null, duplicate `entityRef`, entity scope mismatch, maximum count 초과를 거부한다.
- candidate의 retrieval source와 strategy version이 execution request와 일치해야 한다.
- `retrievedAt`은 execution completion 이후일 수 없다.
- provider/runtime exception은 safe typed failure로 변환한다.

**검증 결과**

- null/duplicate/boundary/source/version/time binding PASS
- retrieval failed와 unavailable 분리 PASS

**보완 사항**

- 최초 구현 후 retrieval source·strategy version·timestamp cross-binding을 추가했다.

**잔여 리스크**

- production retrieval/index 전략은 미결정이다.

### 4.4 IP-5.4 Filtering, Eligibility and Visibility Boundary

**목적**

Search가 Operations visibility/eligibility 의미를 임의 생성하지 못하도록 한다.

**변경 파일**

- `port/SearchCandidateFilter.java`
- `port/SearchEligibilityPort.java`
- `port/SearchVisibilityPort.java`
- `port/SearchDependencyDecision.java`
- `port/SearchFilterDecisionV1.java`
- `fixture/PassThroughSearchCandidateFilter.java`

**구현 내용**

- fixture-only explicit decision: `allow`, `deny`, `unknown`, `dependency_unavailable`
- `deny`와 `unknown`은 result candidate에서 제외
- dependency unavailable은 typed terminal status로 처리
- DB 상태 조회, moderation 추정, legacy result authority 사용 없음

**검증 결과**

- eligible/ineligible/unknown/dependency unavailable PASS
- filter/eligibility/visibility 예외의 단계별 failure mapping PASS

**보완 사항**

- dependency 예외를 generic retrieval failure로 분류하던 가능성을 제거했다.

**잔여 리스크**

- Operations 제공 `VisibilityEligibilityPort`의 실제 owner와 schema가 필요하다.

### 4.5 IP-5.5 Ranking, Reranking and Deterministic Ordering

**목적**

Search 전용 ranking 경계와 재현 가능한 final ordering을 고정한다.

**변경 파일**

- `ranking/SearchRankingPort.java`
- `ranking/SearchRankingRequestV1.java`
- `ranking/SearchRankingResultV1.java`
- `ranking/SearchRankedCandidateV1.java`
- `ranking/SearchRerankingPort.java`
- `ranking/NoOpSearchRerankingPort.java`
- `ranking/SearchDeterministicOrdering.java`
- `fixture/DeterministicFixtureSearchRankingPort.java`

**구현 내용**

최종 정렬 순서:

1. finite ranking score descending, null last
2. explicit ordering key
3. source rank
4. entity type wire value
5. canonical entity reference

- NaN/Infinity 거부
- locale-independent `String.compareTo` 기반 canonical ordering
- HashMap iteration order, current time, random UUID를 ordering에 사용하지 않음
- reranker는 candidate count, identity set, candidate facts를 변경할 수 없음
- no-op reranker는 score와 입력 order semantics를 보존

**검증 결과**

- 동일 점수/mixed type/repeated execution/HashMap insertion order/locale 변경 PASS
- reranking order change와 candidate facts 보존 PASS

**보완 사항**

- 동일 `entityRef`로 candidate facts를 교체하는 reranker output도 거부하도록 강화했다.

**잔여 리스크**

- fixture ranking은 production ranking policy가 아니다.

### 4.6 IP-5.6 Immutable Snapshot and SearchRun Binding

**목적**

foundation 결과를 불변 evidence로 표현하되 persisted evidence처럼 위장하지 않는다.

**변경 파일**

- `snapshot/SearchResultSnapshotBuilder.java`
- `snapshot/SearchResultSnapshotV1.java`
- `snapshot/SearchResultItemV1.java`
- `snapshot/SearchRuntimeFingerprintV1.java`

**구현 내용**

Snapshot 결속:

- deterministic ephemeral snapshot ID
- request/query/filter fingerprints
- ranking policy/retrieval strategy/query normalization versions
- reference time
- ordered items, final 1-based positions, source ranks
- runtime schema/version와 producer build
- foundation-only, persistence/exposure authority false

`SearchRunV1`과 `IntelligenceRunV1`은 ephemeral input/candidate/output refs만 사용한다. 실제 DB row나 persisted snapshot 존재를 주장하지 않는다.

**검증 결과**

- defensive copy와 immutable collection PASS
- snapshot ID/content fingerprint 반복 실행 동일 PASS
- runtime result, SearchRun, IntelligenceRun output snapshot cross-binding PASS

**보완 사항**

- snapshot `builtAt`과 reference/started/completed time range를 강화했다.
- external provider source를 포함하면 exact replay를 선언하지 못하도록 했다.

**잔여 리스크**

- canonical persistent snapshot schema와 writer는 미결정이다.

### 4.7 IP-5.7 Failure and Bounded Fallback

**목적**

실패를 빈 성공으로 숨기지 않고 제한된 fallback을 증거화한다.

**변경 파일**

- `SearchRuntimeFailureCode.java`
- `SearchRuntimeFailureV1.java`
- `SearchRuntimeFallbackCode.java`
- `SearchRuntimeFallbackV1.java`

**구현 내용**

- invalid request, retrieval unavailable/failed, invalid/duplicate candidate, filtering, eligibility, visibility, ranking, score, reranking, ordering, snapshot, contract validation failure 구분
- ranking failure에 한해 명시적으로 허용된 경우 source-rank ordering fallback 수행
- fallback score를 생성하지 않음
- fallback 성공 결과도 status=`fallback`
- fallback failure는 terminal failed result

**검증 결과**

- primary ranking failure → source-rank fallback PASS
- fallback code/primary failure/policy version binding PASS
- fallback failure와 no-results 분리 PASS

**보완 사항**

- 각 port 예외가 정확한 typed failure code로 매핑되도록 분리했다.

**잔여 리스크**

- production fallback policy와 승인 주체는 후속 단계 결정 사항이다.

### 4.8 IP-5.8 Pagination and Test-only Cursor

**목적**

snapshot-bound pagination invariant만 검증하고 production cursor authority는 활성화하지 않는다.

**변경 파일**

- `pagination/SearchRuntimePageProjector.java`
- `pagination/SearchRuntimePageV1.java`
- `pagination/SearchTestCursorV1.java`

**구현 내용**

- immutable snapshot item slicing
- first/last page projection
- next page가 있을 때만 IP-3 `SearchCursorV1` payload 생성
- query/filter/policy/referenceTime/snapshot/ordering tuple binding
- wrapper authority=`test_only_unsigned`
- production-authoritative flag는 항상 false
- incoming cursor execution은 persisted snapshot이 없으므로 거부

**검증 결과**

- first/last page slicing PASS
- valid snapshot-bound test cursor PASS
- invalid binding, unsigned production authority, legacy offset 변환 없음 PASS

**보완 사항**

- page item count가 requested page size를 초과하지 못하도록 invariant를 추가했다.

**잔여 리스크**

- signing key owner, key rotation, expiration과 API issuance는 미결정이다.

### 4.9 IP-5.9 Runtime Evidence and Privacy

**목적**

실행 단계와 count를 추적하되 민감 payload를 저장하지 않는다.

**변경 파일**

- `SearchRuntimeEvidenceV1.java`
- `SearchRuntimeStageEvidenceV1.java`

**구현 내용**

Evidence:

- runtime/retrieval/ranking/query normalization versions
- referenceTime, input fingerprint
- candidate/filtered/eligible/ranked/result/rejected counts
- fallback/failure codes
- ephemeral snapshot ref
- producerBuildId와 stage evidence

저장하지 않는 값:

- raw query
- full candidate payload
- authentication token
- private metadata
- precise location

**검증 결과**

- evidence count consistency PASS
- raw query/privacy-forbidden field scan PASS
- persistence 없음 PASS

**보완 사항**

- result와 evidence snapshot/count binding을 강화했다.

**잔여 리스크**

- 실제 로그 retention/access/deletion 정책은 Data/Operations/System Coordination 승인이 필요하다.

### 4.10 IP-5.10 Fixture and Regression Validation

**목적**

runtime behavior와 이전 계약을 독립 회귀한다.

**변경 파일**

- `src/test/java/.../SearchRuntimeContractTest.java`
- `src/test/resources/search-runtime/*.json` 14개
- `verification/ip5/*`

**검증 결과**

- IP-5 runtime: 850 assertions PASS
- IP-4 compatibility: 584 assertions PASS
- IP-3 Search contracts: 425 assertions PASS
- IP-1 common contracts: 739 assertions PASS
- Recommendation Foundation/Wave1~7/golden/isolation/P1 17/P2 23: direct Java regression PASS
- protected source 320/320 exact match
- canonical SQL 26/26 exact match

Gradle Wrapper는 실제 실행했으나 Gradle 8.14.5 distribution 다운로드 단계에서 `services.gradle.org` DNS resolution 실패로 task가 시작되지 않았다. Gradle task PASS로 보고하지 않는다.

## 5. Module and Dependency Direction

```text
jc-intelligence-contracts
          ↑
jc-search-contracts
          ↑
jc-search-runtime

jc-search-runtime ✕ jc-search-compatibility
jc-search-runtime ✕ jc-backend
jc-search-runtime ✕ jc-recommendation-core
jc-search-runtime ✕ Spring/JPA/JDBC/HTTP/provider SDK
```

## 6. Fixture Coverage

14개 JSON fixture와 Java test-only builders로 다음 의미를 모두 검증한다.

- valid multiple/single/empty results
- same score deterministic tie-break
- duplicate/null/NaN/Infinity
- retrieval failure/unavailable
- ranking fallback success/failure
- ineligible/unknown/visibility dependency unavailable
- no-op and order-changing reranking
- repeated determinism and mixed entity types
- Korean normalization/fingerprint
- maximum candidate boundary
- snapshot immutability
- first/last page slicing
- valid/invalid test cursor binding
- contract validation and no-results

Invalid fixture는 정상 builder를 우회하는 명시적 test-only path에서만 생성한다.

## 7. 자체 리뷰

### 리뷰 1 — Architecture, Authority and Immutability

- 발견: 9건
- 수정: 9건
- 보류: 0건

주요 보완:

- 실패 결과의 fake run/snapshot 제거
- status/result-shape 불변조건
- reranker candidate-facts 보존
- external provider exact replay 차단
- snapshot time/binding 강화
- page-size invariant
- result/evidence/run cross-binding
- fixture constructor validation
- runtime interface 분리

### 리뷰 2 — Failure Classification and Input Binding

- 발견: 10건
- 수정: 10건
- 보류: 0건

주요 보완:

- filter/eligibility/visibility 예외 typed mapping
- retrieval source/strategy/time binding
- fixture candidate validation
- SearchRun/IntelligenceRun output reference binding
- exact replay 및 port exception 회귀 추가

보완 후 IP-5/IP-4/IP-3/IP-1 전체 관련 계약 테스트와 보호 해시를 재실행했다.

## 8. 보호 기준선 판정

| 검증 | 결과 |
|---|---|
| protected source | `320/320 exact match` |
| canonical SQL | `26/26 exact match` |
| Controller/Service/Repository/DTO/JPQL/QueryDSL/SecurityConfig | 변경 없음 |
| Recommendation production source | 변경 없음 |
| P2 exposure authority/metric/evidence | 변경 없음 |
| DB migration/new SQL | 없음 |
| production API wiring | 없음 |
| Spring bean/component scan | 없음 |
| Search persistence/exposure persistence | 없음 |

## 9. 현재 상태

```text
IP-5: COMPLETE
Search Runtime Foundation: IMPLEMENTED / IN_MEMORY
Retrieval Port: IMPLEMENTED
Filtering/Eligibility Boundary: IMPLEMENTED
Ranking/Reranking Boundary: IMPLEMENTED
Deterministic Ordering: IMPLEMENTED
Immutable Result Snapshot: IMPLEMENTED / NON_PERSISTENT

Search Persistence: NOT IMPLEMENTED
Search Exposure Persistence: NOT IMPLEMENTED
Production Cursor Authority: NOT ENABLED
Search API Wiring: NOT IMPLEMENTED
Search API Cutover: NOT STARTED
Legacy /api/v1/explore Behavior: UNCHANGED
Protected Baseline: MAINTAINED
```

## 10. 잔여 리스크와 승인 항목

1. Operations visibility/eligibility read port owner와 versioned decision schema
2. actual retrieval/index strategy와 source authority
3. query retention/access/deletion policy
4. SearchRun 및 snapshot physical writer와 persistence schema
5. `search_exposure_v1` physical writer와 Reliability metric activation gate
6. production cursor signing key owner, rotation, expiration, revocation
7. backend shadow execution resource/cost/failure isolation
8. Gradle distribution 접근 가능한 환경에서 전용 tasks 재실행

## 11. IP-6 진입 조건과 권장안

### 진입 판정

`READY — FOUNDATION SCOPE ONLY`

### 권장 다음 단계

```text
IP-6 Search Runtime Integration Boundary
```

허용 후보:

- backend-facing Search runtime port
- disabled-by-default shadow adapter
- legacy result comparison harness
- timeout/resource isolation
- no response replacement
- no persistence/exposure

### HOLD

- `/api/v1/explore` response replacement
- production Search API 활성화
- DB/index/provider integration
- SearchRun/result/exposure persistence
- production cursor issuance
- Operations visibility authority 없이 production result 제공
