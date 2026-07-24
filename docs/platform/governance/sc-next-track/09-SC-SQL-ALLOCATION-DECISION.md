# SC SQL Allocation Decision

## Scope

Decide whether RCA-0 needs canonical DB objects or SQL sequence allocation.

## Current Baseline

- canonical SQL `01..52` is implemented, validated and protected;
- SQL `53+` is absent and unallocated;
- Data projections and current Recommendation P1/P2 tables already exist;
- RCA-0 is contract-and-fixture only.

## Contract Impact

Decision: `DB_CHANGE_NOT_REQUIRED`.

No SQL number, DB version, table, view, function, role, grant or retention class is allocated.

## Authority

SC remains the only allocator of SQL `53+`. Existing object writers and semantic owners remain unchanged.

## Dependencies

If a later phase needs a persisted reconciliation run, consumer view, identity mapping or new runtime role, that phase must submit a separate SC proposal including object name, owner, writer, reader, retention, privacy, rollback, compatibility and PostgreSQL 15/18 validation.

## Allowed Changes

In-memory fixtures, test resources and documentation.

## Forbidden Changes

- SQL `01..52` edits;
- SQL `53+` creation;
- Flyway activation;
- JPA entity or repository for Data projections;
- role/grant changes;
- production DB connection changes.

## Verification

- exactly one SQL file exists for every sequence `01..52`;
- no SQL `53+` exists;
- DB directories and runtime configuration are unchanged.

## Compatibility

No DB impact means no database compatibility or rollback claim is made.

## Risks

A test-only adapter can acquire hidden persistence through a repository or Spring wiring. Static diff checks must reject this.

## Handoff

RCA-0 implementation must report `DB_CHANGE: NONE` and `SQL_ALLOCATION: NOT_REQUIRED`.
