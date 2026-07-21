# Event Idempotency and Fingerprint V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `event-idempotency-fingerprint-v1` |
| 상태 | `ACTIVE / FINGERPRINT APPROVED / DP-2 PERSISTENCE AUTHORIZED` |
| 소유 | Data Platform |
| persistence/concurrency implementation | `DP-2` |

## 2. 판정

```text
same idempotency key + same fingerprint = DUPLICATE
same idempotency key + different fingerprint = CONFLICT
```

Conflict code는 `IDEMPOTENCY_CONFLICT`다. Conflict를 normal duplicate로 처리하지 않는다.

## 3. Logical scope

현재 logical tenant는 `journey-connect`다.

- authenticated: `(tenant, producerVersion, eventFamily, actorRef, idempotencyKey)`
- approved anonymous: `(tenant, producerVersion, eventFamily, serverSessionRef, idempotencyKey)`
- actor/session이 모두 없으면 canonical event 생성 금지
- session은 server-derived/validated 값만 사용
- retained binding 범위에서 key 재사용 금지
- idempotency binding online retention: 30 days from first accepted binding

## 4. Evaluation ordering

1. command shape validation
2. authorization/actor/server session resolution
3. canonical family/type resolution
4. canonical fingerprint calculation
5. atomic idempotency compare
6. duplicate: existing event ref 반환, new event 없음
7. conflict: reject with `IDEMPOTENCY_CONFLICT`
8. new: canonical event + binding append

Concurrent insert는 DP-2에서 unique constraint 또는 동등한 atomic compare로 처리한다.

## 5. Replay

Replay is not new ingest. It creates a new `replayAttemptRef`, preserves source idempotency binding and never updates source to manufacture a new canonical event.

## 6. Fingerprint contract

### Identity

- wire ID: `platform-event-fingerprint-sha256-v1`
- algorithm: SHA-256
- output encoding: lowercase hexadecimal, exactly 64 ASCII characters
- canonicalization: `platform-event-canonical-json-v1`

### Exact inclusion set

Included canonical keys:

- `contractVersion`
- `schemaVersion`
- `canonicalizationVersion`
- `eventFamily`
- `eventType`
- `occurredAt`
- `actorRef`
- `sessionRef`
- `entityRef`
- `causationId`
- `payload`

Excluded:

- `eventId`
- `receivedAt`
- `producerVersion` because it is part of the idempotency scope
- `producerBuildId`
- `requestId`
- `correlationId`
- `idempotencyKey`

`occurredAt` is included as semantic event time. `receivedAt`, build and trace fields are excluded so transport retry or deployment changes do not alter semantic identity.

Digest:

```text
SHA-256(UTF-8(canonicalFingerprintInputJson))
```

### Protection

- new Data fingerprint is separate from protected P0 fingerprint
- locale/whitespace/Map order independent
- secret/raw identity excluded
- failures use `FINGERPRINT_FAILURE`
- existing P0 canonical bytes/fingerprint are never rewritten or reinterpreted

## 7. Persistence boundary

DP-2 may persist this fingerprint and implement atomic compare. It must not implement public ingestion APIs, identity mapping/join, projection cutover or cross-track table writes.

Full SC decision: `../governance/SC-DP2-ENTRY-DECISIONS.md`.
