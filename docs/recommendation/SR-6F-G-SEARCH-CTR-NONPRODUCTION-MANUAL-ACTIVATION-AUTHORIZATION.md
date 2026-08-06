# SR-6F-G Search CTR Non-production Manual Activation Authorization

## 상태

```text
Stage: SR-6F-G
Metric: search-click-through-rate-v1
Policy: search-ctr-activation-finality-v1
Decision: APPROVED_BY_PROJECT_OWNER
Implementation: VERIFIED
Verified implementation head: 9ba2db4a11bc0415652e3e7b1c20577a300615fe
Authorized runtime mode: NONPRODUCTION_MANUAL
Authorized environment: stage
Authorized login role: jc_backend
Authorized window start: 2026-08-06T08:00:00Z
Authorized window end: 2026-08-06T09:00:00Z
Authorized approval reference: approval:sr6fg-stage-20260806t0800z
Authorized producer build prefix: sr6fg-stage-
Runner default: OFF
Kill switch default: ON
Reliability startup requirement default: false
Membership grant: NOT_PERFORMED
Manual execution: NOT_PERFORMED
Finality write: DISABLED
Merge/deploy: NOT_PERFORMED
Overall: VERIFIED_BOUNDED_STAGE_AUTHORIZATION_HOLD_EXECUTION
```

## 결정

SR-6F-F에서 검증된 non-production manual foundation에 대해 최초 runtime mode를 `NONPRODUCTION_MANUAL`로 승인한다.

이번 승인은 일반적인 stage writer 활성화가 아니다. 다음 다섯 값이 모두 정확히 일치하는 한 번의 실행 후보만 승인한다.

```text
environment = stage
login role = jc_backend
window = [2026-08-06T08:00:00Z, 2026-08-06T09:00:00Z)
approval ref = approval:sr6fg-stage-20260806t0800z
producer build prefix = sr6fg-stage-
```

`local`, `dev`, `test`, 다른 stage window, 다른 approval reference, prefix가 다른 build는 승인 범위 밖이다.

## 코드 승인 경계

`SearchCtrActivationPolicy`는 다음 값을 고정한다.

```text
AUTHORIZED_RUNTIME_MODE = NONPRODUCTION_MANUAL
AUTHORIZED_MANUAL_ENVIRONMENT = stage
AUTHORIZED_MANUAL_LOGIN_ROLE = jc_backend
AUTHORIZED_MANUAL_WINDOW_START = 2026-08-06T08:00:00Z
AUTHORIZED_MANUAL_APPROVAL_REF = approval:sr6fg-stage-20260806t0800z
AUTHORIZED_MANUAL_PRODUCER_BUILD_PREFIX = sr6fg-stage-
```

`SearchCtrManualActivationGate`는 기존 foundation 조건에 더해 위 값을 정확히 검증한다.

현재도 다음은 유지된다.

- runner bean default absent: `enabled=false`
- kill switch default active: `kill-switch=true`
- Reliability startup verification default disabled
- active Spring profile은 `stage`여야 함
- `prod`, `production` profile 동시 활성화 금지
- 정확히 하나의 UTC 정렬 1시간 window
- provisional eligibility: `windowEnd + 35 minutes`
- HTTP endpoint 없음
- scheduler/cron 없음
- loop/retry 없음
- `SETTLED` writer 없음
- finality write authorization 없음

## Stage login membership 절차

운영 절차는 다음 파일로 고정한다.

```text
operations/search-ctr/sr6fg/01_grant_stage_reliability.sql
operations/search-ctr/sr6fg/02_verify_stage_reliability.sql
operations/search-ctr/sr6fg/03_revoke_stage_reliability.sql
```

세 스크립트는 공통으로 다음 입력을 요구한다.

```text
sr6fg_environment=stage
sr6fg_approval_ref=approval:sr6fg-stage-20260806t0800z
```

### Grant 전제조건

- 실제 접속 대상이 승인된 stage PostgreSQL인지 운영자가 외부 배포 증거로 확인
- `jc_backend`가 LOGIN, NOINHERIT, NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOREPLICATION, NOBYPASSRLS
- `jc_reliability`가 NOLOGIN, NOINHERIT이며 elevated attribute 없음
- grant 시작 전 `jc_backend`가 `jc_reliability` member가 아님

Grant script는 조건이 어긋나면 transaction을 실패시키고 membership을 남기지 않는다.

### Capability verify

- `jc_backend` → `jc_reliability` membership 존재
- `SET LOCAL ROLE jc_reliability` 성공
- manual writer 함수 `EXECUTE` 존재
- projection 및 manual audit table 직접 권한 없음

### Revoke

