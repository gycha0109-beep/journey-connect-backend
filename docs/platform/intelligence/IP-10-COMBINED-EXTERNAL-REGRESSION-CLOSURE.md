# IP-10 Combined External Regression Closure

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-10-combined-external-regression-closure-v1` |
| 상태 | `DIRECT_REGRESSION_PASS / GRADLE_AND_BACKEND_NOT_EXECUTED` |

## Gradle task

실제 backend Gradle root에 다음 task를 추가했다.

- `ip10TestStageShadowActivationRegression`
- `ip10CombinedExternalRegressionClosure`

`ip10CombinedExternalRegressionClosure`는 IP-9 regression, IP-10 tests, P0/P1/P2 verification, `check`에 의존하며 `ignoreFailures`를 사용하지 않는다.

## 직접 검증 결과

| 영역 | 결과 |
|---|---:|
| IP-1 common | 739 PASS |
| IP-3 Search | 425 PASS |
| IP-4 compatibility | 584 PASS |
| IP-5 runtime | 850 PASS |
| IP-6 integration | 972 PASS |
| IP-7 wiring | 1,700 PASS |
| IP-8 readiness | 2,560 PASS |
| IP-10 direct test/stage | 36 PASS |
| Recommendation Foundation/Wave1~7 | PASS |
| Golden/Isolation | PASS |
| P1 Core | 17 PASS |
| P2 Core | 23 PASS |
| Java 21 `-Xlint:all -Werror` | PASS |

## 미실행

사용자 지시에 따라 Gradle 및 Docker/Testcontainers/PostgreSQL 검증을 최종 단계에서 생략했다. 이전 wrapper 시도는 `services.gradle.org` DNS 해석 실패였다.

```text
Gradle 8.14.5 task execution: NOT EXECUTED — USER-DIRECTED SKIP
IP-9/IP-10 JUnit/Spring: NOT EXECUTED
backend test/check: NOT EXECUTED
PostgreSQL/Testcontainers: NOT EXECUTED
```

따라서 IP-9와 IP-10은 `IMPLEMENTATION_COMPLETE / EXTERNAL_ATTESTATION_PENDING`이며 COMPLETE/CLOSED가 아니다.

## 외부 검증 명령

실행 위치: `jc-backend`

```text
./gradlew --no-daemon --stacktrace tasks --all
./gradlew --no-daemon --stacktrace ip9BackendHookContractTest
./gradlew --no-daemon --stacktrace ip9ControlledBackendHookRegression
./gradlew --no-daemon --stacktrace ip10TestStageShadowActivationRegression
./gradlew --no-daemon --stacktrace ip10CombinedExternalRegressionClosure
./gradlew --no-daemon --stacktrace test
./gradlew --no-daemon --stacktrace p0Verification p1Verification p2Verification
./gradlew --no-daemon --stacktrace check
```
