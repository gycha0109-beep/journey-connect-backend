# IP-8 Search Shadow Activation Readiness & Regression Closure

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `IP-8` |
| 계약 ID | `search-shadow-activation-readiness-v1` |
| 회귀 계약 ID | `ip-8-search-regression-closure-v1` |
| 상태 | `COMPLETE / READINESS_ASSESSED / PRODUCTION_INACTIVE` |
| 기준 단계 | `IP-2..IP-7 COMPLETE` |
| Primary readiness decision | `READY_FOR_CONTROLLED_HOOK_PROPOSAL` |
| Production activation decision | `HOLD_FOR_OWNER_DECISIONS` |
| production hook insertion | `NONE` |
| production shadow activation | `NOT ENABLED` |
| persistence/exposure/release/cursor authority | `NONE` |

## 2. 목적

IP-8은 Search shadow를 활성화하지 않는다. IP-7까지 구현된 disabled/no-op 구조를 실제 backend에 적용할 수 있는지 평가하고, protected source를 변경하지 않은 상태에서 다음을 닫는다.

1. `/api/v1/explore` 실행 경로와 hook 후보 비교
2. controlled hook change proposal
3. disabled-mode production-equivalence contract
4. activation prerequisite·budget·kill-switch·rollback·observability 계약
5. 외부 Gradle 8.14.5 및 Spring/Testcontainers 검증자가 즉시 실행할 unified task와 명령

```text
Readiness evaluation = allowed
Disabled-mode regression = allowed
Production hook insertion = prohibited
Production activation = prohibited
```

## 3. 실제 시작 기준선

- Recommendation P0/P1/P2 technical: `CLOSED`
- P2 production: `HOLD`
- IP-2: `COMPLETE / CONTRACT_ONLY`
- IP-3: `COMPLETE`
- IP-4: `COMPLETE / READ_ONLY`
- IP-5: `COMPLETE / IN_MEMORY`
- IP-6: `COMPLETE / DISABLED_SHADOW_BOUNDARY`
- IP-7: `COMPLETE / DISABLED_SHADOW_WIRING`
- IP-8 readiness assertions: `2560 PASS`
- IP-7 assertions: `1700 PASS`
- IP-6 assertions: `972 PASS`
- IP-5 assertions: `850 PASS`
- IP-4 assertions: `584 PASS`
- IP-3 assertions: `425 PASS`
- IP-1 common assertions: `739 PASS`
- protected source: `320/320 exact`
- canonical SQL: `01..26 exact`

## 4. Backend explore inventory

실제 경로:

```text
GET /api/v1/explore
  → PostController.explore(keyword, region, Pageable)
  → PostService.explore(keyword, region, pageable)
  → JourneyPostRepository.explore(keyword, region, pageable)
  → PostService.summaries(Page<JourneyPost>)
  → PageResponse<PostDtos.Summary>
  → ApiResponse.ok(...)
```

### 4.1 Controller

- path: `GET /api/v1/explore`
- request: optional `keyword`, optional `region`, Spring `Pageable`
- default page size: `20`
- authentication principal: 사용하지 않음
- security: public GET permit
- response assembly: `postService.explore(...)` 결과를 `ApiResponse.ok`로 반환

### 4.2 Service와 transaction

- `PostService` class는 `@DatabaseTransactional(role = APP, readOnly = true)`
- `explore`는 repository page를 `PostDtos.Summary`로 변환해 `PageResponse`를 생성
- Controller가 Service return을 받은 시점에는 proxied service transaction이 종료된 뒤이므로 controller return boundary가 transaction 점유를 최소화한다.

### 4.3 Repository

- keyword match: title/content/region local/ko/en의 case-insensitive substring
- region filter: slug/local/ko/en exact case-insensitive match
- visibility: `PUBLISHED`, `PUBLIC`, moderation `VISIBLE`, active author
- order: `publishedAt DESC, id DESC`
- pagination: Spring offset `Pageable`
- SearchRun/snapshot/cursor/exposure/ranking policy 없음

### 4.4 Exception 및 latency 경계

