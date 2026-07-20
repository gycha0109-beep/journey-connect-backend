# IP-8 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-8-handoff-v1` |
| 상태 | `COMPLETE / EXTERNAL_GRADLE_BACKEND_ATTESTATION_PENDING` |
| Primary readiness | `READY_FOR_CONTROLLED_HOOK_PROPOSAL` |
| Production activation | `HOLD_FOR_OWNER_DECISIONS` |
| 다음 후보 | `IP-9 Controlled Backend Hook Implementation` |

## 완료

- backend `/api/v1/explore` inventory와 transaction/hook 후보 비교
- controlled hook change proposal—source 미적용
- 신규 `jc-search-readiness` executable readiness contracts
- disabled-mode response/executor/runtime/logging zero-call equivalence
- activation prerequisite matrix
- unresolved executor/latency/error budget contract
- kill-switch L0..L4 rollback contract
- privacy-safe observability/retention contract
- root `ip8SearchRegressionClosure` task
- 외부 Gradle 8.14.5 및 backend Spring/Testcontainers 명령

## 변경 파일

- 신규 `jc-search-readiness/**`
- `jc-backend/settings.gradle.kts`: readiness module 등록
- `jc-backend/build.gradle.kts`: unified regression task만 추가
- IP-8 문서 5개와 Intelligence README
- verification/ip8 증거

Production Controller/Service/Repository/DTO/config는 변경하지 않았다.

## Readiness decision

```text
Primary: READY_FOR_CONTROLLED_HOOK_PROPOSAL
Production activation: HOLD_FOR_OWNER_DECISIONS
```

Primary READY는 proposal과 disabled regression이 후속 승인에 제출 가능하다는 의미다. production shadow enable 또는 IP-9 merge 승인이 아니다.

## Unified task

실행 위치: `jc-backend`

```bash
./gradlew --offline ip8SearchRegressionClosure
```

포함:

- IP-1 common
- IP-3..IP-8
- Recommendation Foundation/Wave1~7
- Golden/Isolation
- P1/P2 Core

Backend DB regression은 별도 실행한다.

```bash
./gradlew --offline :test
./gradlew --offline :p0Verification :p1Verification :p2Verification
./gradlew --offline :ip1CompatibilityContractTest
./gradlew --offline check
```

Windows에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용한다.

## External environment requirements

- Java 21
- Gradle Wrapper 8.14.5
- cached distribution/plugins/dependencies for `--offline`, 또는 network
- Docker/Testcontainers + `postgres:15-alpine`
- 대안: PostgreSQL 15와 `JC_TEST_DB_URL/USERNAME/PASSWORD`, 필요 시 `JC_TEST_DB_RESET=true`

## 검증 상태 분리

최종 verification log의 실제 값:

- Direct contract regression: `PASS`
  - IP-8 `2560`
  - IP-7 `1700`
  - IP-6 `972`
  - IP-5 `850`
  - IP-4 `584`
  - IP-3 `425`
  - IP-1 `739`
  - Recommendation Foundation/Wave1~7, Golden/Isolation `PASS`
  - P1 Core `17`, P2 Core `23`
- Gradle configuration/task execution: `NOT_EXECUTED`
  - wrapper distribution 단계 `UnknownHostException: services.gradle.org`
- Backend Spring/Testcontainers regression: `NOT_EXECUTED`
  - Gradle task 실행 단계에 도달하지 못함

실행하지 못한 항목은 `NOT EXECUTED`이며 PASS가 아니다.

## 보호 기준선

- protected source `320/320 exact`
- canonical SQL `01..26 exact`
- Controller/Service/Repository/DTO/JPQL/SecurityConfig exact
- production application config exact
- Recommendation exact
- P2 production `HOLD`

## 잔여 blocker

- actual retrieval/index/runtime input
- visibility/eligibility owner
- writers: SearchRun/snapshot/evidence/exposure
- query/evidence privacy retention/deletion
- executor/latency/error/circuit budgets
- kill-switch/activation/rollback/on-call authority
- production cursor key/rotation
- external Gradle and Spring/Testcontainers attestation

## IP-9 gate

다음을 모두 충족하기 전 `HOLD`다.

1. proposal/protected source change 승인
2. owner/budget/retention 승인
3. Gradle unified regression PASS
4. backend Spring/Testcontainers full PASS
5. rollback/kill-switch authority 확정

## 최종 상태

```text
IP-8: COMPLETE
Controlled Hook Proposal: COMPLETE / NOT APPLIED
Disabled-mode Equivalence: VERIFIED BY DIRECT CONTRACT RUNNER
Unified Regression Task: IMPLEMENTED
Production Hook/Activation: NONE
Legacy /api/v1/explore: UNCHANGED
Protected Baseline: MAINTAINED
IP-9: HOLD_FOR_OWNER_DECISIONS_AND_EXTERNAL_ATTESTATION
```
