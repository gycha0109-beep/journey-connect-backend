# Event Idempotency and Fingerprint V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `event-idempotency-fingerprint-v1` |
| 상태 | `RECOVERED / IDEMPOTENCY ACTIVE / FINGERPRINT SC DECISION REQUIRED` |
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
- physical TTL/retention은 DP-2/privacy 승인 사항

## 4. Evaluation ordering

1. command shape validation
2. authorization/actor/server session resolution
3. canonical family/type resolution
4. canonical fingerprint calculation
5. atomic idempotency compare
6. duplicate: existing event ref 반환, new event 없음
7. conflict: reject with `IDEMPOTENCY_CONFLICT`
8. new: canonical event + binding append

Concurrent insert는 DP-2에서 unique constraint 또는 동등한 atomic compare로 처리한다. DP-1은 types/validator/classification만 구현한다.

## 5. Replay

Replay is not new ingest. It creates a new `replayAttemptRef`, preserves source idempotency binding and never updates source to manufacture a new canonical event.

## 6. Fingerprint fixed boundary

- new Data fingerprint is separate from protected P0 fingerprint
- derived from deterministic versioned canonical bytes
- locale/whitespace/Map order independent
- secret/raw identity excluded
- failures use `FINGERPRINT_FAILURE`
- existing P0 canonical bytes/fingerprint are never rewritten

## 7. SC DECISION REQUIRED

The recovered approved materials do not fix:

- algorithm
- output encoding
- fingerprint version wire ID
- exact inclusion set
- inclusion of `occurredAt`, `receivedAt`, `producerBuildId`

DP-1 must stop before fingerprint implementation. It may define an interface and validation boundary only. P0 SHA/fingerprint or test expectations must not be reused to guess this contract.
