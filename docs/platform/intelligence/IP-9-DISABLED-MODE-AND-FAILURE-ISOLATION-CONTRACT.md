# IP-9 Disabled Mode and Failure Isolation Contract

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-9-disabled-mode-failure-isolation-v1` |
| 상태 | `ACTIVE / PROTECTIVE` |
| 대상 | `/api/v1/explore` controlled hook |

## 1. Disabled-mode 계약

기본 backend application context에는 다음만 존재한다.

```text
ExploreSearchShadowBridge
  = DisabledExploreSearchShadowBridge
```

기본 동작:

- request factory 호출 `0`
- Search hook 호출 `0`
- executor submission `0`
- runtime input 호출 `0`
- comparison 호출 `0`
- logging 호출 `0`
- persistence/exposure 호출 `0`

Production application 설정에는 `search.shadow.*` 활성 값을 추가하지 않았다. 임의 property를 전달해도 기본 configuration은 disabled bridge만 생성한다.

## 2. Response authority

Hook 전후 다음이 동일해야 한다.

- legacy response object identity
- response data value
- item count와 order
- page/size/totalElements/totalPages/last
- `ApiResponse` wrapper 의미
- HTTP status와 JSON serialization

Hook receipt는 API DTO나 header에 포함되지 않는다.

## 3. Failure isolation

다음 일반 runtime failure는 bridge 내부에서 격리한다.

- request context/factory failure
- compatibility conversion failure
- page metadata inconsistency
- Search hook failure
- sampler/circuit/executor/runtime/comparison/logging 계층에서 hook이 전달한 `RuntimeException`

결과:

```text
legacy success + shadow RuntimeException
→ legacy success unchanged
```

다음은 별도다.

```text
legacy Service exception
→ hook not invoked
→ original exception preserved
```

JVM fatal `Error`를 broad catch로 삼키지 않는다.

## 4. Active/test-only assembly 경계

`DefaultExploreSearchShadowBridge`와 `DefaultExploreShadowHookRequestFactory`는 자동 Spring component가 아니다. 후속 승인된 test/stage configuration에서만 명시적으로 조립할 수 있다.

Actual runtime input provider가 없으면 fabricated execution을 만들지 않는다. IP-4 compatibility output은 runtime candidate source가 아니다.

## 5. 금지된 authority

- response authority: 없음
- persistence authority: 없음
- exposure authority: 없음
- release gate authority: 없음
- production cursor authority: 없음
- production activation authority: 없음

## 6. 외부 attestation 필수 항목

Gradle 사용 가능 환경에서 다음을 확인해야 한다.

- Spring bean exactly one disabled bridge
- Controller/MVC serialization exact
- JUnit failure-isolation scenarios
- backend 전체 `test/check`
- P0/P1/P2 verification
- Docker/Testcontainers 또는 external PostgreSQL 경로
