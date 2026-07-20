# IP-7 Search Shadow Wiring & Controlled Comparison

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `IP-7` |
| 계약 ID | `search-shadow-wiring-v1` |
| 구조화 record 계약 ID | `search-shadow-structured-record-v1` |
| 상태 | `COMPLETE / DISABLED_BY_DEFAULT / TEST_ONLY_CONTROLLED_COMPARISON` |
| 기준 단계 | `IP-2..IP-6 COMPLETE` |
| production hook | `NOT INSERTED` |
| production shadow activation | `NOT ENABLED` |
| persistence/exposure/release/cursor authority | `NONE` |

## 2. 목적

IP-7은 IP-6의 disabled shadow integration boundary를 backend 주변에서 사용할 수 있는 **wiring capability**로 감싼다. 실제 `/api/v1/explore` 호출부에는 삽입하지 않으며, 테스트 전용 profile·명시적 allow·deterministic sampling·bounded executor 조건이 모두 충족될 때만 controlled comparison을 수행한다.

```text
Legacy response [authoritative]
  → optional SearchShadowHook [not production-wired]
  → activation/profile/sampling/circuit gate
  → bounded executor abstraction
  → IP-6 SearchShadowIntegrationPort
  → non-persistent structured log port
  → legacy response identity unchanged
```

다음 등식은 성립하지 않는다.

- hook capability ≠ production activation
- compatibility representation ≠ runtime candidate source
- comparison record ≠ search exposure
- comparison metric ≠ P2 metric
- test-only execution ≠ API cutover

## 3. 실제 시작 기준선

- Recommendation P0/P1/P2 technical: `CLOSED`
- Recommendation P2 production: `HOLD`
- IP-2: `COMPLETE / CONTRACT_ONLY`
- IP-3: `COMPLETE`
- IP-4: `COMPLETE / READ_ONLY`
- IP-5: `COMPLETE / IN_MEMORY`
- IP-6: `COMPLETE / DISABLED_BY_DEFAULT`
- IP-6 assertions: `972 PASS`
- IP-5 assertions: `850 PASS`
- IP-4 assertions: `584 PASS`
- IP-3 assertions: `425 PASS`
- IP-1 assertions: `739 PASS`
- protected source: `320/320 exact`
- canonical SQL: `01..26 exact`

## 4. Backend explore 실행 inventory

실제 backend는 다음 경로를 사용한다.

```text
GET /api/v1/explore
  → PostController.explore(keyword, region, Pageable)
  → PostService.explore(keyword, region, pageable)
  → JourneyPostRepository.explore(...)
  → PostService.summaries(Page<JourneyPost>)
  → PageResponse<PostDtos.Summary>
```

현재 동작:

- 인증 principal을 사용하지 않는 public endpoint
- `keyword`, `region`, Spring `Pageable`
- keyword: title/content/region local/ko/en `lower(...) like lower('%...%')`
- region: slug/local/ko/en exact case-insensitive match
- `PUBLISHED`, `PUBLIC`, moderation `VISIBLE`, active author만 조회
- repository order: `publishedAt DESC, id DESC`
- offset page metadata: page/size/totalElements/totalPages/last
- response는 service에서 summary DTO로 조립된 뒤 controller가 반환
- SearchRun/snapshot/cursor/exposure/runtime policy는 현재 경로에 없음

### Hook 가능 지점 판정

legacy response 조립 이후 side-effect 호출 지점은 기술적으로 존재한다. 그러나 `PostController`, `PostService`는 protected source에 포함되며, IP-7 목표는 독립 wiring capability와 controlled test만으로 달성 가능하다.

**판정:** production 호출부는 수정하지 않는다. 실제 hook 삽입은 IP-8 proposal·전체 backend 회귀 이후 별도 결정한다.

## 5. 모듈과 dependency

신규 모듈:

```text
jc-search-shadow-wiring
  → jc-search-integration
  → jc-search-compatibility
```

금지 dependency:

- `jc-backend`
- `jc-recommendation-core`
- Spring/JPA/DB/provider SDK

모듈은 Java 21 dependency-free contract/test runner 구조이며 Spring annotation이나 자동 bean 등록이 없다.

## 6. Hook contract와 backend adapter

### `SearchShadowHook<T>`

legacy response 생성 이후 호출 가능한 좁은 계약이다. 반환 receipt는 client response가 아니며 response DTO에 합쳐지지 않는다.

### `NoOpSearchShadowHook<T>`

기본 구현이다.

- runtime 호출 없음
- executor submit 없음
- comparison/evidence 없음
- 예외 없음
- response identity 유지

### `BackendExploreShadowHookAdapter<T>`

backend DTO에 직접 의존하지 않고 IP-4 mirror view를 받는다.

