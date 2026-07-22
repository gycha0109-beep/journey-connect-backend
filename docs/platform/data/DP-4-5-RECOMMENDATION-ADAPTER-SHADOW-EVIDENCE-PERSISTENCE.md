# DP-4.5 Recommendation Adapter Shadow Evidence Persistence

## Result

`DP45_IMPLEMENTATION_BLOCKED_BY_SQL_ASSIGNMENT`

DP-4.5 cannot implement PostgreSQL persistence because the authoritative SC documents still reserve SQL `01..34` and mark SQL `35+` as unallocated. No migration number, adapter evidence role, or persistence authority has been assigned.

## Authoritative direction

```text
Recommendation P0 source
  -> DP-4 deterministic adapter
  -> Data Platform shadow candidate
  -> proposed append-only compatibility evidence
```

The reverse path into production Recommendation input is prohibited. This stage does not create canonical Data events, Recommendation events, P2 exposure, recommendation results, projections, workers, schedulers, replay or production traffic.

## Preflight

- authoritative main: `c1649c3647e8b640bd95853fdcb645d1571f54c3`;
- DP-4 PR `#11`: merged;
- DP-4 result: `DP4_IMPLEMENTATION_COMPLETE_WITH_SQL_ASSIGNMENT_PENDING`;
- SQL `01..34`: protected;
- SQL `35+`: `SC ASSIGNMENT REQUIRED`;
- adapter evidence writer/reader roles: not registered;
- DP-4 output fingerprint: Java-produced, SHA-256 lowercase hexadecimal, 64 characters, not the P0 or canonical Data fingerprint;
- production worker absent, scheduler disabled, production shadow disabled, kill switch enabled, sampling `0 BPS`, cohort empty, Search cutover not started.

## Why implementation stops

Persistence cannot be simulated with Java DTOs, fixtures, in-memory stores, an existing Recommendation table, an existing Data table, or an unassigned SQL filename. Atomic `NEW/DUPLICATE/CONFLICT`, append-only enforcement and least-privilege grants require an SC-owned migration sequence and explicit role allocation.

## Proposed DB object design

The following names are design candidates only. They are not implemented or registered.

| Proposed object | Purpose | Required invariants |
|---|---|---|
| `data_recommendation_adapter_run_v1` | one bounded adapter execution observation | source/adapter/target versions, started/completed times, terminal run status, 90-day retention metadata |
| `data_recommendation_adapter_output_v1` | successful mapped shadow output evidence | semantic compatibility only, mapped fields, DP-4 output fingerprint, no raw source payload |
| `data_recommendation_adapter_failure_v1` | deterministic mapping rejection/failure evidence | DP-4 stable failure code, retryability, bounded failure signature, no unrestricted error text |
| `data_recommendation_adapter_observation_v1` | duplicate/conflict observations | append-only disposition, existing evidence reference, observed fingerprint, bounded conflict code |
| `persist_recommendation_adapter_evidence_v1(...)` | atomic persistence boundary | one transaction, advisory transaction lock plus unique constraint, validates Java output fingerprint, returns existing evidence reference for duplicate |
| `prevent_recommendation_adapter_evidence_mutation_v1()` | append-only trigger | rejects UPDATE and DELETE with stable SQLSTATE |
| `data_recommendation_adapter_summary_v1` | privacy-safe reader view | aggregate counts only; no actor/session/request/payload dimensions |

## Proposed SQL sequence requiring SC assignment

No number below is allocated by this branch.

| Candidate sequence | Proposed responsibility |
|---|---|
| first assigned SQL | run/output/failure/observation tables, constraints, indexes, retention metadata, append-only triggers |
| second assigned SQL | atomic persistence function, logical identity lock, `NEW/DUPLICATE/CONFLICT`, owner, role and grant boundary, safe aggregate view |
| third assigned SQL | PostgreSQL 15/18 smoke, duplicate/conflict/concurrency, privilege, append-only, retention and protected-authority validation |

SC may assign different numbers or split responsibilities differently. Implementation must follow the final registry.

