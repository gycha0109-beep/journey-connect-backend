# SC DP-2 Entry Decisions

## 상태

`APPROVED / DP-2 ENTRY AUTHORIZED AFTER THIS SC DECISION PR MERGE`

## 기준

- official DP-1 Baseline SHA: `9d84f630e87d54f780e332eead0c1f8df6a51d0b`
- DP-1 implementation HEAD: `f4f48b139e49b9cba98f60ab64a18871f204b4de`
- DP-1 merge commit: `bdce7de5ef6be31f8da6a8a349424be8f06a87a1`
- DP-1 result: `DP1_IMPLEMENTATION_COMPLETE_WITH_BLOCKED_FINGERPRINT_ALGORITHM`
- this decision resolves the fingerprint blocker without changing protected P0/P1/P2 authority.

## 1. Fingerprint exact contract

### IDs

- fingerprint contract/wire ID: `platform-event-fingerprint-sha256-v1`
- canonicalization ID: `platform-event-canonical-json-v1`
- digest algorithm: SHA-256
- output encoding: lowercase hexadecimal, exactly 64 ASCII characters

### Canonical fingerprint input

The canonical JSON object contains exactly these keys in deterministic lexical order:

1. `actorRef`
2. `canonicalizationVersion`
3. `causationId`
4. `contractVersion`
5. `entityRef`
6. `eventFamily`
7. `eventType`
8. `occurredAt`
9. `payload`
10. `schemaVersion`
11. `sessionRef`

Rules:

- `occurredAt`: INCLUDED; UTC ISO-8601 `Z` canonical representation.
- `receivedAt`: EXCLUDED; retry/transport timing must not change semantic identity.
- `producerBuildId`: EXCLUDED; deployment/build changes must not change semantic identity.
- `producerVersion`: EXCLUDED from digest bytes because it is already part of the idempotency scope.
- `eventId`: EXCLUDED; server-generated after/new-event decision.
- `requestId`, `correlationId`: EXCLUDED; transport/trace metadata.
- `idempotencyKey`: EXCLUDED from digest bytes; it is the comparison key, not event content.
- nullable `actorRef`, `sessionRef`, `causationId` are emitted as explicit `null` where the envelope contract permits null.
- `payload` follows the approved allowlist and canonical JSON rules; secrets, tokens, raw identity and unrestricted text remain forbidden.

The digest is `SHA-256(UTF-8(canonicalFingerprintInputJson))`.

### Separation

- Existing Recommendation P0 canonical bytes/fingerprint are unchanged.
- No P0 fingerprint row, fixture, hash or algorithm is rewritten or treated as this new fingerprint.

## 2. DB target and SQL sequence

Target remains the existing canonical database package:

`database/journey-connect-db-v2.7`

Allocated sequence:

- SQL `29`: Data canonical event store, append-only attempt/evidence base objects and retention metadata.
- SQL `30`: idempotency binding, atomic new/duplicate/conflict persistence boundary, indexes, constraints and grants.
- SQL `31`: DP-2 PostgreSQL smoke/contract/concurrency verification.
- SQL `32+`: unallocated; SC assignment required.

Existing SQL `01..28` must not be modified.

## 3. Physical writer and roles

### Roles

- `jc_data_event_writer`: sole application writer for approved Data event-store functions/tables.
- `jc_data_event_reader`: read-only access to approved Data views/contracts.
- `jc_data_replay_executor`: may execute approved replay procedures in later stages; no direct canonical event INSERT/UPDATE/DELETE.

### Ownership and mutation rules

- Data owns the new DP-2 objects.
- Canonical event and evidence rows are append-only.
- Application/runtime roles receive no direct `UPDATE` or `DELETE` on canonical event, idempotency binding, conflict or attempt evidence.
- Atomic ingest is exposed through a narrowly granted function or equivalent transactional boundary with fixed `search_path` and explicit input validation.
- Data receives no write grant on Recommendation, Search, Operations or Reliability objects.

## 4. Retention technical baseline

- idempotency binding online dedupe retention: 30 days from first accepted binding.
- ingest attempt, conflict and quarantine technical retention: 90 days.
- canonical platform event default retention class: 365 days.
- tables may record `retention_policy_version`, `retention_class` and `expires_at`.
- automatic purge, physical deletion or crypto-erasure executor remains disabled until Operations/Security/Privacy approval.
- legal hold or approved erasure may override the technical expiry through a separately audited policy; DP-2 does not implement identity mapping or legal workflow.

## 5. DP-2 boundary

Allowed:

- PostgreSQL canonical event store
- append-only ingest attempt/evidence
- fingerprint persistence using the exact contract above
- atomic idempotency new/duplicate/conflict handling
- roles/grants limited to Data objects
- PostgreSQL concurrency and replay-safety tests
- migration/runbook/evidence/handoff

Not allowed:

- Controller, public API or production ingestion activation
- identity mapping repository/join
- Recommendation/Search/Operations/Reliability table write
- projection persistence or source cutover
- P1/P2 authority changes
- production shadow, sampling, cohort, kill switch or Search cutover changes

## 6. Entry decision

```text
DP-1: MAIN INTEGRATED
SC-DP1-009: RESOLVED
DP-2 SQL: 29..31 ASSIGNED
DP-2 ENTRY: AUTHORIZED AFTER THIS SC DECISION PR IS MERGED
```
