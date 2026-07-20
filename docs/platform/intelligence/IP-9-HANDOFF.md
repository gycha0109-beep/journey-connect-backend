# IP-9 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-9-handoff-v1` |
| 상태 | `IMPLEMENTATION_COMPLETE / EXTERNAL_ATTESTATION_PENDING` |
| production activation | `PROHIBITED / NOT_CONFIGURED` |
| 다음 후보 | `IP-10 Test/Stage Shadow Activation` |

## 완료

- `/api/v1/explore` Controller return boundary에 controlled backend-local hook 삽입
- 동일 legacy `PageResponse`를 유일한 response authority로 유지
- 기본 `DisabledExploreSearchShadowBridge` Spring bean
- active bridge/request factory는 명시적 조립 전용, 자동 활성화 없음
- factory/hook `RuntimeException` fail-open 격리
- legacy Service exception 시 hook zero-call
- Controller/MVC/wiring/static JUnit test 작성
- `ip9BackendHookContractTest`
- `ip9ControlledBackendHookRegression`
- 문서 4개와 verification evidence
- 자체 리뷰 2회 및 보완

## 변경 범위

### 승인된 production 변경

- `PostController.java`: Service 정상 반환 후 void hook 호출
- backend-local `com.jc.backend.search.shadow` 신규 package
- `build.gradle.kts`: `jc-search-shadow-wiring` dependency와 IP-9 tasks

### 비변경

- `PostService`
- `JourneyPostRepository`/JPQL
- `PostDtos`
- `SecurityConfig`
- production application resources
- canonical SQL `01..26`
- Recommendation source/evidence/metric/exposure authority

## 검증 상태

```text
Direct Search/Recommendation/bridge regression: PASS
Gradle 8.14.5 execution: NOT_EXECUTED
Backend Spring/JUnit/Testcontainers: NOT_EXECUTED
```

Gradle은 wrapper 다운로드 단계 `UnknownHostException: services.gradle.org`로 중단됐다. 미실행 항목은 PASS가 아니다.

## 보호 결과

- Recommendation·SQL protected manifest: `320/320 exact`
- canonical SQL: `26/26 exact`
- backend protected boundary:
  - Controller: `APPROVED_DELTA`
  - Service/Repository/DTO/SecurityConfig/config: `EXACT`
- production shadow property/profile: 없음
- persistence/exposure/release/cursor authority: 없음
- P2 Production: `HOLD`

## 외부 attestation gate

후속 검증자는 Java 21, Gradle 8.14.5와 Docker/Testcontainers 또는 PostgreSQL 15 환경에서 다음을 PASS해야 한다.

1. `tasks --all`
2. `ip9ControlledBackendHookRegression`
3. `ip8SearchRegressionClosure`
4. `ip1CompatibilityContractTest`
5. backend `test`
6. `p0Verification p1Verification p2Verification`
7. `check`
8. JUnit XML 집계 및 protected/hash 재검증

## IP-10 gate

현재 `HOLD`다. 다음이 모두 닫혀야 제안할 수 있다.

- 외부 Gradle/backend/PostgreSQL attestation PASS
- runtime input provider 확정
- visibility/eligibility owner 확정
- executor/latency/error budget 승인
- kill-switch/activation/rollback authority 승인
- query 및 evidence retention 정책 승인
- production profile에서 test-only 활성화가 불가능함을 재검증

## 최종 상태

```text
IP-9: IMPLEMENTATION_COMPLETE / EXTERNAL_ATTESTATION_PENDING
Controlled Hook Source Application: COMPLETE
Default Mode: DISABLED / NO_OP
Legacy Response Authority: MAINTAINED
Production Activation: PROHIBITED / NOT_CONFIGURED
Search API Cutover: NOT STARTED
Persistence/Exposure/Release/Cursor Authority: NONE
IP-10: HOLD
```
