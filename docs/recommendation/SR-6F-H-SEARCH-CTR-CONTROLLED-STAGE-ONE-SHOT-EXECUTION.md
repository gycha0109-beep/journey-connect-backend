# SR-6F-H Search CTR Controlled Stage One-shot Execution

## 상태

```text
Stage: SR-6F-H
Metric: search-click-through-rate-v1
Policy: search-ctr-activation-finality-v1
Execution contract: search-ctr-stage-one-shot-v1
Implementation: IMPLEMENTED_PENDING_CI
Actual stage execution: NOT_PERFORMED
Execution status: BLOCKED_EXTERNAL_STAGE_ACCESS
Authorized runtime mode: NONPRODUCTION_MANUAL
Authorized environment: stage
Authorized login: jc_backend
Authorized window: [2026-08-06T08:00:00Z, 2026-08-06T09:00:00Z)
Authorized approval reference: approval:sr6fg-stage-20260806t0800z
Endpoint fingerprint: UNASSIGNED
Finality write: DISABLED
Merge/deploy: NOT_PERFORMED
Overall: IMPLEMENTED_EXECUTION_CONTROL_HOLD_EXTERNAL_STAGE_ACCESS
```

## 목적

SR-6F-G가 승인한 단 하나의 stage Search CTR projection 후보를 실제 환경에서 안전하게 실행할 수 있는 제어 경로를 만든다.

이번 단계의 구현은 stage 실행을 가장하거나 CI의 disposable PostgreSQL 결과를 stage 결과로 대체하지 않는다. 실제 stage endpoint, restricted backend credential, administrative operator credential, exact deployed build와 database package 적용 증거가 확인될 때만 한 번 실행할 수 있다.

## 현재 실행 차단 근거

저장소의 Operations 기준선은 다음 외부 항목을 아직 확보하지 못한 상태로 기록한다.

```text
EXTERNAL_ENDPOINT_READY=NO
EXTERNAL_CREDENTIAL_READY=NO
EXTERNAL_ALLOWLIST_READY=NO
STAGE1_ENABLEMENT=BLOCKED
FINAL_DEPLOYMENT_PLATFORM=UNDECIDED
```

추가로 현재 Search 추천 계보는 `main`에 병합되지 않은 stacked draft PR이다. 따라서 다음 사실도 아직 충족되지 않았다.

- SR-6F-C~H runtime과 canonical v2.8 package가 stage 배포본에 포함됐다는 증거 없음
- manual `workflow_dispatch` 파일이 authoritative default branch에 게시됐다는 증거 없음
- GitHub `stage` environment와 required secrets가 구성됐다는 증거 없음
- 승인할 실제 stage endpoint fingerprint 없음
- exact deployed source SHA 없음

이 상태에서 실행 결과를 생성하거나 PASS로 기록하면 안 된다.

## 구현된 제어 패키지

```text
operations/search-ctr/sr6fh/
├─ stage-execution-contract.env
├─ validate_stage_endpoints.py
├─ sanitize_stage_evidence.py
├─ execute_stage_one_shot.sh
├─ stage-one-shot.init.gradle
├─ java/com/jc/backend/intelligence/search/
│  └─ SearchCtrStageOneShotApplication.java
├─ 01_preflight_stage.sql
├─ 04_collect_stage_evidence.sql
└─ 05_verify_stage_revoked.sql
```

기존 SR-6F-G 절차를 그대로 재사용한다.

```text
operations/search-ctr/sr6fg/01_grant_stage_reliability.sql
operations/search-ctr/sr6fg/02_verify_stage_reliability.sql
operations/search-ctr/sr6fg/03_revoke_stage_reliability.sql
```

## Machine-readable 실행 계약

`stage-execution-contract.env`는 현재 다음과 같이 닫혀 있다.

```text
SR6FH_EXECUTION_STATUS=BLOCKED_EXTERNAL_STAGE_ACCESS
SR6FH_AUTHORIZED_STAGE_ENDPOINT_SHA256=UNASSIGNED
```

실행 준비가 완료됐다는 별도 증거 없이 이 값을 변경하면 안 된다. 재개 시에는 endpoint 문자열을 저장하지 않고 다음 canonical endpoint의 SHA-256만 승인 기록에 넣는다.

```text
postgresql://<lowercase-host>:<port>/<database>
```

admin URL과 backend JDBC URL은 같은 host, port, database를 가리켜야 한다. fingerprint가 다르면 credential을 사용하기 전에 실패한다.

## Manual-only workflow

```text
.github/workflows/sr-search-ctr-stage-one-shot.yml
```

허용 trigger는 `workflow_dispatch` 하나뿐이다.

금지 trigger:

- `pull_request`
- `push`
- `schedule` / cron
- 다른 workflow의 자동 호출

Job은 GitHub `stage` environment를 사용하고 동시 실행을 금지한다. workflow input은 exact source SHA, approval reference, window start와 명시적인 실행 확인 문자열을 요구한다.

이 workflow는 default branch에 승인된 파일이 존재해야 dispatch할 수 있다. 현재 stacked draft 상태에서는 실행 경로가 게시된 것으로 간주하지 않는다.

## Secret boundary

필요한 secret 이름만 계약으로 고정한다. 값은 저장소, 로그, artifact 또는 문서에 기록하지 않는다.

