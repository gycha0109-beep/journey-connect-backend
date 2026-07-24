# RCA-1B P2 Database Reconciliation Result

## Scope
Records P2 non-production database reconciliation while preserving Reliability authority.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; exposure authority is `recommendation_p2_experiment_exposure`; metrics remain `engagement_rate,fallback_rate`.

## Implementation
Authoritative assignment/exposure/run/behavior and Data outcome projection are read in one read-only snapshot and normalized.

## Authority
Reliability remains accountable for P2 exposure, outcome, metric, dedupe, hash and release semantics.

## Dependencies
RCA-1 comparator taxonomy, exact 604800-second window and synthetic seed.

## Execution Environment
Independent ephemeral PostgreSQL 15 and 18 jobs.

## DB Access Boundary
Protected dataset and release evidence objects are not granted or read.

## Query Boundary
P2 authoritative/candidate query IDs plus shared checkpoint/lineage queries only.

## Dataset
Includes valid exposure/binding/boundaries/click/like/save/share/fallback and explicit contamination/mismatch/duplicate/migration cases.

## Identity/Privacy
Synthetic subject/session/run/exposure references are hashed before evidence.

## P1 Result
Independent and not aggregated with P2.

## P2 Result
Expected completion result: `RECONCILED_WITH_MIGRATION_GAPS`. Exact assignment/version/variant, binding, exposure authority, lower-inclusive upper-exclusive 604800-second window, allowed events and bound fallback are required.

## Checkpoint/Lineage
Equal zero-lag checkpoint, snapshot and lineage are exact-parity preconditions.

## Evidence
Required markers: `P2_NON_PRODUCTION_RECONCILIATION_ONLY`, `CURRENT_P2_AUTHORITY_UNCHANGED`, `NO_AUTHORITY_TRANSFER`.

## Verification
General exposure, impression, view/hide/report and unbound fallback are rejected; duplicate constraints and migration gaps are classified.

## Compatibility
Stale-unexposed assignment and persisted one-observation dedupe remain `MIGRATION_REQUIRED`.

## Risks
Canonical dataset bytes/hash and release evidence remain deliberately untested and protected.

## Exit Criteria
Baseline exact dimensions pass independently on 15/18 with only approved migration gaps.

## Handoff
Reliability review package remains `PENDING_USER_REVIEW`.
