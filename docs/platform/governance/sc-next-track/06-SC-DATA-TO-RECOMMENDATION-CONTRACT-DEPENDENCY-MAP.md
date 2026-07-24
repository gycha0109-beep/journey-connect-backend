# SC Data-to-Recommendation Contract Dependency Map

## Scope

Map the exact source, candidate contract, consumer owner, missing semantics and approval boundary for RCA.

## Current Baseline

| Lane | Current authority | Data candidate | Current compatibility |
|---|---|---|---|
| P1 profile | behavior events + content facts + explicit preferences | `recommendation-profile-input-v1` | conditionally compatible |
| P2 outcome | assignment + authoritative experiment exposure + run + behavior + P1 snapshot | `experiment-outcome-input-v1` | conditionally compatible |

## Contract Impact

### P1 dependency

```text
Data profile projection
→ RCA-0 profile consumer validator
→ compatibility result only
✕ RecommendationP1ProfileSource wiring
```

Required mapping dimensions: schema/version, subject, as-of time, 7/30/90-day windows, interaction counts, ranked region/content/tag references, positive/negative signals, source count and lineage fingerprint.

Known gaps: explicit preferences, event-level timestamps/order, exact feature vocabulary transform, decay/saturation and current profile-builder input partition semantics.

### P2 dependency

```text
Data outcome projection
→ RCA-0 outcome consumer validator
→ Reliability-approved compatibility result only
✕ recommendation-evaluation-dataset-v1 replacement
```

Required mapping dimensions: experiment/version, variant, exact P2 exposure, run, subject/session, exposure time, 604800-second window, click/like/save/share booleans, fallback, outcome references and lineage.

Known gaps: assignment eligibility, stale-unexposed assignment handling, one-observation dataset dedupe, segment lookup, canonical dataset bytes/hash and release evidence binding.

## Authority

Data owns candidate records and lineage. Intelligence owns P1 consumption semantics. Reliability owns P2 evaluation semantics. SC owns adoption approval.

## Dependencies

Identity mapping, target-specific fixture approval and current-source regression evidence.

## Allowed Changes

Pure validation and mapping evidence.

## Forbidden Changes

Consumer wiring, database queries, source replacement, target writes or authority promotion.

## Verification

Each field must be classified as exact, derivable, missing, incompatible or authority-protected. Unknown required semantics fail closed.

## Compatibility

RCA-0 may return `COMPATIBLE_FOR_FIXTURE_VALIDATION`, `CONDITIONALLY_COMPATIBLE`, `MIGRATION_REQUIRED` or `INCOMPATIBLE`. It must not return `RUNTIME_READY` or `AUTHORITATIVE`.

## Risks

Record-shape similarity can hide grain, denominator and authority differences.

## Handoff

RCA-1 proposal must consume this map and resolve every missing or authority-protected dimension before shadow execution.
