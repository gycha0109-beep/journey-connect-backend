# IP-6 Search Runtime Integration Boundary

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `IP-6` |
| 단계명 | `Search Runtime Integration Boundary` |
| 계약 ID | `search-runtime-integration-boundary-v1` |
| evidence 계약 ID | `search-shadow-comparison-evidence-v1` |
| 상태 | `COMPLETE / DISABLED_BY_DEFAULT / NON_PERSISTENT` |
| 소유 트랙 | Intelligence Platform / Search |
| 기준 단계 | `IP-2 COMPLETE`, `IP-3 COMPLETE`, `IP-4 COMPLETE`, `IP-5 COMPLETE` |
| production API | 변경 없음 |
| DB/SQL | 변경 없음 |

## 2. 목적

IP-6는 현재 `/api/v1/explore` 결과를 계속 유일한 client response authority로 유지하면서, backend-facing legacy read representation과 IP-5 in-memory Search Runtime 결과를 독립적으로 비교할 수 있는 경계를 구현한다.

```text
Legacy /api/v1/explore execution [authoritative]
  → IP-4 read-only compatibility representation
  → optional Search shadow boundary [default disabled]
       ├─ fixture/test-only runtime input provider
       ├─ IP-5 runtime execution 또는 prepared runtime result
       └─ deterministic comparison harness
  → privacy-safe memory-only evidence
  → original legacy response object returned unchanged
```

이 단계는 Controller hook, production bean, API response 교체, SearchRun/snapshot/exposure persistence 또는 release gate를 구현하지 않는다.

## 3. 실제 시작 기준선과 legacy execution inventory

### 3.1 보호 기준선

- Recommendation P0/P1/P2 technical: `CLOSED`
- Recommendation P2 production: `HOLD`
- IP-1/IP-1.10: `CLOSED`
- IP-2: `COMPLETE / CONTRACT_ONLY`
- IP-3: `COMPLETE`
- IP-4: `COMPLETE / READ_ONLY`
- IP-5: `COMPLETE / IN_MEMORY`
- protected source: 320개 SHA-256 목록
- canonical SQL: `journey-connect-db-v2.7/01..26`

### 3.2 `/api/v1/explore` 현재 경로

| 항목 | 실제 확인 결과 |
|---|---|
| path/method | `GET /api/v1/explore` |
| Controller | `PostController.explore(keyword, region, Pageable)` |
| Service | `PostService.explore` |
| Repository | `JourneyPostRepository.explore` |
| transaction | `PostService`의 APP read-only transaction 경계 |
| request | optional `keyword`, optional `region`, Spring `Pageable`; 기본 size 20 |
| keyword | blank→null; title/content/region name 계열의 case-insensitive substring |
| region | normalized 후 slug/local/ko/en exact case-insensitive match |
| visibility | `PUBLISHED`, `PUBLIC`, moderation `VISIBLE`, active author |
| ordering | `publishedAt DESC, id DESC` |
| pagination | offset 기반 Spring Page |
| response | `ApiResponse<PageResponse<PostDtos.Summary>>` |
| auth context | endpoint `permitAll`; explore method에 principal 입력 없음 |
| related loading | author/region entity graph, 이후 summary용 like/bookmark count 조회 |
| Search evidence | SearchRun/snapshot/cursor/exposure 없음 |

따라서 현재 경로는 `Legacy explore read path`이며 Search Intelligence Runtime이 아니다.

## 4. 단계별 작업 기록

### 4.1 IP-6.1 Module and Dependency Boundary

**목적**

production path와 격리된 비교 경계를 만든다.

**변경 파일**

- `jc-backend/settings.gradle.kts`
- `jc-search-integration/build.gradle.kts`
- 신규 `jc-search-integration/src/**`

**구현 내용**

- 독립 모듈 `jc-search-integration` 생성
- 의존성은 `jc-search-runtime`, `jc-search-compatibility`로 제한
- backend, recommendation, Spring, JPA, JDBC, HTTP, provider SDK 의존 없음
- 전용 `searchIntegrationContractTest` Gradle task 추가
- production resources/configuration/bean 없음

**검증 결과**

- Java 21 main/test 직접 컴파일 PASS
- `-Xlint:all -Werror` PASS
- forbidden dependency/annotation scan PASS

**보완 사항**

- 실제 production hook 대신 generic integration port와 test harness만 구현했다.

**잔여 리스크**

- Gradle distribution DNS 접근 환경에서 Gradle task 재실행이 필요하다.

### 4.2 IP-6.2 Disabled-by-default Activation

**목적**

설정 누락이나 unknown mode가 shadow 활성화로 이어지지 않게 한다.

**변경 파일**

- `SearchShadowMode`
- `SearchShadowPolicyV1`
- `SearchShadowActivationDecisionV1`
- `SearchShadowStatus`

**구현 내용**

- mode: `disabled`, `test_only`, `shadow_enabled`
- 기본 policy: `disabled`
- unknown wire mode: disabled로 safe handling
- activation decision을 명시적 immutable object로 반환
- production property/profile은 추가하지 않음

