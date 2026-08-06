# SR-6F-E Search CTR Activation and Finality Governance

## 상태

```text
Stage: SR-6F-E
Metric: search-click-through-rate-v1
Policy: search-ctr-activation-finality-v1
Governance contract: VERIFIED
Verified implementation head: 20022a39d740cee8052e2b5c113d99a759e343d6
Runtime mode: DISABLED
Finality write: DISABLED
Endpoint/scheduler/dashboard: NOT_IMPLEMENTED
Merge/deploy/production activation: NOT_PERFORMED
Overall: VERIFIED_GOVERNANCE_HOLD_ACTIVATION_AND_FINALITY
```

## 목적

SR-6F-D에서 검증된 append-only projection writer를 언제, 어떤 실행 경계로 사용할 수 있는지와 `PROVISIONAL` projection을 언제 finality 검토 대상으로 볼 수 있는지를 고정한다.

이 단계는 writer를 실제로 실행하지 않는다. scheduler, HTTP endpoint, restricted backend login의 `jc_reliability` membership, `SETTLED` writer를 추가하지 않는다.

## 근거가 되는 현재 계약

```text
Search CTR attribution window: 30 minutes
Search behavior maximum event age: 30 days
Search behavior maximum future skew: 5 minutes
Projection snapshot status: PROVISIONAL only
Projection writer: append-only single SECURITY DEFINER writer
```

Search 행동 API는 발생 시각이 현재보다 최대 5분 미래이거나 최대 30일 과거인 이벤트를 허용한다. 따라서 `windowEnd + 30분`만으로는 finality를 선언할 수 없다.

## V1 window 계약

```text
window type: fixed UTC hour
interval: [windowStart, windowEnd)
duration: exactly 1 hour
alignment: HH:00:00Z
segment: global aggregate only
```

지역, query class, device, user, subject, session, raw query segment는 이 정책에서 허용하지 않는다.

## Provisional eligibility

```text
provisionalEligibleAt = windowEnd
                      + 30 minute attribution window
                      + 5 minute clock-skew allowance
                      = windowEnd + 35 minutes
```

`observedAt >= provisionalEligibleAt`부터 해당 window의 provisional 평가를 검토할 수 있다.

현재 runtime mode가 `DISABLED`이므로 eligibility 도달은 실행 승인을 의미하지 않는다.

## Settlement eligibility

```text
settlementThreshold = windowEnd
                    + 30 minute attribution window
                    + 30 day behavior replay age
                    + 5 minute clock-skew allowance
                    = windowEnd + 30 days 35 minutes
```

`observedAt > settlementThreshold`인 경우에만 settlement 검토 후보가 된다. threshold와 같은 시각은 허용하지 않는다.

Settlement eligibility 역시 finality write 승인이 아니다.

## Runtime activation mode

정의된 mode:

```text
DISABLED
NONPRODUCTION_MANUAL
NONPRODUCTION_SCHEDULED
PRODUCTION_SCHEDULED
```

현재 승인 mode:

```text
DISABLED
```

다음에 승인 가능한 최초 mode는 `NONPRODUCTION_MANUAL`뿐이다. scheduled mode와 production mode로 직접 건너뛰지 않는다.

## NONPRODUCTION_MANUAL 선행조건

다음 항목이 모두 구현·검증·승인돼야 한다.

1. restricted backend login에 대한 `jc_reliability` membership을 non-production에서만 부여한다.
2. startup capability verification이 명시적 설정으로 `jc_reliability`를 요구할 수 있어야 한다.
3. projection table 직접 SELECT 없이 current window head를 반환하는 identity-free read boundary가 있어야 한다.
4. 실행 요청과 결과를 identity 없이 남기는 append-only operational run audit가 있어야 한다.
5. default-off kill switch와 environment allowlist가 있어야 한다.
6. 한 번의 실행은 정확히 하나의 UTC 정렬 1시간 window만 처리해야 한다.
7. 애플리케이션은 metric count, CTR, canonical payload, fingerprint를 입력하지 않아야 한다.
8. `STORED`, `DUPLICATE`, `IDEMPOTENCY_CONFLICT`, `PREDECESSOR_CONFLICT`를 서로 다른 운영 결과로 처리해야 한다.

HTTP endpoint는 manual activation 경로로 허용하지 않는다. 최초 실행기는 non-production one-shot runner로 제한한다.

## Idempotency와 predecessor

Manual runner의 idempotency key는 window, policy version, producer build를 결속해야 한다.

```text
search-ctr:{windowStart}:{windowEnd}:{policyVersion}:{producerBuildId}
```

변경된 payload를 저장하려면 identity-free head read boundary에서 읽은 current projection ID를 expected predecessor로 전달한다.

`PREDECESSOR_CONFLICT` 발생 시 자동 추측이나 blind retry를 하지 않는다. current head를 다시 읽고 새로운 실행 판단을 내려야 한다.

## Finality 결정

### `SUPERSEDED`

