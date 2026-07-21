# DP-2 PostgreSQL Event Store & Idempotency

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `DP-2` |
| 계약 ID | `dp-2-postgresql-event-store-idempotency-v1` |
| 상태 | `IMPLEMENTATION COMPLETE` |
| 소유 트랙 | Data Platform |
| 기준 브랜치 | `main` |
| DP-1 merge commit | `bdce7de5ef6be31f8da6a8a349424be8f06a87a1` |
| SC DP-2 decision merge commit | `c3f791c6c6eaa12b2aba3a1dbe686cb0b3d3cc80` |
| 작업 시작 HEAD | `c3f791c6c6eaa12b2aba3a1dbe686cb0b3d3cc80` |
| canonical DB target | `database/journey-connect-db-v2.7` |
| SQL allocation | `29..31` |
| production runtime/API 영향 | 없음 |

## 2. 목적

DP-1의 순수 Java event contract를 기반으로 신규 Data canonical event, idempotency binding, ingest attempt, duplicate observation, conflict observation을 PostgreSQL append-only evidence로 저장한다.

```text
validated platform event + approved fingerprint bytes
        ↓ narrow SECURITY DEFINER function
logical idempotency scope serialization
        ↓
NEW       → canonical event + binding + attempt, one transaction
DUPLICATE → existing event ref + observation + attempt, no new event
CONFLICT  → existing event ref + conflict evidence + attempt, no new event
```

Controller, API, Spring Service, JPA, production wiring, retry/quarantine/replay processor는 포함하지 않는다.

## 3. Preflight 결과

- 기본 브랜치: `main`
- 작업 시작 HEAD: `c3f791c...`
- PR #6: merged, merge commit `bdce7de...`
- PR #7: merged, merge commit `c3f791c...`
- `SC-DP1-009`: resolved
- DP-2 entry: authorized
- SQL `29..31`: DP-2 배정
- SQL `01..28`: 보호 기준선
- 역할 관례: NOLOGIN capability role, unsafe existing role fail-closed, `jc_security_owner` ownership, fixed `search_path`, PUBLIC revoke, explicit grant
- PostgreSQL 검증: 15/18 matrix

## 4. Fingerprint completion

### 4.1 계약

| 항목 | 값 |
|---|---|
| wire ID | `platform-event-fingerprint-sha256-v1` |
| canonicalization | `platform-event-canonical-json-v1` |
| algorithm | SHA-256 |
| encoding | lowercase hexadecimal |
| length | 64 |

포함 필드:

```text
contractVersion
schemaVersion
canonicalizationVersion
eventFamily
eventType
occurredAt
actorRef
sessionRef
entityRef
causationId
payload
```

제외 필드:

```text
eventId
receivedAt
producerVersion
producerBuildId
requestId
correlationId
idempotencyKey
```

### 4.2 Java 구현

- `PlatformEventFingerprintCanonicalizerV1`
  - envelope validation 후 승인된 11개 필드만 선택
  - exact top-level key set 검증
  - payload 내부 secret/token/raw identity denylist 유지
- `Sha256EventFingerprintBoundaryV1`
  - Java platform SHA-256
  - lowercase hex 64
  - canonicalization version과 size 검증
- `Sha256DigestV1`
  - dependency-free digest helper
- `UnresolvedEventFingerprintBoundaryV1`
  - DP-1 compatibility boundary로 유지
  - runtime default binding 없음

Golden fingerprint:

```text
f6ca2be46c46150e7c26d82e5c22974b1fd53dc54b500a0fccba29e23450f09d
```

기존 Recommendation P0 canonical bytes와 fingerprint code/fixture는 변경하거나 재사용하지 않는다.

## 5. SQL 29 — canonical event and evidence base

### 5.1 Objects

- `data_platform_event_v1`
- `data_event_ingest_attempt_v1`
- `data_event_duplicate_observation_v1`
- `data_event_conflict_observation_v1`
- `prevent_data_event_append_only_mutation_v1()`
- `data_event_type_valid_v1(varchar, varchar)`
- `data_event_payload_contains_forbidden_key_v1(jsonb)`
- `data_event_canonical_json_v1(jsonb)`

