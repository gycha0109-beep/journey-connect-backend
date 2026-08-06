# SR-6F-F Search CTR Non-production Manual Activation Foundation

## 상태

```text
Stage: SR-6F-F
Metric: search-click-through-rate-v1
Policy: search-ctr-activation-finality-v1
Implementation: VERIFIED
Verified implementation head: 349aef3f489cddb9190856dac734be41a3086afc
Runtime mode: DISABLED
Runner default: OFF
Reliability startup requirement default: false
Finality write: DISABLED
HTTP/scheduler/dashboard: NOT_IMPLEMENTED
Merge/deploy/production activation: NOT_PERFORMED
Overall: VERIFIED_FOUNDATION_HOLD_ACTIVATION_AND_FINALITY
```

## 목적

SR-6F-E에서 승인한 최초 activation 후보인 `NONPRODUCTION_MANUAL`을 실제 활성화하지 않은 상태로 안전하게 준비한다.

이 단계는 다음 기반만 추가한다.

- identity-free projection current-head read boundary
- append-only operational manual-run audit
- explicit `jc_reliability` startup capability verification flag
- default-off one-shot application runner
- non-production environment allowlist
- kill switch
- writer result별 운영 분기
- negative contract와 PostgreSQL integration

현재 `SearchCtrActivationPolicy.AUTHORIZED_RUNTIME_MODE`는 계속 `DISABLED`다. 따라서 설정에서 runner를 켜더라도 정책 승인이 없으면 DB 호출 전에 fail-closed한다.

## DB 계약

Canonical package:

```text
database/journey-connect-db-v2.8/
├─ 08_search_ctr_nonprod_manual_activation_foundation.sql
└─ 09_search_ctr_nonprod_manual_activation_smoke_test.sql
```

Testcontainers bootstrap:

```text
08 ↔ 61_search_ctr_nonprod_manual_activation_foundation.sql
09 ↔ 62_search_ctr_nonprod_manual_activation_smoke_test.sql
```

Flyway auto-discovery migration은 추가하지 않는다.

### Identity-free head boundary

```text
read_search_ctr_projection_head_v1
```

입력:

```text
windowStart
windowEnd
requester = reliability-search-ctr-manual
```

출력은 current projection의 다음 aggregate evidence로 제한한다.

```text
projectionId
projectionFingerprint
predecessorProjectionId
metricId
metricVersion
windowStart
windowEnd
status
computedAt
sourceMaxReceivedAt
```

user, subject, session, exposure, click, raw query 식별자는 반환하지 않는다. `jc_reliability`에는 projection table 직접 `SELECT` 권한을 부여하지 않는다.

### Atomic manual execution boundary

```text
execute_search_ctr_manual_v1
```

이 함수는 하나의 transaction에서 다음을 수행한다.

```text
입력·환경·window·eligibility 검증
→ current head 조회
→ expected predecessor 결속
→ 기존 single writer 호출
→ append-only operational audit 저장
→ identity-free 결과 반환
```

허용 environment:

```text
local
dev
test
stage
```

`prod`, `production` 및 그 밖의 값은 거절한다.

한 번의 호출은 정확히 하나의 UTC 정렬 1시간 window만 처리한다. `observedAt >= windowEnd + 35 minutes`를 만족하지 않으면 거절한다.

### Operational audit

```text
search_ctr_manual_run_audit_v1
```

- append-only
- physical owner: `jc_security_owner`
- runtime mode: `NONPRODUCTION_MANUAL` only
- environment allowlist 고정
- 요청 window, policy, build, idempotency, expected predecessor, writer status 기록
- projection ID/fingerprint/watermark는 writer 반환값만 기록
- identity-bearing material 미저장
- `finality_write_attempted = false` 고정
- `jc_reliability` direct SELECT/INSERT/UPDATE/DELETE 없음

## Application 계약

### Explicit Reliability startup capability

```text
app.database.role-routing.require-reliability=false
```

기본값은 `false`다. 승인된 non-production login에 `jc_reliability` membership을 부여한 경우에만 `true`로 전환할 수 있다.

`true`일 때 `DatabaseRoleCapabilityVerifier`가 startup에서 `SET LOCAL ROLE jc_reliability` 가능 여부를 검증한다.

### Default-off one-shot runner

```text
app.intelligence.search-ctr.manual.enabled=false
app.intelligence.search-ctr.manual.kill-switch=true
```