```text
SR6FH_STAGE_ADMIN_DATABASE_URL
SR6FH_STAGE_ADMIN_USERNAME
SR6FH_STAGE_ADMIN_PASSWORD
SR6FH_STAGE_BACKEND_JDBC_URL
SR6FH_STAGE_BACKEND_USERNAME
SR6FH_STAGE_BACKEND_PASSWORD
SR6FH_STAGE_JWT_SECRET
```

admin credential은 일시적인 role grant/revoke와 catalog 검증에만 사용한다. application process는 restricted login `jc_backend`로만 접속한다.

## 실행 순서

```text
1. contract READY_FOR_ONE_SHOT 및 endpoint fingerprint 확인
2. exact checkout SHA와 producer build ID 결속
3. admin/backend endpoint 동일성 및 fingerprint 확인
4. stage DB preflight
5. jc_reliability membership grant
6. routed capability와 direct-table denial 검증
7. 비웹 Spring process에서 ApplicationRunner 한 번 실행
8. identity-free projection/audit evidence 수집
9. membership 즉시 revoke
10. catalog에서 residual membership/direct grant 부재 확인
11. jc_backend의 SET ROLE jc_reliability 실패 확인
12. raw 운영 로그를 redaction한 뒤 evidence artifact 생성
```

## Preflight 조건

`01_preflight_stage.sql`은 write 전에 다음을 모두 검사한다.

- environment, approval reference, window exact match
- producer build ID가 `sr6fg-stage-<40-char-source-sha>`
- provisional eligibility 충족
- PostgreSQL 15 이상이며 recovery/read-only endpoint가 아님
- operator가 grant와 revoke를 모두 수행할 수 있음
- `jc_backend`가 LOGIN, NOINHERIT, 비특권 restricted login
- `jc_reliability`가 NOLOGIN, NOINHERIT, 비특권 role
- projection, manual audit, manual execution function 존재
- 실행 전 membership 부재
- 같은 exact build의 선행 audit 부재
- projection lineage head가 0개 또는 1개

하나라도 실패하면 membership을 부여하지 않는다.

## One-shot process

정상 backend main class나 artifact main contract를 변경하지 않는다. operations-only Java entry point를 reviewed Gradle init script가 별도로 컴파일한다.

```text
WebApplicationType.NONE
→ authoritative Spring context start
→ SearchCtrManualActivationRunner exactly once
→ context close
```

runner는 계속 다음 결과만 정상으로 처리한다.

```text
STORED
DUPLICATE
```

다음 결과에는 자동 retry가 없다.

```text
IDEMPOTENCY_CONFLICT
PREDECESSOR_CONFLICT
```

## Revoke guarantee

실행 shell은 `EXIT`, `INT`, `TERM` 종료 경로를 revoke 절차에 연결한다. grant가 성공한 이후 어떤 후속 단계가 실패하더라도 `03_revoke_stage_reliability.sql`을 호출한다.

revoke가 실패하면 workflow는 별도 보안 실패 코드로 종료하며 성공으로 판정하지 않는다.

정상 종료에서도 다음을 모두 검증한다.

- membership 없음
- direct projection/audit table 권한 없음
- `jc_backend`의 `SET ROLE jc_reliability` 실패

기존 append-only projection과 audit evidence는 삭제하거나 수정하지 않는다.

## Evidence boundary

업로드 가능한 결과는 identity-free 필드로 제한한다.

```text
operationId
writeStatus
windowStart / windowEnd
producerBuildId
projectionId
projectionFingerprint
predecessorProjectionId
projectionStatus
eligibleExposureCount
attributedExposureCount
ctrBasisPoints
sourceMaxReceivedAt
finalityWriteAttempted
endpointFingerprint
```

금지 항목:

- endpoint 원문
- username/password/JWT/secret
- user ID 또는 subject mapping
- session/exposure/click event identity
- raw query
- raw database row

raw logs는 artifact 대상이 아니며 sanitizer를 통과한 safe directory만 업로드한다.

## 현재 수행하지 않은 것

```text
Stage endpoint binding: NOT_PERFORMED
GitHub stage environment configuration: NOT_VERIFIED
Stage canonical DB package deployment: NOT_VERIFIED
Exact backend deployment: NOT_PERFORMED
Database preflight: NOT_PERFORMED
Membership grant: NOT_PERFORMED
Manual ApplicationRunner execution: NOT_PERFORMED
Projection write: NOT_PERFORMED
Evidence row collection: NOT_PERFORMED
Membership revoke drill against stage: NOT_PERFORMED
Merge: NOT_PERFORMED
Production activation: NOT_PERFORMED
Finality writer: NOT_IMPLEMENTED
```

## 실행 재개 조건

SR-6F-H 실제 실행은 다음 증거가 모두 제공된 이후 같은 단계에서 재개한다.

1. SR-6F-C~H를 포함한 approved source SHA가 authoritative branch에 존재
2. 해당 SHA 기반 stage deployment 또는 dedicated one-shot artifact
3. canonical DB v2.8 `01..09` 적용 증거
4. 실제 stage PostgreSQL endpoint와 endpoint owner 승인
5. endpoint fingerprint 승인 commit
6. GitHub `stage` environment 보호 규칙
7. 일시적 admin credential과 restricted `jc_backend` credential
8. execution 이후 즉시 revoke할 operator 책임자
9. evidence artifact 보존 위치

그 전까지 SR-6F-H 판정은 다음과 같다.

```text
IMPLEMENTED_EXECUTION_CONTROL_HOLD_EXTERNAL_STAGE_ACCESS
```
