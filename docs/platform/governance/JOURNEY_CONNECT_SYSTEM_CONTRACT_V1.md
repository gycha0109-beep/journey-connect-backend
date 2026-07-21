# Journey Connect System Contract V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `jc-system-contract-v1` |
| 개정 | `V1.2 / SC DP-1 BASELINE RECONCILIATION` |
| 상태 | `ACTIVE / P2_AND_IP12_5_ALIGNED` |
| canonical DB | `journey-connect-db-v2.7/01..28` |
| 기준일 | 2026-07-21 |

이 문서는 Data, Intelligence, Operations, Reliability와 System Coordination의 공통 식별자, 시간, 버전, source authority, DB sequence와 보호 기준선을 고정한다.

## 2. 현재 authoritative 상태

### Main

- PR #3 merge commit: `f38cf56b34ff23fbd5cb20b9013444a8cb2d29f4`
- PR #3 protected IP-12.5 controls are current main authority.
- decision: `IP-12.5 HOLD_OPERATIONAL_INPUTS_PENDING`
- P0/P1/P2 technical baseline은 보호한다.
- canonical SQL은 `01..28`이다.

PR #3 병합은 production pilot/traffic/Search cutover 승인이 아니다.

보호 상태:

```text
Production shadow: DISABLED
Kill switch: true
Effective sampling: 0 BPS
Actual cohort: empty / 0%
Search cutover: NOT STARTED
Production traffic: NOT APPROVED
Go/No-Go: NO_GO_FOR_TRAFFIC
```

## 3. 트랙 경계

| 영역 | semantic owner | Data 권한 |
|---|---|---|
| canonical raw platform event, ingestion, retry, quarantine, replay, lineage | Data | own |
| recommendation score/rank/profile/run/general exposure | Intelligence | approved read only |
| P2 assignment/experiment exposure/metric/release | Reliability | approved read only; physical P2 path 보호 |
| visibility/moderation/eligibility/audit | Operations | approved read only |
| contract/identity/exposure registry 및 DB sequence | SC | proposal only |
| Search derived projection | Intelligence/Search | direct write 금지 |

타 트랙 table direct `INSERT/UPDATE/DELETE`를 금지한다.

## 4. DB baseline과 SQL 27/28

- canonical baseline: `database/journey-connect-db-v2.7/01..28`
- SQL `25..26`: protected P2 evaluation/release
- SQL `27`: Search document projection + Operations eligibility
- SQL `28`: SQL 27 smoke test
- DP-1은 SQL을 생성·수정하지 않는다.
- DP-2 이후 신규 SQL 번호와 target DB version은 SC가 `28` 이후로 별도 배정한다.

Ownership:

- `search_document_projection_v1`: Search-owned rebuildable derived projection. Canonical source/Data authority가 아니다.
- `search_document_operational_eligibility_v1`: Operations semantic authority. Missing row는 fail-closed다.
- Data는 두 객체에 direct write하지 않는다.

## 5. 식별자

- cross-track entity: `<entity-type>:<source-id>`
- 신규 pseudonymous actor: `subject:<opaque-id>`
- protected legacy P2 subject: `user:<numeric-id>`

```text
subject:<opaque-id> != user:<numeric-id>
```

자동 변환, 문자열 추론, anonymous/other-subject fallback, 실제 join, P2 row/hash rewrite를 금지한다. 연결은 별도 승인된 `IdentityMappingReadPort`, 단일 write owner, purpose binding, access audit, version, invalidation/deletion 정책이 필요하다. Owner는 현재 미결정이다.

## 6. 시간

- Java/Kotlin: `Instant`
- DB: `TIMESTAMPTZ`
- JSON: UTC ISO-8601 `Z`
- 결정론적 계산은 명시적 `referenceTime`을 사용한다.

## 7. Version contract

| 필드 | 의미 | 예 |
|---|---|---|
| `contractVersion` | 교차 트랙 의미 계약 | `platform-event-v1` |
| `schemaVersion` | payload/snapshot 구조 | `user-behavior-event-v1` |
| `canonicalizationVersion` | canonical bytes 규칙 | `platform-event-canonical-json-v1` |
| `producerVersion` | 논리 producer 구현 계약 | `jc-backend-event-producer-v1` |
| `consumerVersion` | consumer compatibility 구현 | `data-event-consumer-v1` |
| `producerBuildId` | 실제 build/commit | `git:<40-hex-sha>` |

