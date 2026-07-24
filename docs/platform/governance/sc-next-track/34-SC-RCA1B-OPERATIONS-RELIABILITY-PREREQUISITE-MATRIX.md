# SC RCA-1B Operations and Reliability Prerequisite Matrix

## Scope

Assign required and blocking approvals for RCA-1B implementation and exit.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; RCA-1 completed offline without DB execution.

## Decision

| Role | Entry/implementation status | Required decision |
|---|---|---|
| Intelligence | `BLOCKING_APPROVAL` | P1 query, dimensions, expected-gap acceptance and P1 exit |
| Reliability | `BLOCKING_APPROVAL` | P2 exposure/window/event/fallback query, migration-gap acceptance and evidence integrity |
| Data | `REQUIRED` | candidate object inventory, checkpoint, lineage and seed interpretation |
| System Coordination | `BLOCKING_APPROVAL` | entry/exit, registry, breaking change, SQL allocation and authority protection |
| Operations | `BLOCKING_APPROVAL` | CI PostgreSQL, credentials, network isolation, role properties, timeout/resource and retention |
| Privacy/Security | `BLOCKING_APPROVAL` | synthetic identity, redaction, secret handling, raw-data prohibition and retention |

## Rationale

Model B introduces credentials and database execution even though it remains non-production and read-only. Operations and Privacy/Security therefore become blocking rather than merely consulted.

## Authority

Each role is accountable only for its listed boundary; physical implementation location does not transfer semantic authority.

## Dependencies

Approved environment/read-only/query/dataset/identity/evidence decisions.

## Execution Environment

Operations approval must confirm no production route, ephemeral storage, exact image versions and teardown.

## DB Access Boundary

Operations and SC jointly approve the role/grant inventory. Lane owners approve only semantic object access.

## Query Boundary

Intelligence and Reliability approve separate query IDs; Data confirms candidate/checkpoint/lineage objects.

## Identity/Privacy

Privacy/Security approval is mandatory before any DB execution artifact is accepted.

## Evidence

Reliability approves evidence integrity; Operations approves artifact lifecycle; SC verifies decision records.

## DB/SQL Impact

No canonical SQL allocation. Any persistent role/grant proposal reopens SC/Operations approval.

## Production Impact

None.

## Verification

SC-4 verifies matrix completeness. Actual approvals for an implementation head are `NOT_EXECUTED` and must be evidenced in the RCA-1B PR.

## Risks

A single combined approval can hide lane or operational failures. Each blocking approval must be explicit and head-bound.

## Exit Criteria

All blocking approvals are present for the exact final RCA-1B implementation head and no approval is inferred from SC-4 alone.

## Handoff

The implementation PR must include an approval evidence matrix and remain blocked while any required row is absent.