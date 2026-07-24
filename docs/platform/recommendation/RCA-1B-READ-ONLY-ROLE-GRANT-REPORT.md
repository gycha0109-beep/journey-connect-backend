# RCA-1B Read-only Role and Grant Report

## Scope
Documents the disposable `rca1b_readonly` role and actual permission-negative boundary.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; no persistent role, grant or SQL allocation is authorized.

## Implementation
Bootstrap owner creates one ephemeral login with `NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS`, role-level read-only/timeouts/UTC, and explicit schema/table SELECT grants.

## Authority
Operations owns credential execution; SC owns allocation. The owner connection is prohibited from reconciliation.

## Dependencies
Canonical tables, test-only fixture schema and PostgreSQL catalog privilege functions.

## Execution Environment
Role exists only in the isolated Testcontainers database and is destroyed with it.

## DB Access Boundary
No ownership, write, sequence, default privilege or privileged function execute. PUBLIC grants are revoked for the test database boundary.

## Query Boundary
SELECT is granted only on objects used by the seven allowlisted queries. Non-allowlisted tables, P2 canonical dataset, release evidence and identity-sensitive tables remain inaccessible.

## Dataset
Seed is applied by bootstrap owner before readonly reconnect.

## Identity/Privacy
No identity mapping table is granted or queried.

## P1 Result
P1 tables are read through explicit grants only.

## P2 Result
P2 assignment/exposure/run/behavior and candidate projection are read explicitly; protected dataset/release objects are not granted.

## Checkpoint/Lineage
Checkpoint, snapshot and lineage tables receive SELECT only.

## Evidence
Catalog role attributes, privilege booleans, negative SQLSTATE classifications and recovery query result are recorded.

## Verification
INSERT, UPDATE, DELETE, MERGE, DDL, temp object, COPY, sequence, write function and prohibited SELECT must fail.

## Compatibility
Permission behavior must pass on PostgreSQL 15 and 18.

## Risks
A future persistent/shared DB role requires separate SC/Operations allocation.

## Exit Criteria
All catalog assertions and negative permission tests pass; normal allowlisted query remains usable after rollback.

## Handoff
No role or grant is promoted outside test resources.
