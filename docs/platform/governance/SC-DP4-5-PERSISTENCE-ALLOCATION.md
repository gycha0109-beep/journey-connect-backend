# SC DP-4.5 Persistence Allocation

## Status

- Decision: `APPROVED`
- Implementation authority: `GRANTED`
- Baseline main: `1eb981fa2ab33e6b3870c6c1b76e547eeae48980`
- DP-4.5 persistence is a required prerequisite for DP-5.

## System outcome

DP-4 Recommendation P0 adapter shadow results may be persisted as append-only evidence. This approval does not authorize production Recommendation writes, a worker, scheduler, replay, backfill, cutover, or production traffic.

## SQL allocation

- `35_data_recommendation_adapter_shadow_evidence.sql`
  - adapter run evidence
  - mapped-output evidence
  - mapping-failure evidence
  - duplicate/conflict observation evidence
  - append-only enforcement, constraints, indexes, retention metadata
- `36_data_recommendation_adapter_shadow_persistence.sql`
  - atomic `NEW / DUPLICATE / CONFLICT` persistence
  - existing evidence reference return on duplicate
  - conflict rejection and append-only conflict observation
  - safe aggregate reader view
  - roles and grants
- `37_data_recommendation_adapter_shadow_validation.sql`
  - PostgreSQL 15/18 smoke, atomicity, duplicate, conflict, append-only, role/grant, retention and protected-regression validation

SQL `01..34` remains protected. SQL `38+` remains unallocated.

## Roles and ownership

- Writer role: `jc_data_adapter_evidence_writer`
  - execute approved persistence function only
  - no direct table update/delete
  - no Recommendation table write
  - no canonical event mutation
- Reader role: `jc_data_adapter_evidence_reader`
  - select approved safe aggregate views only
  - no raw payload or direct table write
- Function owner: `jc_data_adapter_evidence_function_owner`
  - `NOLOGIN`
  - fixed `search_path`
  - PUBLIC execution revoked
  - explicit least-privilege grants only

## Persistence contract

Logical identity consists of:

- source event reference
- source fingerprint
- adapter ID and version
- target contract version
- mapping policy version

Outcomes:

- same identity and same output fingerprint: `DUPLICATE`, return existing evidence reference, create no new mapped output
- same identity and different output fingerprint: `CONFLICT`, create no new mapped output, append conflict observation with `ADAPTER_EVIDENCE_CONFLICT`
- previously unseen identity: `NEW`, create run and success-or-failure evidence atomically

Duplicate observations are not mandatory for every duplicate request. Aggregate duplicate counters or an optional bounded observation may be used, but mapped output duplication is forbidden.

## Retention and privacy

- run, output, failure and conflict evidence: 90-day retention metadata
- no automatic purge or physical delete
- mapped canonical payload only, size-bounded and privacy-validated
- no tokens, secrets, unrestricted errors, raw identity, Recommendation results, or P2 exposure data

## Protected boundaries

- authoritative direction remains `Recommendation P0 source -> Data Platform shadow candidate`
- shadow evidence is not a production Recommendation input
- no production worker or scheduler
- production shadow disabled
- kill switch enabled
- sampling `0 BPS`
- cohort empty
- replay and quarantine release unauthorized
- Search cutover not started

## DP-5 gate

DP-5 may begin only after:

1. SQL `35..37` are implemented and merged;
2. PostgreSQL 15 and 18 validations pass;
3. append-only and least-privilege checks pass;
4. existing Recommendation, Data, Backend and SC regression checks pass.
