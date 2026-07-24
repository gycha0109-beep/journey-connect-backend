# SC Operations and Reliability Prerequisite Matrix

## Scope

Separate prerequisites for contract work, shadow reconciliation, runtime enablement and production adoption.

## Current Baseline

Operations workers, scheduler, deployment, secrets, dashboards, purge executor and incident runbooks are incomplete. Reliability SLI/SLO, thresholds, DR, replay/backfill approval and production release evidence are incomplete.

## Contract Impact

| Capability | RCA-0 contract/fixture | RCA-1 shadow reconciliation | runtime enablement | production cutover |
|---|---|---|---|---|
| Operations worker/scheduler | not required | required if repository data is processed | required | required |
| deployment/secrets/DB access | not required | required for non-local environment | required | required |
| observability | test evidence only | required | required | required |
| Reliability P2 semantic approval | required for P2 fixtures | required | required | required |
| SLI/SLO and thresholds | not required | target values may remain `TO_BE_DECIDED` before authorization | required | required |
| rollback/DR | not required | bounded stop plan required | required | required |
| release approval | not required | not granted | required for staged traffic | required |
| SC authority approval | merge of RCA-0 decision | separate RCA-1 decision | separate decision | final decision |

## Authority

Operations owns execution plane and operational controls. Reliability owns evaluation and release gates. SC owns integration ordering and final authority changes.

## Dependencies

RCA-0 depends only on existing contracts, fixtures and regression gates. It does not consume production operational inputs.

## Allowed Changes

Documentation, tests and non-runtime fixtures.

## Forbidden Changes

Operational secrets, deployment configuration, worker/scheduler implementation, production monitoring thresholds, release transitions or rollback commands.

## Verification

The RCA-0 diff must contain no runtime configuration or operational source changes. P2 fixture changes require Reliability review evidence.

## Compatibility

Technical consumer validation and production readiness are separate classifications.

## Risks

Treating Operations or Reliability incompleteness as a blocker for all contract work would stall safe preparation; treating contract completion as runtime readiness would bypass gates.

## Handoff

Operations Runtime Enablement and Reliability Production Readiness remain later independent tracks. RCA-0 does not advance GATE-3 through GATE-9.