- legacy repository/service exception은 기존 global exception mapping을 따른다.
- 현재 hook이 없으므로 shadow exception·timeout·queue 상태가 request path에 들어오지 않는다.
- future hook은 legacy response가 생성된 뒤 dispatch receipt를 무시하는 형태여야 한다.

## 5. Hook 후보 비교

| 후보 | 변경 | transaction 위험 | latency/exception 위험 | 테스트·rollback | 판정 |
|---|---|---:|---:|---|---|
| A. Controller 반환 직전 | `PostController` 1개 protected source + 신규 config/adapter | 낮음 | hook 격리 필요 | 명확 | **권장 proposal** |
| B. Service response 조립 직후 | `PostService` protected source | 높음—read transaction 안 | request latency 결합 가능 | 보통 | 비권장 |
| C. Service decorator | bean replacement/qualifier 필요 | 중간 | Spring wiring 복잡 | rollback 가능 | 대안 |
| D. Facade/wrapper | Controller dependency 변경 | 낮음 | facade 격리 가능 | 좋음 | A보다 파일 증가 |
| E. Event/listener | event type/publisher 필요 | 낮음 | async owner·event semantics 추가 | 복잡 | 과도함 |
| F. 호출부 변경 없음 | 변경 0 | 없음 | 없음 | 현재와 동일 | **IP-8 실제 상태** |

### 최종 추천

후속 승인 단계의 최소 proposal은 **A. Controller 반환 직전 hook**이다. Service transaction 밖에서 legacy response를 먼저 확정하고 `SearchShadowHook.dispatch(...)` 반환값을 버린다.

IP-8에서는 A를 적용하지 않았다. 실제 상태는 F다.

## 6. 신규 readiness module

```text
jc-search-readiness
  → jc-search-shadow-wiring
  → jc-search-integration
  → jc-search-runtime / compatibility / contracts
```

역할:

- prerequisite matrix와 두 readiness 차원
- controlled hook proposal invariants
- unresolved production budget contract
- kill-switch/rollback contract
- observability/retention privacy contract
- disabled-mode equivalence verifier
- external Gradle/backend regression manifest

금지 dependency:

- `jc-backend`
- `jc-recommendation-core`
- Spring/JPA/DB/provider

## 7. Disabled-mode production equivalence

`SearchDisabledModeEquivalenceVerifier`와 IP-7 실제 `DefaultSearchShadowDispatcher.disabledByDefault`를 함께 검증했다.

Disabled 기대값:

```text
executor submissions = 0
runtime/integration invocations = 0
runtime input provider invocations = 0
comparison invocations = 0
comparison log calls = 0
response object identity change = 0
response value/order/page metadata change = 0
```

검증 항목:

- object identity 및 deep equality
- item order
- page/size/totalElements/totalPages/last
- exception/serialization semantics contract
- receipt에 fabricated integration/log evidence 없음

현재 production hook이 없으므로 HTTP status와 실제 Controller exception mapping은 코드상 완전히 동일하다. 후속 hook proposal 테스트는 이를 별도 Spring regression으로 다시 검증해야 한다.

## 8. Readiness 판정

### 8.1 Primary decision

```text
READY_FOR_CONTROLLED_HOOK_PROPOSAL
```

근거:

- backend inventory 완료
- hook 후보 비교 및 최소 pseudo-diff 완료
- production hook 미삽입 확인
- disabled-mode equivalence 직접 검증
- unified Search/recommendation-core Gradle task 정의
- rollback/kill-switch/privacy 계약 완료

이 판정은 production 활성화 승인이 아니다.

### 8.2 Production activation decision

```text
HOLD_FOR_OWNER_DECISIONS
```

다음이 unresolved다.

- retrieval/index/runtime input source
- Operations visibility/eligibility authority
- SearchRun/snapshot/evidence/exposure writers
- query/evidence retention·deletion owner
- executor/queue/timeout/latency/error budget
- circuit/kill-switch/activation/rollback/on-call authority
- production cursor key/rotation
- 외부 Gradle 및 backend Spring/Testcontainers full PASS

## 9. Budget contract

