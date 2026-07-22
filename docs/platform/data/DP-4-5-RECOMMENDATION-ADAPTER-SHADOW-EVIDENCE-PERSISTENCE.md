# DP-4.5 Recommendation Adapter Shadow Evidence Persistence

## Result

`DP45_IMPLEMENTATION_COMPLETE`

## Authoritative direction

```text
Recommendation P0 source
  -> DP-4 deterministic adapter
  -> Data Platform shadow candidate
  -> append-only compatibility evidence
```

The reverse path into production Recommendation input is prohibited. This implementation does not create Recommendation inputs, Recommendation results, P2 experiment exposure, canonical Data events, workers, schedulers, replay, backfill, cutover or production traffic.

## Baseline and authority

- work-start main: `9a5785448ff300063b7f320c9f1043ef1863741e`;
- DP-4.5 blocker/design PR `#12`: merged;
- SC allocation PR `#13`: merged;
- implementation authority: `GRANTED`;
- SQL `01..34`: protected;
- SQL `35..37`: allocated to DP-4.5;
- SQL `38+`: unallocated;
- DP-4 output fingerprint remains Java-produced SHA-256 lowercase hexadecimal, 64 characters, and is not the P0 or canonical Data fingerprint.

## Implemented database boundary

### SQL 35 — evidence foundation

Implemented append-only evidence:

- adapter run evidence;
- mapped shadow output evidence;
- mapping failure evidence;
- conflict observation evidence;
- bounded duplicate counter used only for aggregate observability.

Run, output, failure and conflict evidence reject `UPDATE` and `DELETE` through the existing Data append-only trigger. All evidence has `adapter_evidence_90d`, `data-retention-policy-v1` and 90-day expiry metadata. No purge or physical-delete executor exists.

### SQL 36 — atomic persistence

`persist_recommendation_adapter_shadow_evidence_v1(...)` is the only writer capability. It validates the exact DP-4 version boundary, reference namespaces, privacy-safe mapped payload, stable failure taxonomy, output fingerprint shape and terminal success-or-failure exclusivity.

Logical identity:

```text
source_event_ref
+ source_fingerprint
+ adapter_id
+ adapter_version
+ target_contract_version
+ mapping_policy_version
```

| Outcome | Implemented behavior |
|---|---|
| `NEW` | create one run and exactly one mapped output or failure in one transaction |
| `DUPLICATE` | return the existing run/evidence references, increment a bounded aggregate counter, create no new output/failure |
| `CONFLICT` | create no new output/failure, preserve existing evidence, append conflict evidence and return `ADAPTER_EVIDENCE_CONFLICT` |

A transaction-scoped advisory lock plus a unique logical-identity constraint protects concurrent callers. Partial run/output/failure writes are impossible because all changes occur in one function transaction.

### SQL 37 — validation

The rollback-only PostgreSQL validation covers:

- mapped success `NEW`, `DUPLICATE`, `CONFLICT`;
- deterministic failure `NEW`, `DUPLICATE`, `CONFLICT`;
- existing evidence reference return;
- no extra output on conflict;
- malformed fingerprint, incompatible class and privacy-unsafe payload denial;
- transaction rollback atomicity;
- append-only denial;
- writer, reader, function-owner and PUBLIC privilege boundaries;
- fixed function `search_path`;
- safe aggregate view;
- retention metadata;
- absence of automatic purge.

A separate multi-session fixture verifies one concurrent `NEW` plus one `DUPLICATE`, one run and one mapped output.

## Fingerprint contract

The database verifies that the supplied DP-4 output fingerprint is lowercase hexadecimal with exactly 64 characters. It does not recalculate it under a different semantic contract and does not reuse the Recommendation P0 or canonical Data event fingerprint.

For failed mapping evidence only, the database derives a separate bounded result-comparison hash from mapping status, stable failure code/class, retryability and failure signature. This hash is used only for persistence idempotency and is not a replacement output fingerprint.

## Privacy and payload

Persisted mapped payload must be:

- the DP-4 mapped payload only;
- a JSON object;
- no larger than 64 KiB;
- accepted by the existing recursive Data forbidden-key validator.

Not persisted:

- raw source payload or canonical source bytes;
- password, token, API key, authorization header or credential;
- full idempotency key;
- unrestricted exception text or stack trace;
- raw numeric identity or unverified identity binding;
- production Recommendation result;
- actual Recommendation exposure result;
- P2 experiment exposure.

## Roles and grants

### Writer

`jc_data_adapter_evidence_writer`

- may execute the approved persistence function;
- has no direct evidence-table write;
- has no canonical event mutation;
- has no Recommendation or P2 table write;
- has no DDL authority.

### Reader

`jc_data_adapter_evidence_reader`

- may select the safe aggregate view only;
- cannot select raw evidence tables;
- cannot insert, update or delete.

### Function owner

`jc_data_adapter_evidence_function_owner`

- `NOLOGIN` and non-superuser;
- no create-role, create-database, replication or bypass-RLS attributes;
- fixed `search_path = pg_catalog, public, pg_temp`;
- PUBLIC execute revoked;
- narrow table privileges needed by the function only.

## Safe aggregate view

`data_recommendation_adapter_safe_metrics_v1` exposes:

- run, success, failure, `NEW`, `DUPLICATE` and `CONFLICT` counts;
- compatibility-class and failure-code counts;
- adapter and target-contract versions;
- oldest run age and latest processed time.

It exposes no actor, user, session, request, payload, source fingerprint, idempotency key, error message, stack trace or token dimension.

## Protected baseline

Unchanged:

- SQL `01..34`;
- Recommendation P0/P1/P2 production source and authority;
- existing P0 fingerprint;
- Data canonical event and idempotency sources;
- DP-3 retry/quarantine sources;
- Search and Intelligence production source;
- production configuration and `/api/v1/explore`;
- production shadow disabled, kill switch enabled, sampling `0 BPS`, cohort empty and Search cutover not started.

## DP-5 gate

DP-4.5 implementation satisfies the technical persistence prerequisite for DP-5 after this PR is merged and the exact-head PostgreSQL 15/18, Data, Recommendation, Backend and SC gates remain successful. DP-5 is not started by this change.
