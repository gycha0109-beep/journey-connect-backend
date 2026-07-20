# IP-9 Verification and Self Review

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-9-verification-self-review-v1` |
| 상태 | `DIRECT_PASS / GRADLE_AND_BACKEND_NOT_EXECUTED` |

## 1. 직접 검증

Java 21 `javac --release 21 -Xlint:all -Werror`로 독립 계약 모듈과 Recommendation core를 깨끗한 임시 디렉터리에 컴파일했다.

| 검증 | 결과 |
|---|---|
| IP-1 common contracts | `739 PASS` |
| IP-3 Search contracts | `425 PASS` |
| IP-4 compatibility | `584 PASS` |
| IP-5 runtime | `850 PASS` |
| IP-6 integration | `972 PASS` |
| IP-7 wiring | `1700 PASS` |
| IP-8 readiness | `2560 PASS` |
| Recommendation Foundation/Wave1~7 | `PASS` |
| Golden/Isolation | `PASS` |
| P1 Core | `17 PASS` |
| P2 Core | `23 PASS` |
| backend-local bridge stub compile | `PASS` |
| backend-local bridge direct behavior | `6 PASS` |

근거: `verification/ip9/IP9_DIRECT_VERIFICATION.log`

## 2. Gradle 및 backend 상태

Gradle Wrapper 8.14.5를 실제 실행했으나 distribution 설치 단계에서 중단됐다.

```text
java.net.UnknownHostException: services.gradle.org
```

따라서 다음은 `NOT_EXECUTED`다.

- `tasks --all`
- `ip9ControlledBackendHookRegression`
- backend `test`
- `p0Verification p1Verification p2Verification`
- `check`
- Spring context/MVC/JUnit XML
- Docker/Testcontainers/PostgreSQL backend tests

근거: `verification/ip9/IP9_GRADLE_EXECUTION.log`

## 3. 보호 검증

| 대상 | 결과 |
|---|---|
| 기존 Recommendation·SQL protected manifest | `320/320 exact` |
| canonical SQL 별도 manifest | `26/26 exact` |
| Controller | `APPROVED_DELTA` |
| Service/Repository/DTO/SecurityConfig | `EXACT` |
| production application resources | `EXACT` |
| Recommendation source | `EXACT` |

기존 320 manifest는 Controller를 포함하지 않는다. Controller와 backend production 경계는 별도 pre/post manifest로 검증했다.

## 4. 자체 리뷰 1 — 구조·권한

발견 및 보완:

1. Request factory가 `Pageable`과 완성된 legacy page metadata의 불일치를 명시적으로 차단하지 않았다.
   - page/size exact validation을 추가했다.
2. IP-9 static test가 IP-8 보호 manifest 파일명을 잘못 참조했다.
   - 실제 `IP8_PROTECTED_BASELINE_EXPECTED_SHA256.txt`로 교정했다.
3. 기존 320 protected manifest에 Controller가 포함된다는 잘못된 가정이 있었다.
   - 320/320 exact 검증과 backend Controller 승인 delta 검증을 분리했다.

재검증: 직접 전체 회귀 및 hash `PASS`.

## 5. 자체 리뷰 2 — 회귀·운영 실패

발견 및 보완:

1. hook failure unit scenario가 page metadata mismatch에서 먼저 종료되어 실제 hook exception 경로를 검증하지 못했다.
   - matching page fixture와 hook invocation counter를 적용했다.
2. external Gradle command의 root path를 재검토했다.
   - `jc-backend`가 Gradle root이므로 `test`, `p0Verification`, `p1Verification`, `p2Verification`, `check`를 root task로 문서화했다.
3. temporary compiled classes가 최종 package에 남을 수 있었다.
   - direct runner를 `/tmp` 기반으로 교체하고 임시 class 디렉터리를 제거했다.

재검증: IP-1~IP-8, Recommendation, bridge direct regression `PASS`; protected 320/320 및 SQL 26/26 `PASS`.

## 6. 외부 검증 명령

실행 위치: `jc-backend`

Linux/macOS:

```bash
./gradlew --no-daemon --stacktrace --version
./gradlew --no-daemon --stacktrace tasks --all
./gradlew --no-daemon --stacktrace ip9ControlledBackendHookRegression
./gradlew --no-daemon --stacktrace ip8SearchRegressionClosure
./gradlew --no-daemon --stacktrace ip1CompatibilityContractTest
./gradlew --no-daemon --stacktrace test
./gradlew --no-daemon --stacktrace p0Verification p1Verification p2Verification
./gradlew --no-daemon --stacktrace check
```

Windows:

```powershell
.\gradlew.bat --no-daemon --stacktrace --version
.\gradlew.bat --no-daemon --stacktrace tasks --all
.\gradlew.bat --no-daemon --stacktrace ip9ControlledBackendHookRegression
.\gradlew.bat --no-daemon --stacktrace ip8SearchRegressionClosure
.\gradlew.bat --no-daemon --stacktrace ip1CompatibilityContractTest
.\gradlew.bat --no-daemon --stacktrace test
.\gradlew.bat --no-daemon --stacktrace p0Verification p1Verification p2Verification
.\gradlew.bat --no-daemon --stacktrace check
```

Backend DB regression requires Docker/Testcontainers-compatible PostgreSQL 15, or the existing external DB contract:

```text
JC_TEST_DB_URL
JC_TEST_DB_USERNAME
JC_TEST_DB_PASSWORD
JC_TEST_DB_RESET=true
```

## 7. 현재 판정

```text
Implementation: COMPLETE
Direct contract regression: PASS
Gradle configuration/runtime: NOT_EXECUTED
Backend Spring/Testcontainers: NOT_EXECUTED
Final state: IMPLEMENTATION_COMPLETE / EXTERNAL_ATTESTATION_PENDING
```