Production 숫자는 확정하지 않았다. 다음 차원은 모두 `UNRESOLVED`다.

- maxConcurrency, queueCapacity, taskTimeout, endToEndShadowBudget
- hook dispatch/executor submission/queue wait/runtime/comparison/logging/total duration
- rejection/queue-full/late-result/cancellation/circuit-open policies

불변조건:

- unbounded queue 금지
- common ForkJoinPool 금지
- request thread에서 full runtime 실행 금지
- queue full/unavailable이면 shadow skip
- timeout/late result는 legacy response와 무관
- test fixture 숫자를 production proposal 숫자로 승격하지 않음

## 10. Kill-switch와 rollback

Kill-switch priority:

1. global disabled
2. profile disabled
3. sample rate 0
4. circuit open
5. executor unavailable

Shadow activation에는 fail-closed, legacy response에는 fail-open이다.

Rollback levels:

- Level 0: sample 0
- Level 1: mode disabled
- Level 2: no-op bean replacement
- Level 3: hook call removal
- Level 4: module dependency removal

상세는 [IP-8 Rollback/Kill-switch/Observability Contract](IP-8-ROLLBACK-KILL-SWITCH-AND-OBSERVABILITY-CONTRACT.md)를 따른다.

## 11. Observability/retention

현재 logging port는 no-op/in-memory이며 persistence authority가 없다.

허용 record:

- reference time, correlation fingerprint
- mode/sampling/dispatch/runtime/comparison status
- deterministic mismatch/severity/count metrics
- duration buckets
- policy/build versions

금지:

- raw/normalized query text
- full request/response/candidate payload
- auth token, raw user/session ID, precise location/private metadata

Storage/access/retention/deletion/incident hold/aggregation/audit owner와 기간은 `UNRESOLVED`다.

## 12. Unified Gradle regression

신규 root task:

```text
ip8SearchRegressionClosure
```

Dependency graph:

- IP-1 common contract
- IP-3 Search contract
- IP-4 compatibility
- IP-5 runtime
- IP-6 integration
- IP-7 wiring
- IP-8 readiness
- Recommendation Foundation/Wave1~7
- Golden/Isolation
- P1 Core
- P2 Core

`ignoreFailures`를 사용하지 않으며 외부 Exec를 감싸지 않는다. Backend Spring/Testcontainers는 별도 명령으로 남긴다.

## 13. 외부 재검증 명령

실행 위치: `jc-backend`

### Linux/macOS

```bash
./gradlew --offline tasks --all
./gradlew --offline ip8SearchRegressionClosure
./gradlew --offline :test
./gradlew --offline :p0Verification :p1Verification :p2Verification
./gradlew --offline :ip1CompatibilityContractTest
./gradlew --offline check
```

### Windows

```powershell
.\gradlew.bat --offline tasks --all
.\gradlew.bat --offline ip8SearchRegressionClosure
.\gradlew.bat --offline :test
.\gradlew.bat --offline :p0Verification :p1Verification :p2Verification
.\gradlew.bat --offline :ip1CompatibilityContractTest
.\gradlew.bat --offline check
```

Offline cache에 Gradle 8.14.5 또는 Maven/plugin artifacts가 없으면 `--offline`은 실패한다. 네트워크가 가능한 환경에서는 `--offline`을 제거한다.

Backend Spring integration 조건:

- Java 21
- Gradle 8.14.5
- Docker/Testcontainers와 `postgres:15-alpine`, 또는 `JC_TEST_DB_URL` external PostgreSQL 15
- canonical SQL `01..26`
- `jc-backend/src/test/resources/application.yml`

## 14. 현재 환경 Gradle 상태

실제 wrapper를 다음 명령으로 실행했다.

```text
./gradlew --offline tasks --all --no-daemon
./gradlew --offline ip8SearchRegressionClosure --no-daemon
./gradlew --offline test --no-daemon
./gradlew --offline p0Verification p1Verification p2Verification ip1CompatibilityContractTest --no-daemon
./gradlew --offline check --no-daemon
```