**검증 결과**

- disabled 상태에서 input provider와 runtime execution 미호출 PASS
- unknown mode 자동 활성화 없음 PASS

**보완 사항**

- mode와 activation reason을 분리해 evidence에서 원인을 확인할 수 있게 했다.

**잔여 리스크**

- production activation/rollback owner는 미결정이다.

### 4.3 IP-6.3 Runtime Input and Execution Isolation

**목적**

IP-4 compatibility result가 runtime retrieval source로 재사용되는 자기비교를 차단한다.

**변경 파일**

- `SearchShadowRuntimeInputProvider`
- `SearchShadowRuntimeInputContextV1`
- `SearchShadowRuntimeInputResultV1`
- `UnavailableSearchShadowRuntimeInputProvider`
- fixture provider/execution port

**구현 내용**

- provider input은 correlation/referenceTime과 legacy fingerprints만 포함
- legacy item/payload/query를 runtime candidate로 전달하지 않음
- 상태: `available`, `unavailable`, `unsupported`, `invalid`
- 기본 provider는 명시적 unavailable
- fixture provider와 direct execution은 test-only package에 격리
- execution request 전체를 runtime input fingerprint에 결속

**검증 결과**

- compatibility-not-runtime-source invariant PASS
- unavailable/unsupported/invalid 분류 PASS
- null result와 exception containment PASS

**보완 사항**

- 초기 fingerprint가 일부 metadata만 결속하던 문제를 execution request 전체 hash로 강화했다.

**잔여 리스크**

- 실제 retrieval/index source authority는 미정이다.

### 4.4 IP-6.4 Comparison Harness and Taxonomy

**목적**

legacy와 runtime의 서로 다른 의미를 유지하면서 비교 가능한 축만 결정론적으로 비교한다.

**변경 파일**

- `SearchShadowComparisonHarness`
- `SearchShadowComparator`
- `SearchShadowMismatchCode`
- `SearchShadowMismatchV1`
- `SearchShadowSeverity`
- `SearchShadowComparisonMetricsV1`
- `SearchShadowComparisonResultV1`

**구현 내용**

비교 축:

- execution status
- raw result count와 unique entity count
- entity set/intersection/legacy-only/runtime-only
- ordered entity references와 position difference
- top-K overlap
- duplicate reference
- pagination/cursor/visibility/ranking comparability

`pagination_not_comparable`, `cursor_not_comparable`, `visibility_not_comparable`, `ranking_not_comparable`은 runtime failure와 구분한다.

Mismatch는 severity/code/entityRef/position의 canonical ordering으로 정렬한다. HashMap 순서, locale, random UUID, 현재 시각은 결과 순서에 사용하지 않는다.

**검증 결과**

- exact/entity set/order/count/top-K/duplicate/mixed type PASS
- same input repeated comparison deterministic PASS
- mismatch/warning ordering stability PASS
- zero denominator top-K ratio finite PASS

**보완 사항**

- duplicate가 존재할 때 raw item count와 unique set count가 혼동되던 가능성을 제거했다.
- runtime input invalid taxonomy를 추가했다.

**잔여 리스크**

- comparison metric은 운영 KPI나 release gate가 아니다.

### 4.5 IP-6.5 Fail-open, Timeout and Response Guard

**목적**

shadow 상태와 무관하게 legacy response authority를 보존한다.

**변경 파일**

- `SearchShadowIntegrationBoundary`
- `SearchShadowIntegrationPort`
- `SearchShadowIntegrationResult`
- `SearchShadowResponseGuard`
- `SearchShadowExecutionDeadlineV1`
- `SearchShadowExecutionOutcomeV1`

**구현 내용**

- generic legacy response 객체를 입력받아 동일 객체 reference로 반환
- shadow provider/execution/comparison exception은 evidence failure로 격리
- legacy success를 HTTP/contract failure로 변환하지 않음
- supplied referenceTime/deadline 기반 deterministic timeout contract
- unmanaged thread, executor, common pool 없음
- timeout 시 runtime result 폐기, legacy response 그대로 유지

**검증 결과**

- shadow success/failure/timeout에서 response identity PASS
- item order와 page metadata envelope 보존 PASS
- fail-open and safe exception containment PASS

**보완 사항**

- response deep equality뿐 아니라 동일 객체 identity를 guard에서 강제했다.

**잔여 리스크**

- 실제 bounded production executor와 latency budget은 IP-7 이후 승인 대상이다.

### 4.6 IP-6.6 Memory-only Evidence and Authority

**목적**

비교 결과를 검토 가능하게 하되 persistence/exposure/metric/release authority를 만들지 않는다.

**변경 파일**

- `SearchIntegrationContractIds`
- `SearchShadowComparisonEvidenceV1`
- `SearchShadowAuthorityV1`
- `SearchShadowFingerprintV1`
- `SearchShadowWarningCode`

**구현 내용**

Evidence 결속:

