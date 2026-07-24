# SC Next-Track Ownership Decision

## Scope

Determine whether Recommendation Consumer Adoption is a new platform, Intelligence work, Reliability work or a joint workstream.

## Current Baseline

- current P1 profile calculation and policy meaning are Intelligence-owned;
- current P2 assignment, exposure, metric, evaluation and release meaning are Reliability-owned;
- both remain physically located in protected recommendation packages and DB roles;
- SC owns authority-transfer and breaking-change approval.

## Contract Impact

Decision: `JOINT_INTELLIGENCE_RELIABILITY_ADOPTION`.

Recommendation Consumer Adoption is a cross-track workstream named `RCA`. It is not a new platform and does not acquire a fifth platform authority.

## Authority

| Boundary | Responsible | Accountable | Consulted |
|---|---|---|---|
| P1 profile consumer contract | Intelligence | Intelligence | Data, SC |
| P2 outcome consumer contract | Reliability semantics; Intelligence implementation coordination allowed | Reliability | Data, SC |
| shared fixture packaging | Intelligence implementation lead | SC | Reliability |
| registry and breaking changes | SC | SC | affected tracks |
| runtime operations | Operations | Operations | Intelligence, Reliability, SC |

## Dependencies

P2 fixtures require Reliability approval. Production activation requires Operations and Reliability gates. Neither production prerequisite is needed for contract-and-fixture work.

## Allowed Changes

Read-only consumer contracts, validators, fixtures, compatibility evidence and tests.

## Forbidden Changes

Physical ownership migration, direct DB write, source replacement, metric change, release transition or runtime activation.

## Verification

Confirm no modified file changes current P1/P2 source classes, DB roles, SQL or production configuration.

## Compatibility

Physical location does not override semantic ownership. Joint workstream does not imply joint write authority.

## Risks

A single undifferentiated Recommendation owner would silently absorb Reliability authority. Separate lane accountability is mandatory.

## Handoff

RCA-0 uses Intelligence as implementation lead, Reliability as mandatory P2 semantic approver and SC as final boundary approver.
