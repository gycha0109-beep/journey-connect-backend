# RCA-1B Permission-negative Verification

## Scope
Verifies that read-only is an enforced database contract, not a client declaration.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; writes, DDL, temp objects and prohibited reads are forbidden.

## Implementation
Each negative statement runs on a fresh readonly connection, records stable SQLSTATE classification, rolls back, closes, and is followed by an allowlisted recovery query.

## Authority
Operations owns credential/permission execution; SC owns the boundary; test success is not external approval.

## Dependencies
Ephemeral role, explicit grants, PostgreSQL transaction read-only and lock timeout.

## Execution Environment
PostgreSQL 15/18 isolated containers only.

## DB Access Boundary
INSERT, UPDATE, DELETE, MERGE, CREATE/ALTER/DROP/TRUNCATE, temp table, function, trigger, sequence, server-file COPY, write function, sequence read and prohibited SELECT must fail.

## Query Boundary
Negative statements are test-only and never enter the seven-query registry or evidence as raw text.

## Dataset
Uses disposable fixture objects and no production content.

## Identity/Privacy
Identity-sensitive table read is explicitly denied.

## P1 Result
Permission failures do not alter P1 baseline result.

## P2 Result
Protected dataset and release-object reads are explicitly denied.

## Checkpoint/Lineage
A normal checkpoint query must succeed after each rollback/recovery boundary.

## Evidence
Test ID, category, blocked status and SQLSTATE only; no full error text.

## Verification
At least 19 permission negatives plus deterministic lock-timeout negative must be blocked.

## Compatibility
Negative inventory and normalized classifications are compared across versions.

## Risks
PostgreSQL may vary message wording; verifier therefore uses SQLSTATE classes.

## Exit Criteria
All required negatives are blocked and recovery query remains usable.

## Handoff
Any broadened grant requires a new review package and SC decision.
