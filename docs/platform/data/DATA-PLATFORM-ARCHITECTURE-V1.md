# Data Platform Architecture V1

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `data-platform-architecture-v1` |
| 상태 | `RECOVERED / ACTIVE DESIGN / IMPLEMENTATION_NOT_STARTED` |
| 소유 | Data Platform |
| baseline | `journey-connect-db-v2.7/01..28` |

## 논리 흐름

```text
Client / Application
→ ClientEventCommandV1 validation
→ authentication, actor/session/authority resolution
→ PlatformEventEnvelopeV1 canonical builder
→ future Data event store [DP-2]
   ├─ ingestion attempts
   ├─ quarantine/retry/replay
   ├─ versioned projections
   └─ data quality/lineage
→ immutable dataset snapshots
   ├─ recommendation input [shadow]
   ├─ search analytics input
   ├─ experiment outcome input [shadow]
   └─ approved operations read model
```

DP-1은 contract/validation only이며 store/runtime을 구현하지 않는다.

## Module boundary

| 단계 | 논리 module | 책임 |
|---|---|---|
| DP-1 | reserved `jc-data-contracts` / `com.jc.data.contract` | dependency-free types, validators, compatibility, fixtures |
| DP-2 | future backend Data module | PostgreSQL store/idempotency |
| DP-3 | future processing module | attempt/quarantine/replay |
| DP-4 | future adapter | P0 source read-only adapter |
| DP-5/6 | future projection/quality | snapshots, reconciliation, lineage |

## Ownership/dependency

- Data writes only Data-owned future event/attempt/projection/snapshot objects.
- Intelligence owns recommendation and Search calculation/source evidence.
- Operations owns visibility/eligibility/audit decisions.
- Reliability owns experiment/metric/release semantics.
- SC owns registries/DB sequence/breaking-change decisions.
- dependency crosses stable ports, versioned events/projections/snapshots only.
- cross-track JPA entity/repository/direct write is prohibited.

## Processing and failure isolation

- command validation is synchronous and fail-closed.
- accepted canonical event persistence precedes asynchronous projection.
- transient projection failure creates a new attempt/retry record.
- permanent/integrity/privacy failure quarantines without mutating source.
- poison event must not block unrelated partitions.
- replay creates new evidence and cannot change source authority.

## Determinism and ordering

- canonical bytes and hashes are version-bound.
- event ordering is guaranteed only inside explicitly declared partition/order keys.
- datasets bind source set, versions, referenceTime, ordering and content hash.
- same source set + same versions + same referenceTime must reproduce the same derived snapshot.

## Current physical compatibility

| Object | Owner | Data treatment |
|---|---|---|
| `recommendation_behavior_event` | Intelligence | read-only adapter source |
| general recommendation exposure | Intelligence | approved read contract |
| P2 experiment exposure | Reliability semantic/current protected physical path | exact authority; read only |
| `search_document_projection_v1` | Intelligence/Search | derived; no direct write |
| `search_document_operational_eligibility_v1` | Operations | semantic authority; no direct write |

## Observability

Privacy-safe signals include event/attempt/projection IDs, schema/producer/consumer/build versions, status/error code, counts, duration, checkpoint/watermark, snapshot/hash refs. Raw identity, token, canonical payload and unrestricted text are not logged.

## Deployment topology

Current default remains modular monolith + PostgreSQL + scheduler/DB queue + versioned projections. Kafka, warehouse or microservice split is not a prerequisite and requires measured need plus contract-preserving migration.
