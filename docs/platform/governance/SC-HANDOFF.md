# System Coordination Handoff

## 상태

`DP1_MAIN_INTEGRATED / DP2_ENTRY_DECISIONS_APPROVED`

## 기준

- official DP-1 Baseline SHA: `9d84f630e87d54f780e332eead0c1f8df6a51d0b`
- DP-1 implementation HEAD: `f4f48b139e49b9cba98f60ab64a18871f204b4de`
- DP-1 PR: `#6`
- DP-1 merge commit/current authority start: `bdce7de5ef6be31f8da6a8a349424be8f06a87a1`
- DP-1 exact-head CI: Data Contract `29840338516`, Recommendation Core `29840339853`, Backend `29840338075`, SC `29840340842` — PASS
- DP-1 result: `DP1_IMPLEMENTATION_COMPLETE_WITH_BLOCKED_FINGERPRINT_ALGORITHM`
- SC fingerprint decision: `SC-DP1-009 RESOLVED` by `SC-DP2-001`

## 완료

- PR #3, PR #4 and PR #6 merged into `main`
- `jc-data-contracts` is active at `com.jc.data.contract.v1`
- Client command/canonical envelope, taxonomy, identity namespace, version, validation, canonicalization and idempotency boundaries are main authority
- SQL 01..28 and Recommendation/Search/runtime authority remain protected
- new Data fingerprint exact contract approved without reusing or rewriting protected P0 fingerprint
- DP-2 DB target, SQL sequence, physical writer/roles and technical retention baseline assigned

## DP-2 approved decisions

### Fingerprint

- wire ID: `platform-event-fingerprint-sha256-v1`
- algorithm: SHA-256
- encoding: lowercase hexadecimal, 64 characters
- included: `contractVersion`, `schemaVersion`, `canonicalizationVersion`, `eventFamily`, `eventType`, `occurredAt`, `actorRef`, `sessionRef`, `entityRef`, `causationId`, `payload`
- excluded: `eventId`, `receivedAt`, `producerVersion`, `producerBuildId`, `requestId`, `correlationId`, `idempotencyKey`
- exact rules: `SC-DP2-ENTRY-DECISIONS.md`

### DB and SQL

- target: `database/journey-connect-db-v2.7`
- SQL 29: canonical event store/evidence base
- SQL 30: idempotency/atomic ingest/grants
- SQL 31: PostgreSQL smoke/contract/concurrency verification
- SQL 32+: unallocated

### Roles

- writer: `jc_data_event_writer`
- reader: `jc_data_event_reader`
- future replay executor: `jc_data_replay_executor`
- direct canonical UPDATE/DELETE prohibited

### Retention technical baseline

- idempotency binding: 30 days
- attempt/conflict/quarantine: 90 days
- canonical event default class: 365 days
- automatic purge remains disabled until Operations/Security/Privacy approval

## DP-2 entry

```text
DP-1: MAIN INTEGRATED
SC-DP1-009: RESOLVED
DP-2 ENTRY: AUTHORIZED AFTER SC DP-2 DECISION PR MERGE
```

DP-2 may implement PostgreSQL persistence and concurrency only. It must not add a public API, production ingestion activation, identity mapping/join, projection cutover or cross-track writes.

## Remaining unresolved

- identity mapping physical owner/deletion workflow remains unresolved and outside DP-2
- country/legal retention and erasure rules remain outside the technical baseline
- production purge/erasure executor requires Operations/Security/Privacy approval

## Protected state

```text
IP-12.5: HOLD_OPERATIONAL_INPUTS_PENDING
Production shadow: DISABLED
Kill switch: true
Effective sampling: 0 BPS
Actual cohort: empty / 0%
Search cutover: NOT STARTED
Production traffic: NOT APPROVED
Go/No-Go: NO_GO_FOR_TRAFFIC
```
