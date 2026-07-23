# SC DP-7 Cross-track Integration Validation Allocation

## Status

- Decision: `PROPOSED / MERGE REQUIRED`
- Implementation authority: `NOT GRANTED`
- Allocation branch: `codex/sc-dp7-integration-allocation`
- Authoritative baseline: `69b2f9619733e8e6068a23bb149c2aaf41f23fc9`
- DP-6 implementation PR: `#18 / MERGED`
- DP-6 implementation HEAD: `0e9b09283bad61faa830db1019d421c6e906fc7c`
- DP-6 merge commit: `69b2f9619733e8e6068a23bb149c2aaf41f23fc9`
- Current DP-7 state: `DP7_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`

## Purpose

This proposal allocates the physical persistence boundary required for DP-7 to validate, without activating, whether Data Platform snapshots and DP-6 quality verdicts are compatible with Recommendation, Intelligence and Search contracts.

```text
Data projection/snapshot
+ DP-6 VALIDATED quality verdict
+ target-track contract
+ identity/authority/privacy/retention policy
→ deterministic compatibility checks
→ append-only integration evidence
→ COMPATIBLE / INCOMPATIBLE / CONDITIONALLY_COMPATIBLE / INCONCLUSIVE
```

`COMPATIBLE` remains validation evidence only. It is not production, serving, runtime, traffic, release or cutover authority.

## Current authoritative blocker

The current Platform Registry assigns SQL `01..47` and explicitly marks SQL `48+` as unallocated. No DP-7 integration writer, reader, function-owner role or integration policy is assigned. Therefore this PR must not add SQL, database objects or Java implementation code.

## Proposed SQL allocation

The following sequence becomes implementation authority only after this proposal is reviewed and merged:

- SQL 48: `48_cross_track_integration_validation_foundation.sql` — append-only integration run/status/check foundation.
- SQL 49: `49_cross_track_contract_mapping_and_boundary_evidence.sql` — mapping, identity, authority, privacy and retention evidence.
- SQL 50: `50_cross_track_integration_verdict_and_conflict.sql` — verdict, fingerprint and conflict evidence.
- SQL 51: `51_cross_track_integration_persistence_roles_and_safe_view.sql` — atomic `NEW / DUPLICATE / CONFLICT`, roles, hardened functions and aggregate-only safe view.
- SQL 52: `52_cross_track_integration_validation.sql` — PostgreSQL 15/18 rollback-only validation.

SQL `01..47` remains protected. No SQL `48+` is present in this allocation-only PR.

## Proposed role allocation

- `jc_data_integration_writer`: execute the approved atomic DP-7 persistence function only; no direct table or cross-track write.
- `jc_data_integration_reader`: select the aggregate DP-7 safe view only.
- `jc_data_integration_function_owner`: `NOLOGIN`, `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOREPLICATION`, `NOBYPASSRLS`; fixed `search_path`; minimum grants.

These roles are proposed names only until this allocation is merged. They must not be created by this PR.

## Proposed contracts and policy

- integration policy: `data-cross-track-integration-policy-v1`;
- integration run contract: `data-cross-track-integration-run-v1`;
- integration check contract: `data-cross-track-integration-check-v1`;
- contract mapping contract: `data-cross-track-contract-mapping-v1`;
- authority matrix contract: `data-cross-track-authority-matrix-v1`;
- privacy/retention matrix contract: `data-cross-track-privacy-retention-matrix-v1`;
- integration verdict contract: `data-cross-track-integration-verdict-v1`;
- conflict code: `CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT`.

Allowed verdicts are exactly:

```text
COMPATIBLE
INCOMPATIBLE
CONDITIONALLY_COMPATIBLE
INCONCLUSIVE
```

Production-oriented verdict names are prohibited.

## Proposed fingerprint domains

| Logical field | Versioned fingerprint contract |
|---|---|
| `integration_input_fingerprint` | `integration-input-sha256-v1` |
| `integration_check_evidence_fingerprint` | `integration-check-evidence-sha256-v1` |
| `integration_mapping_fingerprint` | `integration-mapping-sha256-v1` |
| `integration_verdict_fingerprint` | `integration-verdict-sha256-v1` |
| `cross_track_contract_matrix_fingerprint` | `cross-track-contract-matrix-sha256-v1` |

