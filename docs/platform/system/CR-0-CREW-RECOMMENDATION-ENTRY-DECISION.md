# CR-0 Crew Recommendation Entry Decision

## 판정

```text
Decision date: 2026-08-07 KST
Stage: CR-0
Project owner progression approval: RECEIVED
Predecessor search track: SR-6F-I VERIFIED / external stage HOLD
Crew recommendation contract implementation: VERIFIED
Verified implementation head: e5104253718dd7605209bef807eacedae538e572
Crew runtime activation: NOT_AUTHORIZED
Crew DB change: NOT_AUTHORIZED_IN_CR0
CR-1 DB sequence: UNASSIGNED
Crew exposure registry proposal: crew_recommendation_exposure_v1
Crew exposure registry status: PROPOSED_NOT_REGISTERED
Merge/deploy: NOT_AUTHORIZED_BY_THIS_DECISION
Overall: VERIFIED_CR0_CONTRACT_HOLD_CR1_DB_ALLOCATION
```

## 진행 해석

사용자의 다음 단계 진행 승인은 탐색 추천 repository-internal 검증 이후 Crew recommendation 계약 트랙으로 이동하는 것을 승인한다.

SR-6F-I의 actual stage execution이 external platform decision으로 HOLD인 상태를 Crew 기능 구현으로 우회하거나 Search 운영 검증이 완료된 것으로 재해석하지 않는다.

CR-0은 Crew recommendation의 eligibility, feature coverage, scoring component allocation, fallback, cross-track authority boundary를 고정한다. 현재 Crew API와 DB는 변경하지 않는다.

## Current Crew authority

현재 authoritative Crew list는 다음 경로다.

```text
GET /api/v1/crews
→ CrewController.list
→ CrewService.list
→ CrewRepository.findByRecruitingTrueOrderByCreatedAtDescIdDesc
```

CR-0에서 이 경로의 response/order semantics는 `crew-service-list-v1` fallback으로 보호한다.

## Contract allocation

```text
crew-recommendation-contract-v1
owner=Intelligence/Crew recommendation
status=CR0_LOCAL_CONTRACT_VERIFIED

crew-ranking-policy-v1
owner=Intelligence/Crew recommendation
status=CR0_COMPONENTS_FIXED_IMPLEMENTATION_PENDING

crew_recommendation_exposure_v1
owner=System Coordination registry decision required
status=PROPOSED_NOT_REGISTERED
```

`crew_recommendation_exposure_v1`은 CR-0 문서에서 제안하는 이름일 뿐 registry reservation이나 persistence authority가 아니다.

## System Contract compatibility

CR-0은 기존 System Contract의 다음 규칙을 유지한다.

- `entityRef=crew:<id>` 형식 사용
- 기존 API `regionCode`와 canonical `regionSlug`를 분리
- 신규 runtime은 versioned policy/contract 사용
- 과거 run/exposure/evidence 수정 금지
- 다른 트랙 direct write 금지
- Operations visibility 의미를 Intelligence가 재정의하지 않음
- Reliability experiment exposure와 metric denominator를 Crew list exposure와 혼합하지 않음
- DB version/SQL sequence는 System Coordination이 배정

## Verification

Verified implementation head:

```text
e5104253718dd7605209bef807eacedae538e572
```

CI:

```text
CR Crew Recommendation: 31141170509 — SUCCESS
Recommendation P0 Database CI: 31141170544 — SUCCESS
Backend PR CI: 31141170548 — SUCCESS
```

Results:

```text
CR-0 scope lock: SUCCESS
CR-0 focused: 2 suites / 11 tests / failures 0 / errors 0 / skipped 0
Protected recommendation contracts: SUCCESS
Full backend: 112 suites / 401 tests / failures 0 / errors 0 / skipped 0
PostgreSQL 15 canonical integration: SUCCESS
PostgreSQL 18 canonical integration: SUCCESS
Backend IP-12.5 protected readiness: SUCCESS
```

Evidence digests:

```text
Focused: sha256:c9ad570b006c2802a9722d8c947953085435c49ab0a4f7deca4a48875579565e
Full backend: sha256:4a2b3eb2d6a6f9229ea10ddfda532ba9081fbbfbaa8f343cb5bab98049861585
```

## Database decision

CR-0에서는 DB를 변경하지 않는다.

과거 구현 계획에 기록된 `V16__crew_recommendation_features.sql`은 현재 repository 기준에 재사용하지 않는다.

현재 merged main의 canonical v2.7 계보에는 Admin hardening `53/54`가 존재하고, stacked Search 계보에는 별도 `journey-connect-db-v2.8` package가 존재한다. 따라서 CR-1은 어느 계보의 번호도 추정해서 사용하지 않는다.

CR-1 진입 시 다음을 다시 확인한다.

```text
Authoritative merged DB baseline: RECHECK_REQUIRED
Current canonical package sequence: RECHECK_REQUIRED
Search stacked DB package coexistence: RECHECK_REQUIRED
CR-1 DB version: UNASSIGNED
CR-1 DB sequence: UNASSIGNED
```

Crew↔Tag association은 logical design으로만 승인하며 physical table name, FK target, migration number, role/grant는 CR-1에서 별도 검증한다.

## Operations boundary

현재 Crew baseline에 approved Operations visibility read port가 연결됐다고 가정하지 않는다.

CR-0 contract의 `VisibilityState.NOT_INTEGRATED`는 current behavior preservation state다. 향후 approved Operations decision이 제공되면 `INELIGIBLE`은 hard filter가 된다.

Intelligence가 자체 moderation rule을 만들거나 admin state를 직접 write하지 않는다.

## Reliability / exposure boundary

Crew recommendation list exposure는 다음과 동일하지 않다.

```text
recommendation_general_exposure_v1
recommendation_behavior_impression_v1
recommendation_p2_experiment_exposure_v1
search_exposure_v1
```

따라서 기존 exposure source를 재사용하지 않는다.

CR-4 전에 필요한 결정:

1. Crew exposure registry ID 승인
2. authoritative persistence/write owner
3. dedupe key
4. viewer/session/run binding
5. event attribution semantics
6. privacy/logging boundary
7. Reliability가 사용할 metric definition이 존재한다면 별도 version 승인

## CR-1 entry gate

CR-1은 다음 조건에서만 시작한다.

```text
CR-0 contract tests: PASS
CR-0 full backend regression: PASS
Current Crew API regression: PASS
Current authoritative DB baseline: RECONFIRMED
CR-1 DB version/sequence: ASSIGNED
Crew tag authority: CONFIRMED
No Search/P1/P2 ownership conflict: CONFIRMED
```

현재 앞의 세 검증 조건은 충족했다. DB baseline 재확인과 sequence allocation은 CR-1 시작 시 수행한다.

DB sequence가 아직 미배정이면 CR-1 DDL을 임의 번호로 만들지 않는다.

## 수행하지 않는 것

- Search SR-6F-H/I actual stage execution
- Search PR merge
- Crew API 변경
- Crew DTO tag 추가
- Crew DB migration
- Crew ranking runtime
- Crew exposure write
- behavior producer
- cloud provisioning
- deployment
- production activation