## Persistence contract proposal

### Logical identity

```text
source_event_ref
+ source_fingerprint
+ adapter_id
+ adapter_version
+ target_contract_version
+ mapping_policy_version
```

### Outcomes

| Outcome | Required behavior |
|---|---|
| `NEW` | insert run plus exactly one output or failure in one transaction |
| `DUPLICATE` | return existing evidence reference; append bounded duplicate observation; no new output/failure and no mutation |
| `CONFLICT` | append bounded conflict observation with `ADAPTER_EVIDENCE_CONFLICT`; no new output/failure and no mutation |

Partial run/output/failure persistence is prohibited. The function must serialize the logical identity with a transaction-scoped advisory lock and enforce a matching unique constraint.

## Proposed table constraints

- adapter ID/version, mapping policy and target contract are exact versioned wire values;
- source and output fingerprints match `^[0-9a-f]{64}$`;
- output fingerprint is supplied by and semantically validated against the DP-4 contract, never recalculated under a different DB contract;
- compatibility remains `semantic_compatible`; unsupported/rejected outcomes cannot enter the output table;
- mapping status remains shadow-only and cannot become production-ready or cutover-ready;
- mapped payload is a bounded object, deterministic canonical JSON, DP-4 allowlisted and privacy-safe;
- raw source canonical bytes, raw source payload, full idempotency key, numeric user identity, token, credential, unrestricted message and stack trace are prohibited;
- general Recommendation exposure lineage remains distinct from P2 experiment exposure;
- foreign keys use `ON DELETE RESTRICT`; no cascade deletion;
- `expires_at = created_at + interval '90 days'` under a versioned retention class;
- no purge function, deletion executor or scheduled cleanup.

## Proposed roles requiring SC assignment

Suggested capability names, not approved roles:

```text
jc_data_adapter_evidence_writer
jc_data_adapter_evidence_reader
```

Writer proposal:

- EXECUTE only on the approved atomic persistence function;
- no direct table INSERT/UPDATE/DELETE;
- no canonical Data event mutation;
- no Recommendation table write;
- no retry/quarantine procedure access unless separately granted.

Reader proposal:

- SELECT only on the aggregate safe view;
- no raw table SELECT;
- no function ownership or write authority.

Privileged function proposal:

- narrow NOLOGIN owner, consistent with `jc_security_owner` convention if SC approves;
- `SECURITY DEFINER` only where required;
- fixed `search_path = pg_catalog, public, pg_temp`;
- PUBLIC execute revoked and explicit role grant;
- caller cannot select object names, role names, schema names or SQL fragments.

## Safe view proposal

Permitted dimensions:

- adapter ID/version;
- target contract version;
- compatibility class;
- run/mapping disposition;
- stable failure code;
- time bucket;
- counts, oldest uncompleted age and bounded latency aggregates.

Prohibited dimensions:

- actor/user/session/request/exposure IDs;
- source event ID where individual lookup is possible;
- raw payload, fingerprint canonical bytes, idempotency key;
- raw or unrestricted failure text, token or stack trace.

## Retention

Run, output, failure, duplicate and conflict evidence use the proposed technical class `adapter_evidence_90d`, policy `data-retention-policy-v1`, and 90-day expiry metadata. Automatic purge and physical deletion remain disabled. Final class naming requires SC approval.

## Required SC decisions

1. assign the exact SQL range after `34`;
2. register exact migration responsibilities and filenames;
3. approve or replace writer/reader capability roles;
4. approve function owner and membership/grant routing;
5. approve physical object and retention-class names;
6. decide whether duplicate observations are mandatory or optional;
7. approve deterministic adapter evidence ID/reference formats;
8. confirm whether persistence is a prerequisite for DP-5.

## Protected baseline

This blocker branch must contain no changes to SQL `01..34`, Java/Kotlin production source, `jc-data-contracts`, Recommendation P0/P1/P2 source, Search/Intelligence source, production configuration or `/api/v1/explore`. DP-4 output remains fixture/CI evidence only until a later authorized persistence implementation.
