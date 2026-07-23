# DP-6 Data Quality & Lineage Validation Hardening

## Status

`IMPLEMENTATION CANDIDATE / EXACT-HEAD CI PENDING`

Authoritative base: `c0f6b5dc8cc7089412a100989109b61315c062d0`. Implementation branch: `codex/dp-6-data-quality-validation`. PR: `#18`.

## Purpose

DP-6 validates DP-5 checkpoints, projection records, snapshots and lineage against authoritative source evidence and stores append-only quality evidence. It does not repair the validated objects or connect them to production Recommendation.

```text
canonical Data event
+ approved DP-4.5 adapter evidence
+ immutable DP-5 checkpoint/projection/snapshot/lineage
+ explicit identity binding
+ authoritative P2 exposure
→ deterministic checks
→ quality metrics
→ VALIDATED / REJECTED / INCONCLUSIVE
```

## Implemented validation capability

### Source completeness

The validator reconciles source count, exact source membership, source-set and checkpoint-definition fingerprints, last source reference, event-time range, ingestion upper bound, source contract/schema and authoritative timestamps. Duplicate source identities do not inflate counts. Late arrivals are observed without mutating the checkpoint or snapshot.

### Projection completeness

Profile projections are rebuilt through the existing DP-5 engine and checked across exact 7/30/90-day windows, source contribution, signal and aggregation boundaries. Experiment outcomes require protected P2 exposure, correct subject/variant/experiment/time, seven-day outcome window and authoritative fallback. Rejected, conflicted or unsupported adapter evidence cannot enter a validated result.

### Snapshot consistency

Record, subject and source counts are recomputed. Projection name/schema/policy, checkpoint, as-of time, status, content fingerprint and lineage fingerprint must match the immutable snapshot contract.

### Lineage integrity

Every record must have lineage; every lineage must reference an existing record, checkpoint member and authoritative source with the same fingerprint. Missing, orphan and duplicate lineage, invalid adapter binding, checkpoint mismatch and policy-version mismatch fail closed.

### Identity and exposure integrity

`subject:<opaque-id>` and `user:<numeric-id>` remain separate. Explicit binding version/source/fingerprint/scope is required where compatibility is used. Conflicting bindings fail closed. Experiment outcomes only accept `recommendation_p2_experiment_exposure`; general exposure and behavior impression cannot substitute.

### Deterministic rebuild

The coordinator reruns the actual DP-5 profile or outcome projection engine from explicit source, checkpoint, identity and exposure inputs. It compares record/subject/source counts, stable record ordering, record fingerprints, snapshot fingerprint, lineage fingerprint, profile aggregates and outcome booleans. Insertion order, map iteration, locale, timezone, build ID, row ID and execution time do not affect output.

## Pure Java boundary

Implemented in `jc-data-contracts` under `com.jc.data.contract.v1.quality`:

- immutable validation, check, metric, threshold, policy, anomaly, verdict, rebuild, late-arrival and persistence-outcome contracts;
- source, projection, snapshot, lineage, identity, exposure and deterministic-rebuild validators;
- `FullSnapshotQualityValidator` deterministic coordinator;
- deterministic quality fingerprints and `NEW / DUPLICATE / CONFLICT` decision.

The code has no Spring, JPA, JDBC, network, controller, worker, scheduler, replay/backfill, direct system clock, random UUID, mutable global state or locale/timezone-dependent calculation.

## Check, metric and verdict boundary

Checks are stably ordered and use `PASS`, `FAIL`, `SKIPPED`, `NOT_APPLICABLE`; severity is `INFO`, `WARNING`, `ERROR`, `BLOCKER`. Unexecuted required checks cannot be PASS.

Metrics include numerator, denominator, canonical 12-decimal value, unit, threshold/operator/result, version and fingerprint. Ratio metrics reject `numerator > denominator`; zero denominators remain explicit and do not synthesize 100%.

Verdicts are append-only and limited to:

- blocker or required failure → `REJECTED`;
- required skipped/missing check or metric, or insufficient evidence → `INCONCLUSIVE`;
- all required checks and thresholds pass → `VALIDATED`.

## Persistence and database boundary

SQL `43..47` implements:

- validation run/status, check and anomaly evidence;
- versioned quality policy, metrics, snapshot verdict and late-arrival observations;
- atomic logical-identity persistence with advisory transaction lock and unique constraint;
- exact `NEW / DUPLICATE / CONFLICT` behavior and append-only conflict evidence;
- deterministic rebuild comparison;
- execute-only writer, aggregate-only reader and hardened NOLOGIN function owner;
- aggregate-only quality safe view;
- rollback-only PostgreSQL 15/18 fixture.

Persistence recomputes authoritative source/checkpoint/projection/snapshot/lineage observations rather than trusting caller claims before accepting `VALIDATED`.

## Fingerprints

Implemented semantic domains:

- `data-quality-validation-input-sha256-v1`;
- `data-quality-check-evidence-sha256-v1`;
- `data-quality-metric-sha256-v1`;
- `data-quality-verdict-sha256-v1`;
- `data-quality-rebuild-comparison-sha256-v1`;
- `data-quality-late-arrival-observation-sha256-v1`.

Existing DP-2/DP-4/DP-4.5/DP-5 fingerprints are inputs only and are not redefined.

## Independent review corrections

A review separated from the initial implementation found and corrected:

1. multiple bindings for the same source/target with different binding contracts could pass — now classified as identity namespace conflict;
2. source fingerprint checks could be emitted more than once — source evidence is now deduplicated by checkpoint membership;
3. ratio metrics allowed numerator above denominator — now rejected in Java and constrained in SQL;
4. check severity and metric unit were omitted from fingerprints — both are now bound;
5. Java and SQL validation-input fingerprints could disagree on duplicate source inputs — both use canonical checkpoint membership;
6. Java decimal rounding could diverge from PostgreSQL — both use deterministic 12-digit half-up rounding;
7. orphan lineage could also create a false lineage-missing result — missing and orphan classifications are now separated;
8. late/out-of-checkpoint adapter evidence could falsely invalidate the checkpoint — adapter eligibility checks are restricted to checkpoint members;
9. the persistence boundary trusted caller-provided checks/metrics/verdict too broadly — it now validates required identities, fingerprints, thresholds, counts and authoritative observation before `VALIDATED`;
10. the safe view used verdict insertion time as latest snapshot time — it now exposes the validated snapshot as-of time.

## Access and privacy

- writer: approved persistence function execution only;
- reader: aggregate safe view only;
- function owner: NOLOGIN, non-superuser, fixed search path and minimum grants;
- PUBLIC: no function execution or safe-view access.

No user/subject/session/request/source/exposure IDs, raw payload, raw lineage, raw fingerprint dimensions, tokens, stack traces or unrestricted errors are exposed by the safe view.

## Retention and non-responsibility

All DP-6 evidence carries `data_quality_evidence_90d`, `data-retention-policy-v1` and `expires_at`. Automatic purge and physical deletion are absent.

DP-6 does not mutate source, adapter evidence, checkpoint, projection, snapshot or lineage. It does not implement production Recommendation input/write, worker, scheduler, replay, backfill, automatic rebuild, identity repository/join, P2 exposure mutation, metric denominator change, Search projection, dashboard, alerting, traffic, shadow activation or cutover.

## Verification state

Local Java 21 compilation with `-Xlint:all -Werror`, DP-5 contract/boundary regression and DP-6 contract fixtures pass. PostgreSQL 15/18 and complete exact-head protected CI remain pending until the implementation candidate is pushed.