### 5.2 Canonical event invariants

- unique `event_id`
- exact contract/canonicalization/fingerprint IDs
- fingerprint lowercase hex 64
- digest와 stored canonical bytes 일치
- deterministic canonical JSON byte equality
- exact 11-field inclusion set
- `TIMESTAMPTZ`; `received_at >= occurred_at`
- canonical actor는 `subject:<opaque-id>`만 허용
- actor 또는 approved server session 중 최소 하나 필요
- malformed reference/idempotency key 거부
- recursive forbidden payload key 거부
- retention class `canonical_event_365d`
- UPDATE/DELETE append-only trigger 거부

`fingerprint_canonical_bytes`는 raw transport payload가 아니라 SC가 승인한 fingerprint input object의 exact UTF-8 canonical bytes다.

## 6. SQL 30 — atomic idempotency and grants

### 6.1 Logical scope

Authenticated:

```text
(tenant, producer_version, event_family, actor_ref, idempotency_key)
```

Approved anonymous:

```text
(tenant, producer_version, event_family, server_session_ref, idempotency_key)
```

`data_event_idempotency_binding_v1`은 generated `scope_kind`, `scope_ref`와 unique constraint로 physical scope를 고정한다.

### 6.2 Atomic ingest function

`ingest_data_platform_event_v1(...)` 수행 순서:

1. structural/version/reference validation
2. digest validation
3. canonical JSON byte validation
4. exact inclusion set validation
5. event input과 fingerprint object semantic equality validation
6. logical scope advisory transaction lock
7. existing binding lookup
8. NEW/DUPLICATE/CONFLICT evidence append

Application pre-check 후 INSERT 방식은 사용하지 않는다. Advisory transaction lock과 unique constraint를 함께 사용한다.

### 6.3 Dispositions

| disposition | event 생성 | binding 생성 | 반환 |
|---|---:|---:|---|
| `NEW` | 1 | 1 | 신규 canonical event ref |
| `DUPLICATE` | 0 | 0 | 기존 canonical event ref |
| `CONFLICT` | 0 | 0 | 기존 ref + `IDEMPOTENCY_CONFLICT` |

### 6.4 Role/grant

| Role | 허용 | 금지 |
|---|---|---|
| `jc_data_event_writer` | atomic ingest function EXECUTE | direct table mutation, other-track write, DDL |
| `jc_data_event_reader` | approved reader view SELECT | raw table write, function ownership |
| `jc_data_replay_executor` | role reservation | source mutation, replay execution in DP-2 |
| `PUBLIC` | 없음 | Data event read/write/function execute |

Privileged function owner는 narrow NOLOGIN `jc_security_owner`다. Function은 fixed `search_path = pg_catalog, public, pg_temp`를 사용한다.

Reader view는 idempotency key와 raw fingerprint bytes를 노출하지 않는다.

## 7. SQL 31 — smoke and contract validation

SQL 31은 transaction 내부 fixture를 생성하고 마지막에 `ROLLBACK`한다.

검증 항목:

- objects/function signature
- NEW
- exact DUPLICATE와 existing event ref
- CONFLICT와 stable error code
- duplicate/conflict에서 extra event/binding 없음
- invalid digest 거부
- noncanonical bytes 거부
- excluded fingerprint field 거부
- legacy/raw actor namespace 거부
- append-only UPDATE/DELETE denial
- writer function-only boundary
- reader read-only boundary
- replay non-mutation
- PUBLIC denial
- Recommendation/Search direct write denial
- retention metadata
- purge executor absence

## 8. Concurrency

`verification/dp2/run_dp2_concurrency.sh`는 별도 PostgreSQL sessions를 병렬 실행한다.

- same scope/key + same fingerprint: 정확히 `NEW + DUPLICATE`
- same scope/key + different fingerprint: 정확히 `NEW + CONFLICT`
- canonical events: 2
- bindings: 2
- duplicate observations: 1
- conflict observations: 1
- deadlock/transaction abort: 없음

## 9. Retention