별도 상태로 추가하지 않는다. 기존 `predecessor_projection_id` replacement lineage가 이전 projection이 current head가 아님을 표현한다.

### `SETTLED`

향후 별도 승인된 append-only finality writer가 새로운 snapshot을 생성하는 방식만 허용한다. 기존 `PROVISIONAL` row를 UPDATE하지 않는다.

`SETTLED` 후보는 다음을 모두 만족해야 한다.

1. `observedAt > settlementThreshold`
2. current head가 `PROVISIONAL`
3. settlement threshold 이후 최소 1시간 간격으로 실행한 두 평가의 canonical fingerprint가 동일
4. 두 평가에서 source watermark가 역행하지 않음
5. numerator가 denominator를 초과하지 않음
6. identity invalidation 또는 aggregate fail-closed 오류가 없음
7. behavior replay age, attribution window, future-skew 계약이 policy version과 동일

현재 `SearchCtrActivationPolicy.isFinalityWriteAuthorized()`는 `false`다.

## Late arrival 정책

Settlement 이전의 late CLICK은 새 provisional replacement snapshot으로 교정할 수 있다.

Settlement 이후 기존 계약으로 유효한 late correction이 관찰되면 다음과 같이 처리한다.

```text
existing snapshot mutation: forbidden
automatic re-open: forbidden
new SETTLED replacement: forbidden
operational response: pause + policy violation evidence
required action: metric/policy version review
```

Search 행동의 maximum event age가 변경되면 `search-ctr-activation-finality-v1` settlement 계산은 즉시 무효가 되며 activation을 중단해야 한다.

## 자동 중단 기준

다음 중 하나라도 발생하면 writer 호출을 중단한다.

- identity mapping invalidation으로 aggregate가 fail-closed
- numerator > denominator
- source watermark regression
- 예상하지 않은 writer status
- idempotency conflict
- predecessor conflict가 head 재조회 없이 반복됨
- 3회 연속 infrastructure failure
- policy/runtime configuration mismatch

## Rollback

Rollback은 append-only evidence 삭제가 아니다.

```text
runner/scheduler disable
jc_reliability runtime membership 제거
startup capability requirement 비활성화
new writes 중단
existing projection/audit rows 보존
```

UPDATE, DELETE, TRUNCATE 또는 projection history 재작성은 허용하지 않는다.

## 코드 계약

- `SearchBehaviorContract`
- `SearchCtrActivationPolicy`
- `SearchCtrActivationPolicy.Window`
- `SearchCtrActivationPolicy.RuntimeMode`
- `SearchCtrActivationPolicyTest`
- `SearchCtrActivationGovernanceContractTest`

Search 행동의 30일 replay age와 5분 future skew를 서비스 내부 중복 상수가 아니라 공유 계약으로 추출했다. API 의미는 변경하지 않는다.

## CI 검증

Verified implementation head:

```text
20022a39d740cee8052e2b5c113d99a759e343d6
```

### SR Search Recommendation — run `31073477449`

```text
focused Search/PostgreSQL: SUCCESS
protected recommendation contracts: SUCCESS
full backend regression: SUCCESS
focused: 22 suites / 84 tests / failures 0 / errors 0 / skipped 0
full: 101 suites / 358 tests / failures 0 / errors 0 / skipped 0
```

Evidence:

```text
focused artifact: 8956598614
focused digest: sha256:ef0b1aeb624fc14309cbda3298e5bce6958e168d2e62d5b0e10c8566a8467f76
full artifact: 8956688017
full digest: sha256:94232145215371fd729f41f8d2ff50abf6da69009963e5ab15a069497adeb187
```

### Recommendation P0 Database CI — run `31073477384`

```text
PostgreSQL 15: SUCCESS
PostgreSQL 18: SUCCESS
Java/SQL integrity: SUCCESS
canonical PostgreSQL integration: SUCCESS
PG15 artifact: 8956602439
PG15 digest: sha256:48941f0d983d8b463ca789c6e176c6cd6fa0e9768bff6695d83a30d645f03676
PG18 artifact: 8956607489
PG18 digest: sha256:ab6e863c913664cfb2e27dae885bc44d2e69200586f2923a6642502c9c12239f
```

### Backend PR CI — run `31073478653`

```text
IP-12.5 full protected readiness: SUCCESS
artifact: 8956632443
digest: sha256:99f0cabb7a4798bac2d5f6d73950bc3dd5fc0b6d24996e9ab8a4d7645233c448
```

## 다음 단계

```text
SR-6F-F: NONPRODUCTION_MANUAL_ACTIVATION_FOUNDATION
```

SR-6F-F에서 허용 가능한 범위:

- identity-free projection head read boundary
- append-only operational run audit
- explicit reliability startup capability flag
- default-off non-production one-shot runner
- kill switch와 negative tests

계속 금지:

- scheduler/cron
- HTTP evaluation endpoint
- production activation
- dashboard/alert
- `SETTLED` writer
- merge/deploy