필수 실행 입력:

```text
environment
window-start
producer-build-id
approval-ref
```

runner bean은 `enabled=true`일 때만 생성된다. 생성돼도 다음이 모두 충족돼야 한 번 실행한다.

1. kill switch off
2. policy authorized mode = `NONPRODUCTION_MANUAL`
3. explicit Reliability startup verification enabled
4. configured environment가 allowlist에 존재
5. 동일 이름의 active Spring profile 존재
6. active profile에 `prod` 또는 `production` 없음
7. approval ref와 producer build 형식 유효
8. UTC 정렬 1시간 window
9. provisional eligibility 도달

현재 2번이 충족되지 않으므로 runtime write는 승인되지 않는다.

## Idempotency

runner idempotency key는 다음 값을 모두 결속한다.

```text
windowStart
windowEnd
search-ctr-activation-finality-v1
producerBuildId
```

application은 denominator, numerator, CTR, canonical payload, fingerprint, projection ID 또는 expected predecessor를 직접 계산하지 않는다. expected predecessor는 DB current-head boundary에서 결정한다.

## Writer status 운영 분기

| status | 처리 |
|---|---|
| `STORED` | 성공 종료 |
| `DUPLICATE` | 성공 종료, 추가 write 없음 |
| `IDEMPOTENCY_CONFLICT` | 즉시 중단, 자동 재시도 금지 |
| `PREDECESSOR_CONFLICT` | 즉시 중단, blind retry 금지 |

one-shot runner에는 loop, retry, scheduler가 없다.

## Rollback

```text
SEARCH_CTR_MANUAL_ENABLED=false
SEARCH_CTR_MANUAL_KILL_SWITCH=true
DB_ROLE_ROUTING_REQUIRE_RELIABILITY=false
restricted login의 jc_reliability membership 제거
```

기존 projection과 operational audit는 보존한다. UPDATE, DELETE, TRUNCATE로 evidence를 되돌리지 않는다.

## 구현 파일

- `SearchCtrManualActivationPort`
- `JdbcSearchCtrManualActivationStore`
- `SearchCtrManualActivationProperties`
- `SearchCtrManualActivationGate`
- `SearchCtrManualActivationRunner`
- `SearchCtrManualActivationConfiguration`
- `DatabaseRoleCapabilityVerifier`
- `SearchCtrManualActivationGateTest`
- `SearchCtrManualActivationRunnerTest`
- `SearchCtrManualActivationStoreIntegrationTest`
- `SearchCtrManualActivationSqlContractTest`

## 검증 결과

| 검증 | 결과 |
|---|---|
| Search focused PostgreSQL | SUCCESS — 26 suites / 96 tests / failures 0 / errors 0 / skipped 0 |
| Protected recommendation contracts | SUCCESS — P1 17 scenarios / P2 23 scenarios |
| Full backend regression | SUCCESS — 105 suites / 370 tests / failures 0 / errors 0 / skipped 0 |
| PostgreSQL 15 canonical integration | SUCCESS |
| PostgreSQL 18 canonical integration | SUCCESS |
| Backend IP-12.5 protected readiness | SUCCESS |

Exact implementation-head CI:

```text
SR Search Recommendation: 31089405826
Recommendation P0 Database CI: 31089402997
Backend PR CI: 31089403711
```

Evidence digests:

```text
Focused: sha256:a5dbd11c951973fc3900be925bf929fe8da6dd99eb4856d95c3bf991081a4fe3
Full regression: sha256:c9d80cabab98e1ba2f9e545da90bfe8cb15f1215b09ea4a3e8f0f7a2d7317e85
```

## 계속 금지

- public/internal HTTP evaluation endpoint
- scheduler/cron
- production activation
- dashboard/alert
- `SETTLED` finality writer
- existing projection UPDATE
- finality state 변경
- Flyway migration
- merge/deploy

## 다음 단계 후보

```text
SR-6F-G: NONPRODUCTION_MANUAL_ACTIVATION_AUTHORIZATION
```

SR-6F-G는 foundation 검증 후에도 자동 시작하지 않는다. 별도 운영 승인으로 다음을 고정해야 한다.

- exact non-production environment
- restricted login membership grant/revoke 절차
- one-shot window와 operator approval reference
- 실행 전/후 evidence checklist
- disable drill
- runtime mode를 `NONPRODUCTION_MANUAL`로 바꾸는 승인 commit
