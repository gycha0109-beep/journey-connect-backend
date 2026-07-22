# DP-4 P0 Recommendation Event Adapter

Authority direction: **Recommendation P0 source → Data shadow candidate**.

## Result target

DP-4 implements a pure Java, deterministic, shadow-only adapter from the protected Recommendation P0 behavior-event source into a Data Platform user-behavior candidate.

The repository contract is directional:

```text
recommendation_behavior_event (Intelligence authority, read only)
  -> P0 adapter validation and semantic mapping
  -> Data shadow candidate + compatibility evidence
```

The reverse path into the production Recommendation runtime is not implemented.

## Baseline

- work start/main: `233016060378cd368620cac274c64ca61ce812a7`
- DP-3: main integrated
- protected SQL: `01..34`
- SQL `35+`: unallocated / `SC_SQL_ASSIGNMENT_REQUIRED`
- source schema: `recommendation-behavior-event-v1`
- target contract/schema: `platform-event-v1` / `user-behavior-event-v1`
- existing source adapter contract: `p0-recommendation-event-adapter-v1`

## Implemented boundary

The adapter:

1. verifies source row shape and source canonical-byte fingerprint;
2. validates the exact 16-event P0 taxonomy;
3. requires explicit approved numeric-user to opaque-subject binding evidence;
4. hashes the source session into a privacy-safe deterministic Data session reference;
5. validates entity, run and exposure bindings without guessing missing values;
6. maps only allowlisted, target-contract fields;
7. classifies compatibility as semantic rather than exact;
8. emits a separate versioned adapter output fingerprint;
9. returns stable unsupported/quarantine failures instead of throwing mapping guesses;
10. records producer build only as evidence and excludes it from the output fingerprint.

No canonical Data event ID is minted because the deterministic adapter event-ID registry and SQL persistence range are not SC-approved. The result is a candidate/evidence contract, not a persisted or production-authoritative event.

## Exposure authority

- general recommendation impression/click requires `recommendation_general_exposure_v1` binding;
- behavior impression does not independently create an exposure;
- `recommendation_p2_experiment_exposure_v1` is rejected by this adapter;
- no general/P2 exposure double counting is possible;
- no P2 assignment, exposure, dataset, metric or release evidence is written.

## Identity and privacy

The adapter never interprets a numeric P0 user ID as an opaque subject. An explicit versioned binding must match the source row user ID. Missing or conflicting mapping is `identity_mapping_required`.

Raw source session IDs, idempotency keys, canonical payload bytes, queries, credentials, tokens and unrestricted errors are not copied into the mapped payload. Sensitive metadata fails closed. Unknown non-sensitive metadata is dropped and listed as evidence.

## Determinism

The adapter output fingerprint includes adapter/mapping versions, source event reference and source fingerprint, target versions, mapped event family/type/time, pseudonymous actor/session/entity, source run lineage, exposure authority reference and mapped payload. It excludes producer build ID, source received time, source idempotency key and canonical payload bytes.

Tests cover repeated output, insertion-order, locale/timezone, build-ID exclusion, adapter-version change and source-meaning change.

## Persistence status

No SQL is added. SQL `35+` remains unallocated. Shadow persistence, checkpointing and append-only adapter run/output/failure evidence require a later SC SQL assignment. Current machine-readable fixtures and CI artifacts are the DP-4 evidence authority.

## Non-regression

- SQL `01..34` unchanged;
- Recommendation P0 source, parser, validator, canonicalization and fingerprint unchanged;
- Recommendation runtime and production tables receive no writes;
- P1/P2 source authority and metrics unchanged;
- Search/Intelligence production source unchanged;
- production shadow remains disabled, kill switch enabled, sampling `0 BPS`, cohort empty and Search cutover not started.
