# SC Next-Track Naming and Phase Allocation

## Scope

Reserve a non-conflicting name and phase identifier for the first post-Data adoption work.

## Current Baseline

`RP` is already used by repository governance for Reliability Platform. Recommendation Platform is not an approved platform.

## Contract Impact

| Identifier | Allocation |
|---|---|
| `RCA` | Recommendation Consumer Adoption cross-track workstream |
| `RCA-0` | Recommendation Data Consumer Contract & Fixture Alignment |
| `RCA-1` | reserved for separately approved shadow reconciliation; not authorized by this decision |

Reserved contract IDs:

- `recommendation-data-consumer-alignment-v1`
- `recommendation-profile-input-consumer-v1`
- `experiment-outcome-input-consumer-v1`
- `recommendation-data-consumer-fixture-v1`

## Authority

SC owns the names and registry entries. Intelligence owns P1 consumer semantics. Reliability owns P2 outcome semantics.

## Dependencies

RCA-0 starts only after the SC-2 reconciliation PR is approved and merged.

## Allowed Changes

Use `RCA` and `RCA-0` exactly as registered.

## Forbidden Changes

- using `RP` for Recommendation;
- declaring RCA a new platform;
- using `IP-RCA-0` or `RP-RCA-0` as the shared phase ID;
- starting RCA-1 without a separate SC decision.

Lane-specific implementation evidence may identify `RCA-0/P1` and `RCA-0/P2` without creating separate platform phases.

## Verification

Search governance, docs and new source for conflicting `Recommendation Platform`, ambiguous `RP` or unregistered phase IDs.

## Compatibility

The name changes no current package, module, table, role, source or authority.

## Risks

Acronym reuse can cause Reliability approval requirements to disappear from handoffs.

## Handoff

All implementation PR titles and reports use `RCA-0` and explicitly identify the P1 or P2 lane.