- `search-shadow-comparison-evidence-v1`
- comparison/correlation/policy/version/referenceTime
- legacy request/response fingerprint
- runtime input/result fingerprint
- counts, mismatch, warning, severity/status, duration metadata
- producer build

Authority는 다음을 항상 false로 고정한다.

- persistence
- exposure
- release gate
- metric
- production cursor
- API cutover

raw query, raw response, 전체 candidate, token, 정밀 위치, private user metadata를 evidence에 포함하지 않는다.

**검증 결과**

- authority invariants PASS
- privacy field/static scan PASS
- evidence fingerprint/ID repeated determinism PASS

**보완 사항**

- evidence contract ID를 명시적으로 추가했다.
- comparison ID에 correlation/referenceTime을 결속해 서로 다른 실행의 충돌 가능성을 줄였다.

**잔여 리스크**

- evidence retention과 writer는 의도적으로 미결정/미구현이다.

### 4.7 IP-6.7 Fixtures and Regression Validation

**목적**

activation, comparison, authority, response impact와 경계 조건을 실행 증거로 고정한다.

**변경 파일**

- `src/test/resources/search-integration/*.json` 12개
- `SearchIntegrationContractTest`
- `verification/ip6/**`

**구현 내용**

Fixture/runner는 disabled, exact/order/count/entity set, duplicate, no-results, fallback/failure, input unavailable/unsupported, timeout, mixed entity, not-comparable, response pass-through, privacy와 maximum boundary를 포함한다.

**검증 결과**

- IP-6 integration: `972 assertions PASS`
- IP-5 runtime: `850 assertions PASS`
- IP-4 compatibility: `584 assertions PASS`
- IP-3 Search contracts: `425 assertions PASS`
- IP-1 common contracts: `739 assertions PASS`
- Recommendation Foundation/Wave1~7/golden/isolation/P1 17/P2 23 PASS

**보완 사항**

- unsupported legacy sort, duplicate runtime result, empty/non-empty direction, response metadata 보존 검증을 추가했다.

**잔여 리스크**

- Spring/backend 통합 테스트는 production wiring이 없고 Gradle distribution 접근도 차단되어 실행하지 않았다.

## 5. Authority Matrix

| 대상 | IP-6 권위 |
|---|---|
| Legacy response | `authoritative` |
| Shadow runtime result | `non_authoritative` |
| Comparison evidence | `memory_only / non_persistent` |
| Search exposure | `none` |
| Metric/release gate | `none` |
| Production cursor | `none` |
| API cutover | `none` |

## 6. 보호 기준선

- protected source: `320/320 SHA-256 exact match`
- canonical SQL: `26/26 exact match`
- Controller, Service, Repository, DTO, JPQL/QueryDSL, SecurityConfig: 변경 없음
- recommendation source/P2 evidence/exposure authority/metric definitions: 변경 없음
- DB migration/new SQL/index: 없음
- 유일한 기존 build 변경: `settings.gradle.kts` 신규 모듈 등록

## 7. 자체 리뷰

### 1차

- 발견: 10
- 수정: 10
- 보류: 0
- 주요 보완: runtime input fingerprint, comparison ID, raw/unique count, invalid taxonomy, unavailable count evidence, activation decision, unsupported sort, duplicate runtime response, page envelope, evidence contract ID

### 2차

- 발견: 8
- 수정: 8
- 보류: 0
- 주요 보완: authority cross-field, unknown mode, not-comparable 분리, zero denominator, privacy surface, deterministic mismatch order, timeout isolation, default unavailable provider

보완 후 IP-6/IP-5/IP-4/IP-3/IP-1 회귀와 protected/SQL hash를 다시 실행했다.

## 8. Gradle 실행 결과

Gradle Wrapper를 실제 실행했으나 task configuration/compile 전 Gradle 8.14.5 distribution 다운로드 단계에서 `services.gradle.org` DNS 해석 실패가 발생했다.

- Gradle PASS: 선언하지 않음
- 대체 검증: Java 21 `javac`, `-Xlint:all -Werror`, 직접 contract runner, fixture load, 전체 관련 회귀 PASS
- 상세: `verification/ip6/IP6_GRADLE_ATTEMPT.log`, `IP6_GRADLE_RESULT.txt`

## 9. 잔여 리스크와 IP-7 진입 조건

미결정:

1. Operations visibility/eligibility owner
2. actual retrieval/index strategy
3. SearchRun/snapshot writer
4. `search_exposure_v1` physical writer
5. query retention/access/deletion policy
6. production cursor key/rotation owner
7. shadow evidence retention owner
8. performance budget와 activation/rollback authority

### 권장 후속

`IP-7 Search Shadow Wiring & Controlled Comparison`

허용 후보:

- disabled-by-default backend hook
- explicit profile/flag gate
- bounded executor abstraction
- no response replacement
- no persistence/exposure

### 진입 판정

`READY — CONTRACT/CONTROLLED SHADOW SCOPE ONLY`

production activation, API response replacement, persistence, exposure, release gate는 위 owner와 정책이 승인되기 전 `HOLD`다.
