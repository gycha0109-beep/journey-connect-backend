# IP-11 External Attestation Plan

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-11-external-attestation-plan-v1` |
| 상태 | `PLAN_COMPLETE / EXECUTION_DEFERRED` |
| target source SHA | `97931cefa9c591a603dc2ce8219678eb2a46214e1d5a2dc78388fdd7400c321f` plus IP-11 document-only delta |

## 1. Executor and environment

| Item | Requirement |
|---|---|
| Executor | independent verifier, named assignee required |
| Java | 21 |
| Gradle | Wrapper `8.14.5` |
| OS | Linux/macOS/Windows supported by wrapper |
| DB | PostgreSQL 15 via Testcontainers-compatible Docker or approved external DB |
| Network/cache | Gradle distribution and dependency cache available |
| Working directory | `jc-backend` |

External DB variables when used:

```text
JC_TEST_DB_URL
JC_TEST_DB_USERNAME
JC_TEST_DB_PASSWORD
JC_TEST_DB_RESET=true
```

H2 does not replace PostgreSQL integration attestation.

## 2. Required commands

```text
java -version
./gradlew --version
./gradlew tasks --all
./gradlew --no-daemon --stacktrace ip9BackendHookContractTest
./gradlew --no-daemon --stacktrace ip9ControlledBackendHookRegression
./gradlew --no-daemon --stacktrace ip10TestStageShadowActivationRegression
./gradlew --no-daemon --stacktrace ip10CombinedExternalRegressionClosure
./gradlew --no-daemon --stacktrace ip8SearchRegressionClosure
./gradlew --no-daemon --stacktrace test
./gradlew --no-daemon --stacktrace p0Verification p1Verification p2Verification
./gradlew --no-daemon --stacktrace check
```

Windows uses `gradlew.bat` with equivalent task names.

## 3. Required evidence package

- environment manifest: OS, Java, Gradle, Docker/PostgreSQL versions
- command transcript with exit codes
- Gradle task graph / `tasks --all`
- JUnit XML files and aggregate tests/failures/errors/skipped
- Spring default/test/stage/prod-equivalent context results
- Testcontainers or external PostgreSQL route and reset evidence
- protected source and SQL manifests
- source ZIP/final ZIP SHA
- production resource diff
- failure logs and remediation commits if any
- verifier name, timestamp, signature/approval record

## 4. Failure ownership and rerun

| Failure | Primary owner |
|---|---|
| build/task configuration | Backend Owner |
| Search contract/runtime test | Search/Intelligence Owner |
| Spring bean/profile/wiring | Backend Owner |
| PostgreSQL/Testcontainers | Backend + Data/DB owner |
| protected hash mismatch | System Coordination |
| privacy/security finding | Security/Privacy Approver |

Any source change invalidates previous attestation and requires full rerun.

## 5. Validity

Attestation is valid for at most 30 days and only for the exact source/package SHA, Gradle task graph, source schema and activation contract. It expires immediately on hook/source/config/schema/policy/retention/cohort changes or a relevant incident.

## 6. Current status

```text
Gradle/JUnit/Spring: NOT EXECUTED — USER-DIRECTED SKIP
Docker/Testcontainers/PostgreSQL: NOT EXECUTED — USER-DIRECTED SKIP
External attestation decision: DEFERRED / OPEN GATE
```

No prior direct assertion count is accepted as a replacement for this evidence.
