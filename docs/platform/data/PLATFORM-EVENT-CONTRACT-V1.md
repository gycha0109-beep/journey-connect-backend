# Platform Event Contract V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `platform-event-v1` |
| 상태 | `RECOVERED / ACTIVE CONTRACT / IMPLEMENTATION_NOT_STARTED` |
| 소유 | Data Platform |

## 2. Command와 canonical event

### `ClientEventCommandV1`

Untrusted ingress intent다. 요청 action, entity candidate, client-observed time, bounded context와 idempotency key를 포함할 수 있지만 canonical evidence가 아니다.

Client가 최종 결정할 수 없는 값:

- canonical `eventId`
- authoritative actor/permission
- canonical family/type
- server session binding
- canonical fingerprint
- `receivedAt`
- producer identity/version/build
- source authority

### `PlatformEventEnvelopeV1`

```json
{
  "contractVersion": "platform-event-v1",
  "schemaVersion": "user-behavior-event-v1",
  "canonicalizationVersion": "platform-event-canonical-json-v1",
  "producerVersion": "jc-backend-event-producer-v1",
  "producerBuildId": "git:<40-hex-sha>",
  "eventId": "event:<stable-id>",
  "eventFamily": "user_behavior",
  "eventType": "post_view",
  "occurredAt": "2026-07-21T00:00:00Z",
  "receivedAt": "2026-07-21T00:00:00.100Z",
  "actorRef": "subject:<opaque-id>",
  "sessionRef": "session:<server-derived-id>",
  "entityRef": "post:123",
  "requestId": "request:<id>",
  "correlationId": "correlation:<id>",
  "causationId": null,
  "idempotencyKey": "<bounded-key>",
  "payload": {}
}
```

Fingerprint field/algorithm is not implemented until the SC decision is approved.

## 3. Version fields

| 필드 | 의미 | 예 |
|---|---|---|
| `contractVersion` | cross-track semantic contract | `platform-event-v1` |
| `schemaVersion` | payload structure | `user-behavior-event-v1` |
| `canonicalizationVersion` | canonical bytes rules | `platform-event-canonical-json-v1` |
| `producerVersion` | logical producer implementation contract | `jc-backend-event-producer-v1` |
| `consumerVersion` | supported consumer compatibility implementation; read/attempt evidence에 기록 | `data-event-consumer-v1` |
| `producerBuildId` | concrete build/commit | `git:<40-hex-sha>` |

`latest/current/default`를 영속 version으로 금지한다.

## 4. Compatibility

```text
COMPATIBLE
COMPATIBLE_WITH_IGNORED_OPTIONAL_FIELDS
INCOMPATIBLE_SCHEMA
INCOMPATIBLE_REQUIRED_ENUM
UNSUPPORTED_CONSUMER_VERSION
MIGRATION_REQUIRED
```

- same major에서 meaning-preserving optional field 추가만 허용
- unknown optional field는 explicit optional일 때 ignore 가능
- unknown required field/schema는 fail closed
- unknown required enum은 fail closed
- unsupported schema/consumer는 consume 금지
- dual-read는 bounded reconciliation/observability/rollback 조건에서만 허용
- dual-write는 기본 금지; atomicity/dedupe/rollback/full regression/SC 승인이 필요
- cutover는 reconciliation, replay/backfill, consumer support, owner+SC approval가 필요
- superseded version/evidence는 보존한다.

## 5. Canonical JSON

`platform-event-canonical-json-v1`:

- UTF-8
- object key deterministic lexical ordering
- array order preserves contract meaning
- normalized finite number representation; locale-independent
- UTC ISO-8601 `Z` timestamps
- required nullable field is explicit `null`; absent optional field is omitted
- `[]`/`{}` differ from absence
- whitespace and Map insertion order independent
- secret/token/credential/raw account identity/unrestricted text excluded
- HTTP/auth/retry/trace-sampling transport metadata excluded

기존 P0 canonicalization/fingerprint를 재사용하거나 변경하지 않는다.

## 6. Validation errors

```text
INVALID_COMMAND
INVALID_EVENT_FAMILY
INVALID_EVENT_TYPE
INVALID_SCHEMA_VERSION
INVALID_CANONICALIZATION_VERSION
INVALID_PRODUCER_VERSION
UNSUPPORTED_CONSUMER_VERSION
INVALID_ACTOR_REF
IDENTITY_NAMESPACE_MISMATCH
INVALID_ENTITY_REF
INVALID_SESSION_REF
INVALID_TIMESTAMP
INVALID_PAYLOAD
FORBIDDEN_PAYLOAD_FIELD
IDEMPOTENCY_CONFLICT
CANONICALIZATION_FAILURE
FINGERPRINT_FAILURE
```

Stable `UPPER_SNAKE_CASE` values only.

## 7. Consumer matrix

| Producer contract | Producer schema | Consumer contract | Consumer version | Compatibility | Migration | Cutover allowed |
|---|---|---|---|---|---|---|
| `platform-event-v1` | `user-behavior-event-v1` | Data validator | `data-event-consumer-v1` | `COMPATIBLE` | none | no DP-1 runtime cutover |
| `recommendation-behavior-event-v1` | protected P0 | Data adapter | future DP-4 | `MIGRATION_REQUIRED` | adapter + reconciliation | no |
| `recommendation-profile-input-v1` | shadow | current P1 | current direct source | `MIGRATION_REQUIRED` | new source/schema + full regression | no |
| `experiment-outcome-input-v1` | shadow | current P2 evaluation | current dataset | `MIGRATION_REQUIRED` | exact reconciliation + full regression | no |

## 8. Identity

`subject:<opaque-id> != user:<numeric-id>`. Automatic conversion, fallback, actual join, P2 row/hash rewrite are prohibited. DP-1 implements no mapping repository.