All use SHA-256 lowercase hexadecimal, 64 characters, over canonical semantic inputs. Execution time, database row IDs, random UUIDs, build IDs, locale, timezone, insertion order and physical row order are excluded.

## Logical identity and persistence outcome

```text
source_snapshot_ref
+ source_track
+ target_track
+ source_contract
+ target_contract
+ integration_scope
+ validator_version
+ integration_policy_version
```

- absent identity → `NEW`;
- same identity and same input/verdict fingerprints → `DUPLICATE`;
- same identity and different input or verdict fingerprint → `CONFLICT / CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT`.

No existing evidence row may be updated. Concurrent identical requests must yield exactly one `NEW` after implementation.

## Quality verdict boundary

Only a DP-6 `VALIDATED` verdict bound to the exact source snapshot, supported `data-quality-policy-v1` and valid verdict fingerprint may proceed toward compatibility. `REJECTED`, `INCONCLUSIVE`, missing, conflicted, unsupported or snapshot-mismatched verdicts fail closed.

A quality verdict is not Recommendation approval, Intelligence confidence, Search readiness or production authorization.

## Target-track contract findings at allocation time

### Recommendation

- `recommendation-profile-input-v1` contains deterministic 7/30/90-day profile facts compatible with a future Recommendation read adapter, but the current P1 source remains authoritative and no cutover is approved.
- `experiment-outcome-input-v1` preserves authoritative P2 exposure, seven-day click/like/save/share outcomes and fallback semantics, but it does not replace `recommendation-evaluation-dataset-v1`.
- Allocation-time classification: `CONDITIONALLY_COMPATIBLE` for validation design; runtime authority unchanged.

### Intelligence

- `intelligence-input-snapshot-v1` provides a generic immutable envelope and privacy class.
- No approved Data-specific Intelligence domain input mapping identifies which Data projection schema is a valid Intelligence feature/context payload.
- Allocation-time classification: `INCONCLUSIVE`; DP-7 must not invent or activate a target contract.

### Search

- `search-document-projection-v1` is an existing Search-owned projection built from protected post, region and Operations eligibility authority.
- DP-5 profile/outcome snapshots do not have Search document grain or content semantics, and no approved Data-to-Search input contract exists.
- Allocation-time classification: `INCONCLUSIVE` for Data-to-Search integration; direct profile/outcome-to-document mapping is `INCOMPATIBLE`.

## Authority preservation

- canonical event, adapter evidence, checkpoint, projection, snapshot and quality verdict: Data authority;
- Recommendation decision and P2 exposure: Recommendation/Reliability authority as already governed;
- Intelligence runtime/model/result: Intelligence authority;
- Search document/index/runtime and cutover: Search/Intelligence authority;
- DP-7: compatibility evidence only.

DP-7 may not create or modify target-track authoritative objects.

## Privacy and retention proposal

- raw payload, raw query/text, token, email, phone, address, exact location and unrestricted identity mapping are prohibited;
- only purpose-bound pseudonymous references and aggregate-safe observations may be persisted;
- integration evidence retention class: `cross_track_integration_evidence_90d`;
- retention policy: `data-retention-policy-v1`;
- explicit `expires_at` required;
- automatic purge and physical deletion remain disabled;
- target retention may not silently exceed source authority retention.

## Protected boundary

- SQL `01..47`: unchanged;
- SQL `48+`: absent in this PR;
- no Java DP-7 contracts or validators;
- no database roles, tables, functions, views or grants;
- no Recommendation write or metric change;
- no Intelligence runtime/model execution;
- no Search projection/index write, traffic routing or cutover;
- no worker, scheduler, replay, backfill, rebuild, purge or identity repository;
- production shadow remains disabled, kill switch enabled, sampling `0 BPS`, cohort empty and `/api/v1/explore` legacy authority preserved.

## Entry rule

If this allocation PR is merged, its merge commit becomes the sole authoritative base for a separate DP-7 implementation PR. That implementation may use only SQL `48..52`, the proposed roles and policy after their status is changed to approved by the merge. No implementation may be inferred from this proposal alone.