| Evidence | class | expires |
|---|---|---|
| canonical event | `canonical_event_365d` | `received_at + 365 days` |
| idempotency binding | `idempotency_binding_30d` | `created_at + 30 days` |
| attempts/duplicate/conflict | `ingest_evidence_90d` | `created_at + 90 days` |

자동 purge, physical delete, retention executor, legal hold/erasure workflow는 구현하지 않는다.

## 10. CI 및 실행 전략

### 10.1 Data Contract CI

- Java 21
- `-Xlint:all -Werror`
- DP-1 existing contract runner
- DP-2 fingerprint runner
- Recommendation Java Core `check`

### 10.2 Data PostgreSQL CI

Fresh database에서:

1. state-producing canonical SQL을 SQL 30까지 적용
2. rollback-only protected smoke scripts는 state bootstrap에서 제외
3. SQL 31 DP-2 contract smoke 실행
4. concurrency script 실행

기존 SQL `01..28` smoke와 P0/P1/P2 regression은 Recommendation P0 Database CI PostgreSQL 15/18 matrix가 별도로 검증한다. 보호 SQL 파일 자체는 변경하지 않는다.

### 10.3 완료된 implementation-head runs

| Gate | 결과 | Run |
|---|---|---|
| Data Contract CI | PASS | `29854424891` |
| Data PostgreSQL 15/18 | PASS | `29854424964` |
| Recommendation P0 DB 15/18 | PASS | `29854424898` |
| Backend/IP-12.5 | PASS | `29854424935` |
| SC Baseline Reconciliation | PASS | `29854424897` |

## 11. 자체 리뷰와 보완

1. DP-1 pre-approval guard가 승인 SHA-256 구현도 차단했다.
   - 승인 implementation과 unresolved boundary 검사를 분리했다.
2. generic payload denylist가 fingerprint top-level `actorRef`까지 차단했다.
   - 정확한 11개 top-level key 전용 canonicalization 경계를 추가했다.
3. 최초 fixture가 `post_view`에 허용되지 않은 `position`을 포함했다.
   - `surface + viewEpisodeRef`로 교체하고 golden hash를 재생성했다.
4. digest만 검증하면 whitespace/key-order가 다른 bytes도 저장될 수 있었다.
   - PostgreSQL canonical JSON renderer와 exact byte equality를 강제했다.
5. `PUBLIC` privilege test의 role-name 사용을 제거했다.
   - information schema/catalog ACL 검증으로 교체했다.
6. SQL 28은 rollback-only smoke라 state bootstrap 후 SQL29가 실패했다.
   - SQL28 비변경 상태에서 state bootstrap과 protected smoke regression을 분리했다.
7. IP-12 test가 canonical SQL inventory를 28개로 고정했다.
   - test-only로 31개와 정확한 SQL29/30/31 이름을 검증한다.
8. SC allowlist는 exact SQL29..31과 위 test file 하나만 허용한다.

## 12. 보호 상태

변경 없음:

- SQL `01..28`
- Recommendation P0/P1/P2 production code와 authority
- P0 canonical bytes/fingerprint
- Search runtime/projection ownership
- Intelligence contracts
- production Java/Kotlin
- `/api/v1/explore`
- production shadow `DISABLED`
- kill switch `ENABLED`
- sampling `0 BPS`
- cohort `EMPTY`
- Search cutover `NOT STARTED`

## 13. 비책임

- application DB login/membership routing
- HTTP ingestion/API
- Spring Service/JPA Repository
- retry/quarantine processor
- replay procedure와 executor grant
- retention deletion executor
- identity mapping/join
- projection persistence와 source cutover

## 14. 잔여 리스크와 DP-3 진입 조건

잔여 리스크는 runtime integration과 후속 processor 계약으로 제한된다.

DP-3 진입 전:

1. DP-2 PR merge
2. merged main exact-head PostgreSQL 15/18 확인
3. retry/quarantine state machine 승인
4. processor role, retry budget, terminal quarantine contract 승인
5. Operations/Security observability와 privacy review

최종 판정:

```text
DP2_IMPLEMENTATION_COMPLETE
```
