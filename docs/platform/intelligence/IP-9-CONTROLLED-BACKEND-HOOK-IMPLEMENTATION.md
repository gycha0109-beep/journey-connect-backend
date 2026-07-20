# IP-9 Controlled Backend Hook Implementation

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `IP-9` |
| 계약 ID | `ip-9-controlled-backend-hook-implementation-v1` |
| 상태 | `IMPLEMENTATION_COMPLETE / EXTERNAL_ATTESTATION_PENDING` |
| 기준 | `IP-8 COMPLETE / READY_FOR_CONTROLLED_HOOK_PROPOSAL` |
| production activation | `PROHIBITED / NOT_CONFIGURED` |
| legacy response authority | `true` |
| Search response authority | `false` |
| DB/SQL | `UNCHANGED` |

## 2. 목적

IP-8에서 문서로만 확정한 controlled hook을 실제 `/api/v1/explore` Controller return boundary에 최소 삽입한다. Search Runtime 결과를 응답으로 사용하지 않고, 기본 Spring wiring을 완전한 no-op으로 유지한다.

이번 구현은 production shadow 활성화, Search API cutover, SearchRun/snapshot/exposure persistence 또는 production cursor authority를 생성하지 않는다.

## 3. 실제 시작 기준선

```text
GET /api/v1/explore
  → PostController.explore
  → PostService.explore
  → JourneyPostRepository.explore
  → PageResponse<PostDtos.Summary>
  → ApiResponse.ok(...)
```

- Service가 legacy `PageResponse`를 최종 조립한다.
- Repository는 published/public/moderation-visible/active-author 조건과 `publishedAt DESC, id DESC`를 적용한다.
- Service/Repository 내부와 transaction 경계는 변경하지 않았다.
- 기존 Controller의 직접 return만 controlled boundary 형태로 최소 분해했다.

## 4. 실제 wiring

```text
PostService.explore(...) exactly once
  → legacyResponse 확정
  → ExploreSearchShadowBridge.afterExplore(...)
  → 동일 legacyResponse로 ApiResponse.ok(...)
```

Controller 적용 코드 의미:

```java
PageResponse<PostDtos.Summary> legacyResponse =
        postService.explore(keyword, region, pageable);
exploreSearchShadowBridge.afterExplore(keyword, region, pageable, legacyResponse);
return ApiResponse.ok(legacyResponse);
```

Hook 반환값, receipt, runtime result 또는 comparison evidence는 응답 생성에 사용하지 않는다.

## 5. Backend-local bridge

신규 package:

```text
com.jc.backend.search.shadow
├─ ExploreSearchShadowBridge
├─ DisabledExploreSearchShadowBridge
├─ DefaultExploreSearchShadowBridge
├─ ExploreShadowHookRequestFactory
├─ DefaultExploreShadowHookRequestFactory
├─ ExploreShadowRequestContext
├─ ExploreShadowRequestContextProvider
└─ SearchShadowBackendConfiguration
```

### 5.1 기본 구현

`SearchShadowBackendConfiguration`은 `DisabledExploreSearchShadowBridge` 하나만 등록한다.

- property binding 없음
- profile binding 없음
- active hook bean 없음
- executor bean 없음
- runtime input provider bean 없음
- comparison logger bean 없음
- production resource 설정 변경 없음

설정 누락, 임의 property 또는 production-equivalent profile은 active bridge를 만들 수 없다.

### 5.2 Controlled implementation

`DefaultExploreSearchShadowBridge`는 명시적으로 조립해야만 사용할 수 있으며 Spring component가 아니다.

- request factory와 IP-7 `SearchShadowHook`을 생성자에서 받는다.
- factory/dispatch의 일반 `RuntimeException`을 bridge 내부에서 격리한다.
- fatal `Error`는 삼키지 않는다.
- dispatch receipt를 폐기하며 legacy response authority를 취득하지 않는다.

### 5.3 Request factory

`DefaultExploreShadowHookRequestFactory`는 IP-4 legacy compatibility representation과 IP-7 hook request를 조립한다.

- legacy response는 comparison representation으로만 사용한다.
- runtime candidate source로 재투입하지 않는다.
- `Pageable`과 완성된 `PageResponse`의 page/size가 다르면 mapping을 거부한다.
- raw query 또는 identifier를 production log에 쓰지 않는다.
- production cursor, policy owner 또는 persistence reference를 발명하지 않는다.

## 6. 변경 파일

### Production 변경

- `jc-backend/src/main/java/com/jc/backend/post/PostController.java`
- `jc-backend/build.gradle.kts`
- 신규 `jc-backend/src/main/java/com/jc/backend/search/shadow/**`

### Test 변경

- `PostControllerSearchShadowHookTest`
- `ExploreSearchShadowBridgeContractTest`
- `SearchShadowBackendConfigurationTest`
- `IP9ControlledBackendHookStaticTest`
- IP-8 direct regression이 controlled hook 상태를 정확히 인식하도록 기존 Search contract test 2개를 의미 변경 없이 보정

### 비변경

- `PostService`
- `JourneyPostRepository` 및 JPQL
- `PostDtos`
- `SecurityConfig`
- production application resources
- Recommendation production/core source
- canonical SQL `01..26`

## 7. Gradle 구성

Backend root에 최소 dependency를 추가했다.

```kotlin
implementation(project(":jc-search-shadow-wiring"))
```

신규 task:

```text
ip9BackendHookContractTest
ip9ControlledBackendHookRegression
```

`ip9ControlledBackendHookRegression` dependency:

```text
ip8SearchRegressionClosure
ip1CompatibilityContractTest
ip9BackendHookContractTest
```

`ignoreFailures` 또는 실패 무시 Exec 처리는 없다.

## 8. Authority 불변조건

1. legacy Service 반환값만 client response authority다.
2. hook receipt와 Search result는 응답에 합쳐지지 않는다.
3. 기본 context에서 bridge는 완전 no-op이다.
4. shadow failure는 legacy success를 실패로 바꾸지 않는다.
5. legacy Service exception이면 hook은 호출되지 않는다.
6. persistence/exposure/release-gate/production-cursor authority는 없다.
7. Search module은 backend로 역의존하지 않는다.
8. Service/Repository/DB/provider 호출은 신규 bridge에 없다.

## 9. 현재 검증 판정

- dependency-free Search/Recommendation direct regression: `PASS`
- backend-local bridge Java 21 stub compile/direct behavior: `PASS`
- Gradle configuration/task execution: `NOT_EXECUTED`
- backend Spring/JUnit/Testcontainers: `NOT_EXECUTED`

Gradle 미실행 항목은 PASS로 간주하지 않는다. 최종 단계 상태는 외부 attestation 전까지 `IMPLEMENTATION_COMPLETE / EXTERNAL_ATTESTATION_PENDING`이다.