모든 명령은 Gradle 8.14.5 distribution 설치 단계에서 `java.net.UnknownHostException: services.gradle.org`로 종료됐다. 로컬 wrapper distribution cache도 없어 Gradle configuration과 task graph 실행까지 진입하지 못했다.

따라서 현재 판정은 다음과 같다.

```text
Direct contract regression: PASS
Gradle configuration/task execution: NOT_EXECUTED
Backend Spring/Testcontainers regression: NOT_EXECUTED
```

직접 회귀 최종 결과:

- IP-8 readiness: `2560 PASS`
- IP-7 wiring: `1700 PASS`
- IP-6 integration: `972 PASS`
- IP-5 runtime: `850 PASS`
- IP-4 compatibility: `584 PASS`
- IP-3 Search contract: `425 PASS`
- IP-1 common contract: `739 PASS`
- Recommendation Foundation/Wave1~7, Golden/Isolation: `PASS`
- P1 Core: `17 PASS`
- P2 Core: `23 PASS`

실제 wrapper 실행 결과는 verification 로그에 별도 기록한다.

판정 필드는 반드시 분리한다.

```text
Direct contract regression: PASS/FAIL
Gradle configuration/task execution: PASS/FAIL/NOT_EXECUTED
Backend Spring/Testcontainers regression: PASS/FAIL/NOT_EXECUTED
```

DNS/cache/Docker 문제는 test PASS가 아니다.

## 15. 단계별 누적 기록

### IP-8.1 Baseline & inventory

- 목적: backend/Search/Gradle 실구조 확인
- 변경: 없음
- 검증: IP-2..IP-7 문서, 7개 Search/common modules, wrapper/tasks, backend path 확인
- 보완: Service transaction 경계와 Controller post-transaction boundary 구분
- 리스크: actual hook은 protected source 변경 필요

### IP-8.2 Readiness contracts

- 목적: readiness·budget·kill-switch·rollback·retention을 executable contract로 고정
- 변경: 신규 `jc-search-readiness/**`
- 검증: Java 21 `-Xlint:all -Werror`, direct runner
- 보완: proposal readiness와 production activation readiness를 별도 판정
- 리스크: production owner/value unresolved

### IP-8.3 Regression closure

- 목적: 외부 검증자가 한 task로 Search/recommendation-core 회귀 수행
- 변경: settings module 등록, root `ip8SearchRegressionClosure`
- 검증: task dependency static contract 및 실제 wrapper attempt
- 보완: backend DB tests는 별도 explicit command
- 리스크: current environment Gradle distribution/cache availability

### IP-8.4 Independent review

- 목적: activation·authority 과장 방지
- 검증: production config/source/hash/SQL 및 forbidden dependency scan
- 1차 리뷰: `4건 발견 / 4건 수정 / 0건 보류`
  - fixture readiness decision 직접 결속
  - activation blocker 수 정합화
  - observability collection canonical ordering
  - rollback null safety
- 2차 리뷰: `4건 발견 / 4건 수정 / 0건 보류`
  - backend root Gradle task path 교정
  - retention readiness의 incident/aggregation 조건 보강
  - production budget approved value 안전성
  - disabled-equivalence fixture 결속 강화
- 잔여: external Gradle + Spring/Testcontainers attestation

## 16. 보호 결과

- Controller/Service/Repository/DTO/JPQL/SecurityConfig: 변경 없음
- production application config: 변경 없음
- recommendation production source: 변경 없음
- canonical SQL: 변경 없음
- production hook/profile/executor: 없음
- persistence/exposure/release/cursor authority: 없음

## 17. 후속 단계

후속 후보는 `IP-9 Controlled Backend Hook Implementation`이지만 자동 진입하지 않는다.

필수 게이트:

1. controlled hook proposal 승인
2. protected source change 승인
3. runtime input/visibility owner 확정
4. budget·kill-switch·activation·rollback·retention owner 승인
5. `ip8SearchRegressionClosure` Gradle PASS
6. backend Spring/Testcontainers full PASS

현재 IP-9 진입 상태:

```text
HOLD_FOR_OWNER_DECISIONS
+ HOLD_FOR_EXTERNAL_GRADLE_BACKEND_ATTESTATION
```