```text
LegacyExploreRequestView + LegacyExplorePageView
  → LegacyExploreCompatibilityAdapter
  → SearchShadowDispatcher
```

compatibility 결과는 비교 기준으로만 사용하며 runtime candidate source로 사용하지 않는다.

## 7. Activation과 profile gate

`SearchShadowWiringMode`:

- `disabled`
- `test_only`
- `shadow_candidate`

실제 활성 가능한 상태는 `test_only` 하나뿐이다. 다음 조건을 모두 요구한다.

1. mode = `test_only`
2. profile = `search-shadow-test`
3. explicit allow = true
4. runtime input provider 등록
5. executor 등록 및 요구 capacity/concurrency 충족
6. comparison logger 등록

`shadow_candidate`는 readiness 표현일 뿐 IP-7에서 실행되지 않는다. missing/blank/unknown mode는 `disabled`다.

기본 config:

- mode: `disabled`
- sampling: `0 bps`
- explicit allow: false

production `application.yml`과 profile 설정은 변경하지 않았다.

## 8. Deterministic sampling

`DeterministicSearchShadowSampler`는 다음 material의 SHA-256으로 0..9999 bucket을 계산한다.

```text
sampling contract/version
sampling policy version
stable correlation ID
```

- random 사용 없음
- current time 사용 없음
- JVM `hashCode` 사용 없음
- 0 bps = 모두 제외
- 10,000 bps = test-only fixture에서 모두 포함
- 동일 입력/버전 = 동일 결정

raw correlation ID는 structured record에 저장하지 않고 SHA-256 fingerprint만 남긴다.

## 9. Bounded executor와 backpressure

`SearchShadowExecutor`는 다음을 명시한다.

- finite `queueCapacity`
- finite `maxConcurrency`
- timeout
- typed result

상태:

- `completed`
- `rejected`
- `queue_full`
- `executor_unavailable`
- `timed_out`
- `cancelled`
- `failed`

IP-7은 production executor/thread pool을 생성하지 않는다. 테스트는 direct controlled executor를 사용한다. unbounded queue, unmanaged thread, common ForkJoinPool 사용은 금지한다.

모든 rejection/backpressure/timeout/cancellation은 legacy response에 영향을 주지 않는다.

## 10. Circuit boundary

`SearchShadowCircuitBreaker`와 fixed deterministic fixture를 구현했다.

- `closed`: 허용
- `open`: shadow만 차단
- `half_open`: 명시적 fixture trial 여부로 결정

자동 시간 복구, production threshold, operational owner는 구현하지 않았다.

## 11. Dispatcher lifecycle

`DefaultSearchShadowDispatcher`:

1. activation 확인
2. deterministic sampling
3. circuit 확인
4. bounded executor submit
5. IP-6 `SearchShadowIntegrationPort` 호출
6. response identity guard
7. privacy-safe structured record 생성
8. non-persistent log port 호출
9. typed receipt 반환

IP-6 상태를 다음처럼 보존한다.

- input unavailable/unsupported/invalid
- timeout
- runtime failure
- not comparable/compared
- comparison failure

이를 모두 단순 `completed`로 축약하지 않는다.

## 12. Response non-impact

`SearchShadowDispatchReceiptV1`과 dispatcher는 다음을 강제한다.

- response authority: `legacy`
- responseModified: false
- integration result가 있으면 exact object identity 유지
- disabled/not-sampled/circuit/active 상태별 필드 불변조건
- item order·page metadata를 변경하지 않음
- receipt/log/evidence를 response에 추가하지 않음

실제 controller exception/HTTP mapping은 production hook을 삽입하지 않았으므로 변경될 수 없다.

## 13. Comparison logging port

### 기본

`NoOpSearchShadowComparisonLogPort`

- persistence 없음
- 외부 sink 없음
- 성공적으로 skip

### 테스트

in-memory log port를 사용한다.

### structured record

포함 가능:

- correlation fingerprint
- mode/sampling/dispatch/integration/comparison status
- deterministic mismatch/warning code
- severity
- legacy/runtime count
- top-K overlap
- duration bucket
- policy/build version
- referenceTime

금지:

- raw/normalized query text
- raw correlation/session ID
- response/candidate payload
- token/email/location/private metadata
- stack trace

Authority:

- response authority = legacy
- persistence/exposure/release/metric/cursor/activation/cutover = false

## 14. Fixture와 테스트

JSON fixture: `16개`

Java test scenarios는 요청된 50개 의미를 포함한다.

- config missing/disabled/unknown/profile block
- sample 0/include/exclude/repeatability/bounds
- executor accepted/rejected/queue full/unavailable/timeout/cancel/failure
- circuit closed/open/half-open
- IP-6 success/input unavailable/comparison state propagation
- logging success/no-op/failure
- response identity/deep equality/order/page metadata
- duplicate dispatch deterministic result
- structured record privacy/schema/order
- forbidden dependency/annotation/thread/persistence scan