서로 같은 의미로 사용하지 않는다. `latest`, `current`, `default`를 영속 version 값으로 사용하지 않는다.

Compatibility results:

```text
COMPATIBLE
COMPATIBLE_WITH_IGNORED_OPTIONAL_FIELDS
INCOMPATIBLE_SCHEMA
INCOMPATIBLE_REQUIRED_ENUM
UNSUPPORTED_CONSUMER_VERSION
MIGRATION_REQUIRED
```

- unknown optional field: 동일 major와 meaning-preserving addition일 때 ignore 가능
- unknown required field/enum: fail closed
- unsupported schema/consumer: consume 금지
- dual-read: reconciliation, observability, rollback가 있을 때 한시 허용
- dual-write: 기본 금지; atomicity/dedupe/rollback/full regression 승인 필요
- cutover: reconciliation, replay/backfill, consumer support, owner/SC approval 필요
- superseded version과 evidence는 보존한다.

## 8. Command와 canonical event

- `ClientEventCommandV1`: untrusted ingress
- `PlatformEventEnvelopeV1`: server-resolved canonical evidence

Client가 최종 결정할 수 없는 값:

- canonical eventId/family/type/fingerprint
- authoritative actor/permission/session binding
- receivedAt/producer identity/version/build

## 9. Canonical JSON

`platform-event-canonical-json-v1`:

- UTF-8
- object key deterministic lexical ordering
- array order preserves contract meaning
- normalized number representation; NaN/Infinity 금지
- timestamps UTC `Z`
- required null/optional omission 규칙을 schema가 명시
- empty array와 absent field 구분
- whitespace/locale/Map insertion order 비의존
- secret, raw identity, token 제외
- transport-only receipt headers, retry counter, network metadata 제외

기존 P0 canonicalization/fingerprint를 재사용하거나 변경하지 않는다.

## 10. Idempotency, fingerprint, evidence

```text
same key + same fingerprint = DUPLICATE
same key + different fingerprint = CONFLICT / IDEMPOTENCY_CONFLICT
```

Scope, TTL, concurrent insert와 persistence ordering은 DP-1/DP-2에서 승인된 계약대로 구현한다.

신규 Data fingerprint의 algorithm, output encoding, exact inclusion set 및 timestamp/build 포함 여부는 `SC DECISION REQUIRED`다. 따라서 DP-1 fingerprint 구현은 해당 decision 전 중단한다.

Raw event, attempt, quarantine, replay, snapshot, lineage, evaluation과 audit evidence는 append-only다. 정정은 superseding record/new version으로 한다.

## 11. Source authority 보호

- P0/P1 behavior: `recommendation_behavior_event`
- general recommendation exposure: `recommendation_exposure_event` + candidates
- P2 experiment exposure/denominator: `recommendation_p2_experiment_exposure`
- P2 fallback: bound `recommendation_run.run_status`
- Search projection: derived, not canonical

`recommendation-profile-input-v1`과 `experiment-outcome-input-v1`은 shadow-only이며 승인 전 runtime authority가 아니다.

## 12. DP-1 reservation과 시작 기준

| 항목 | 값 | 상태 |
|---|---|---|
| module | `jc-data-contracts` | RESERVED / NOT IMPLEMENTED |
| package | `com.jc.data.contract` | RESERVED / NOT IMPLEMENTED |

DP-1 start baseline은 다음 둘을 모두 포함하는 최초 main HEAD다.

1. merged PR #3 protected IP-12.5 controls
2. merged SC baseline reconciliation changes

현재:

```text
DP-1 entry: BLOCKED UNTIL SC PR #4 IS MERGED
```

## 13. 절대 금지

- SQL 01..28 또는 production runtime 변경
- shadow activation, kill-switch 해제, sampling/account hash 생성
- Search cutover 또는 `/api/v1/explore` authority 변경
- P0/P1/P2 metric/exposure/fingerprint/canonical bytes/evidence 변경
- 타 트랙 direct write
- identity mapping 구현/join
- shadow projection authoritative 승격