수동 실행 프로세스 종료 후 같은 approval reference로 revoke script를 실행한다. revoke 이후 membership이 남아 있으면 transaction을 실패시킨다.

## 승인된 one-shot runtime 입력

실행 단계에서는 다음 값이 모두 필요하다.

```text
SPRING_PROFILES_ACTIVE=stage
DB_ROLE_ROUTING_REQUIRE_RELIABILITY=true
SEARCH_CTR_MANUAL_ENABLED=true
SEARCH_CTR_MANUAL_KILL_SWITCH=false
SEARCH_CTR_MANUAL_ENVIRONMENT=stage
SEARCH_CTR_MANUAL_WINDOW_START=2026-08-06T08:00:00Z
SEARCH_CTR_MANUAL_PRODUCER_BUILD_ID=sr6fg-stage-<exact-deployed-build>
SEARCH_CTR_MANUAL_APPROVAL_REF=approval:sr6fg-stage-20260806t0800z
```

`SEARCH_CTR_MANUAL_PRODUCER_BUILD_ID`는 실제 배포 SHA 또는 불변 build identifier와 결속해야 한다. prefix만 일치하고 exact deployed build 증거가 없으면 실행하지 않는다.

## 실행 전 evidence checklist

- SR-6F-G exact head와 세 CI 성공
- stage 배포 SHA와 producer build ID 일치
- stage DB endpoint 증거
- stage active profile 증거
- grant script 성공 로그
- capability verify script 성공 로그
- runner 기본 OFF 및 kill switch ON 상태 증거
- 승인 window가 provisional eligible 상태임을 UTC로 확인
- projection current-head 사전 조회 결과 또는 empty head 증거
- 동일 approval reference의 선행 실행이 없음을 확인

하나라도 누락되면 실행하지 않는다.

## 실행 후 evidence checklist

- 프로세스가 one-shot으로 정확히 한 번 종료
- writer status가 `STORED` 또는 `DUPLICATE`
- operation ID
- projection ID와 fingerprint
- expected predecessor
- source watermark
- append-only manual-run audit row
- finality write attempted = false
- runner disable 증거
- kill switch ON 복구 증거
- membership revoke 성공 로그
- revoke 후 `SET LOCAL ROLE jc_reliability` 실패 증거

`IDEMPOTENCY_CONFLICT` 또는 `PREDECESSOR_CONFLICT`이면 자동 재시도하지 않고 실행을 중단한다.

## Disable drill

실행 승인 전 다음 순서를 dry-run 또는 isolated stage clone에서 검증한다.

```text
1. SEARCH_CTR_MANUAL_KILL_SWITCH=true
2. SEARCH_CTR_MANUAL_ENABLED=false
3. one-shot process 종료 확인
4. 03_revoke_stage_reliability.sql 실행
5. DB_ROLE_ROUTING_REQUIRE_RELIABILITY=false
6. jc_backend membership 부재 확인
7. 기존 projection/audit evidence 보존 확인
```

rollback은 신규 write capability 제거다. 기존 append-only projection과 audit를 UPDATE, DELETE, TRUNCATE하지 않는다.

## 검증 증거

```text
Verified implementation head:
9ba2db4a11bc0415652e3e7b1c20577a300615fe

SR Search Recommendation:
31092362305 — SUCCESS

Recommendation P0 Database CI:
31092363499 — SUCCESS

Backend PR CI:
31092362603 — SUCCESS

Focused:
27 suites / 99 tests / failures 0 / errors 0 / skipped 0

Protected recommendation contracts:
P1 17 scenarios / P2 23 scenarios — SUCCESS

Full backend:
106 suites / 373 tests / failures 0 / errors 0 / skipped 0

PostgreSQL 15 canonical integration: SUCCESS
PostgreSQL 18 canonical integration: SUCCESS
IP-12.5 protected readiness: SUCCESS

Focused artifact:
sha256:e1ea873ab87492206952f774d239514a8c28b25c2bdc6bd9b9d9b33b8e296b94

Full regression artifact:
sha256:f28067c104051c1c6dacf4d3584f980f01ec442ade010d37a4117a44cf708768
```

## 이번 단계에서 수행하지 않는 것

- stage DB 접속
- `jc_reliability` membership grant
- application deployment
- one-shot process 실행
- projection write
- endpoint 또는 scheduler 추가
- dashboard 또는 alert 추가
- production activation
- `SETTLED` finality writer
- merge

## 다음 단계

```text
SR-6F-H: CONTROLLED_STAGE_ONE_SHOT_EXECUTION_AND_EVIDENCE
```

SR-6F-H는 stage 접근 정보와 exact deployed build가 확인된 경우에만 시작한다. 접근 증거가 없으면 authorization 상태를 유지하고 실행하지 않는다.
