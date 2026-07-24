# RCA-1B Blocking Approval Review Package

## Scope
Packages exact-head evidence for human review without claiming organizational approval.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; all blocking approvals begin as `PENDING_USER_REVIEW`.

## Implementation
The evidence writer emits a machine-readable package for Intelligence, Reliability, Operations, Privacy/Security and System Coordination.

## Authority
Only the user or designated reviewers can approve. Automated verification reports `VERIFIED_NOT_APPROVED`.

## Dependencies
Successful exact-head PG15/18, cross-version and protected-regression artifacts.

## Execution Environment
Operations package includes image/version, isolation, role/grants, server state, limits and teardown.

## DB Access Boundary
Operations/SC review explicit grants, owner prohibition, write blocks and no persistent objects.

## Query Boundary
Intelligence/Reliability/SC review query inventory, fingerprints and physical object boundaries.

## Dataset
Data/Privacy review seed digest, synthetic-only content, retention and teardown.

## Identity/Privacy
Privacy/Security package covers raw identity/credential/query/row absence and 90-day artifact retention.

## P1 Result
Intelligence package: P1 inventory, dimensions, mismatches, expected/protected gaps, checkpoint/lineage and exit recommendation.

## P2 Result
Reliability package: exposure/window/event/fallback, migration gaps, authority mismatch inventory, integrity and exit recommendation.

## Checkpoint/Lineage
Data and lane packages include exact equality and zero-lag results.

## Evidence
System Coordination package includes work-start/final-head, registry, SQL/source protection, no-transfer markers and phase-exit recommendation.

## Verification
Each package is generated from the exact tested head; status remains `PENDING_USER_REVIEW` after technical PASS.

## Compatibility
Technical success is not organizational, production or runtime approval.

## Risks
Misstating pending review as approved would invalidate governance evidence.

## Exit Criteria
All five packages exist, are exact-head bound and contain no raw or secret material.

## Handoff
User explicitly decides merge/approval. The Draft PR is not marked ready or merged automatically.