전용 task:

```text
searchShadowWiringContractTest
```

## 15. 단계별 누적 기록

### IP-7.1 Baseline & Backend Inventory

- 목적: legacy path와 hook 가능 지점 확인
- 변경 파일: 없음
- 검증: controller/service/repository/DTO/config 실제 확인
- 보완: production hook 없이 목표 달성 가능 판정
- 잔여 리스크: 실제 hook 삽입 시 protected source proposal 필요

### IP-7.2 Module & Hook Contract

- 목적: backend 비의존 wiring capability
- 변경: 신규 모듈, settings 최소 등록
- 구현: hook/no-op/backend mirror adapter
- 검증: Java 21 compile
- 보완: response authority를 문자열 `legacy`로 명시
- 리스크: production Spring wiring 없음

### IP-7.3 Activation, Sampling & Circuit

- 목적: 자동 활성화 차단
- 구현: test-only profile/allow gate, default-zero sampling, fixed circuit boundary
- 검증: missing/blank/unknown/prod profile 차단
- 보완: whitespace mode를 disabled 처리
- 리스크: production activation authority 미정

### IP-7.4 Executor & Dispatch

- 목적: backpressure/timeout 격리
- 구현: finite capacity/concurrency contract, typed statuses, dispatcher
- 검증: rejection/queue full/unavailable/timeout/cancel/failure fail-open
- 보완: IP-6 상태를 receipt에 정확히 전파
- 리스크: production executor budget 미정

### IP-7.5 Logging & Privacy

- 목적: 비영속 비교 관측 경계
- 구현: no-op/in-memory port, structured record
- 검증: raw query·raw ID·private payload 부재
- 보완: mismatch와 warning code 안정 정렬
- 리스크: retention/sink owner 미정

### IP-7.6 Regression & Protection

- 목적: 기존 계약/추천/SQL 보호
- 검증: IP-7~IP-1, Recommendation 정적 회귀, 320 hashes, SQL 26 hashes
- 보완: package 재추출 재검증
- 리스크: Gradle distribution DNS failure

## 16. 자체 리뷰

### 1차 — Authority, Status, Executor

발견 `3`, 수정 `3`, 보류 `0`.

1. wiring authority가 legacy response authority를 명시하지 않음
2. executor finite capacity/concurrency가 인터페이스에 드러나지 않음
3. IP-6 input/failure 상태가 completed로 축약될 수 있음

### 2차 — Receipt, Observability, Boundary

발견 `4`, 수정 `4`, 보류 `0`.

1. status별 receipt field 불변조건 부족
2. warning code가 structured record에서 누락
3. 실제 IP-6 unavailable path 연결 검증 부족
4. page metadata response 보존 테스트 부족

수정 후 IP-7~IP-1과 보호 해시를 재검증했다.

## 17. 보호 기준선

- protected source: `320/320 exact`
- canonical SQL `01..26`: `26/26 exact`
- Controller/Service/Repository/DTO/JPQL/SecurityConfig: 변경 없음
- Recommendation source/P2 evidence/exposure/metric: 변경 없음
- 신규 DB migration/SQL/index: 없음
- production application config: 변경 없음

## 18. 현재 상태

```text
IP-7: COMPLETE
Search Shadow Hook Contract: IMPLEMENTED
Shadow Wiring Capability: IMPLEMENTED
Shadow Activation: DISABLED_BY_DEFAULT
Production Shadow Activation: NOT ENABLED
Deterministic Sampling: IMPLEMENTED / DEFAULT_ZERO
Bounded Executor Boundary: IMPLEMENTED / NON_PRODUCTION
Comparison Logging Port: IMPLEMENTED / NON_PERSISTENT
Legacy Response Authority: MAINTAINED
Response Impact: NONE
Search API Cutover: NOT STARTED
Search Persistence/Exposure: NOT IMPLEMENTED
Production Cursor/Release Gate Authority: NOT ENABLED
```

## 19. 잔여 리스크와 후속 조건

미결정:

- actual retrieval/index/runtime input source
- Operations visibility/eligibility owner
- SearchRun/snapshot/search exposure writer
- query/shadow evidence retention owner
- production cursor key/rotation owner
- executor concurrency/queue/latency/error budget
- activation/rollback/kill-switch authority

후속 후보:

```text
IP-8 Search Shadow Activation Readiness & Regression Closure
```

허용 후보:

- protected source hook proposal
- disabled-mode production-equivalence 검증
- executor/performance budget contract
- kill-switch/rollback/observability retention contract
- full backend regression

실제 production activation과 response replacement는 계속 `HOLD`다.
