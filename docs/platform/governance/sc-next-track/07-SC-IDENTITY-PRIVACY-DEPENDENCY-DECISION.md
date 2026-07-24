# SC Identity and Privacy Dependency Decision

## Scope

Define identity behavior permitted in RCA-0 and the unresolved conditions that block later reconciliation.

## Current Baseline

- Data subject: `platform_subject_v1` / `subject:<opaque-id>`.
- protected P2 subject: `legacy_user_numeric_v1` / `user:<numeric-id>`.
- the schemes are not equal and cannot be converted by string manipulation.
- physical identity mapping owner, retention, deletion and audit remain unresolved.

## Contract Impact

RCA-0 may define an `IdentityMappingReadPort` reference and synthetic fixture bindings only. It may not implement the port, repository, table, cache or real join.

## Authority

SC remains accountable for selecting a single mapping write owner. Privacy/Security review is mandatory before physical implementation. Intelligence and Reliability may consume only purpose-bound approved reads.

## Dependencies

Before RCA-1 shadow reconciliation involving both namespaces, SC must decide:

- single physical writer;
- purpose and consumer allowlist;
- mapping/version identity;
- access audit;
- creation/effective/invalidation times;
- deletion/tombstone/cryptographic erasure behavior;
- retention and legal hold interaction;
- anonymous/session identity handling;
- replay stability and mapping failure behavior.

## Allowed Changes

- synthetic mappings in test fixtures;
- opaque references in logs and evidence;
- fail-closed `MAPPING_REQUIRED` or `IDENTITY_SCHEME_MISMATCH` results;
- interface/reference documentation without implementation.

## Forbidden Changes

- real numeric-to-opaque lookup;
- raw ID replication in general logs;
- anonymous fallback;
- best-effort subject substitution;
- P2 row or dataset hash rewrite;
- bulk export or unbounded cache.

## Verification

RCA-0 tests must show absent, invalid, expired or mismatched mapping fails closed and produces no consumer output classified as compatible.

## Compatibility

Identity-independent schema parsing is allowed. Cross-namespace semantic equivalence remains blocked.

## Risks

Synthetic fixture success can conceal production deletion, revocation and replay behavior.

## Handoff

Identity owner resolution is not a blocker for RCA-0 but is a blocker for any RCA-1 plan that compares opaque Data subjects to legacy P2 subjects.
