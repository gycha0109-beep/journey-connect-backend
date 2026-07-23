# DP-7 Privacy and Retention Matrix

## Status

`DESIGNED / LEGAL BASELINE UNCHANGED / PERSISTENCE NOT AUTHORIZED`

DP-7 uses the existing `data-retention-privacy-v1` technical baseline. It does not create a new legal retention decision.

## Privacy matrix

| Source material | Target use | Allowed representation | Prohibited representation | Required check |
|---|---|---|---|---|
| profile subject | Recommendation profile comparison | purpose-bound `subject:<opaque-id>` or approved restricted binding | raw numeric account ID copied into general evidence | identity namespace and binding scope |
| experiment subject | P2 outcome comparison | protected `user:<numeric-id>` reference or approved bound subject | silent numeric-to-opaque conversion | exact exposure/assignment binding |
| source payload | any target | no payload copy; references and bounded derived fields only | raw event payload, user text, raw query, report/comment content | `cross_track_raw_payload_exposure` |
| region/place | Recommendation/Search compatibility | coarse registered region/place reference | exact GPS or unapproved precision | privacy class and target field policy |
| content/tag references | Recommendation/Search compatibility | stable entity refs and bounded aggregate counts | unrestricted content text or inferred sensitive profile | purpose and reidentification check |
| lineage | validation | exact reference/fingerprint through restricted validator | aggregate reader access to raw lineage membership | lineage access authority |
| quality verdict | input eligibility | status, policy version, exact snapshot/verdict references | quality score interpreted as model confidence or production approval | quality semantic separation |
| Search document | Search authority only | stable `post:<id>` and privacy-safe indexed fields | user identity as document ID | document/identity separation |
| integration evidence | DP-7 | contract/schema/status/failure aggregates and hashed references | subject/session/request/raw fingerprint dimensions in safe view | safe-view field allowlist |

## Privacy classes

The target Intelligence contract exposes `public`, `internal`, `pseudonymous` and `restricted`. A future DP-7 validator must use an explicit mapping policy rather than comparing enum names or assuming that Data source classification and Intelligence privacy classification have identical semantics.

Minimum rules:

- public target fields cannot receive pseudonymous or restricted source material;
- pseudonymous references require purpose binding and no reversible mapping exposure;
- restricted identity binding references cannot be copied to aggregate safe views;
- missing target privacy policy is `INCONCLUSIVE`, not compatible;
- raw PII or payload exposure is a blocker.

## Retention matrix

| Evidence/object | Current source baseline | Proposed DP-7 treatment | Conflict rule |
|---|---|---|---|
| canonical platform event | default 365-day metadata; purge disabled | reference only; no copy | DP-7 evidence cannot extend access to raw event content |
| adapter evidence | 90-day metadata | reference only | expired/inaccessible source cannot be masked by longer target retention |
| projection/snapshot/lineage evidence | 90-day metadata | exact reference; no payload copy | target evidence must not imply source remains available |
| quality evidence/verdict | 90-day metadata | exact verdict reference | incompatible expiry or unsupported policy → fail closed |
| Recommendation P1/P2 evidence | owner-defined protected retention | no retention change | DP-7 cannot extend or delete target evidence |
| Intelligence run/result | Intelligence owner | no runtime/result creation | not applicable in allocation phase |
| Search document/index | Search owner and deletion semantics | no indexing or persistence | direct Data retention transfer prohibited |
| DP-7 integration evidence | proposed 90-day metadata | `cross_track_integration_evidence_90d`, explicit expiry | automatic purge remains disabled |

## Deletion semantics

A future validator must compare:

- source tombstone/deletion meaning;
- target active/removed/ineligible meaning;
- whether a target cache/index can outlive source deletion;
- whether aggregate evidence can reconstruct an identity or deleted content;
- whether historical immutable evidence is being rewritten instead of superseded.

Search currently removes ineligible or missing documents through Search-owned projection semantics. DP-7 must not invoke that path or claim that a Data snapshot deletion is equivalent without an approved mapping.

## Required privacy and retention failure codes

```text
cross_track_privacy_class_mismatch
cross_track_pii_exposure
cross_track_raw_payload_exposure
cross_track_lineage_access_violation
cross_track_retention_conflict
cross_track_deletion_semantic_conflict
cross_track_reidentification_risk
intelligence_privacy_violation
intelligence_retention_conflict
search_privacy_violation
search_retention_conflict
```

## Safe aggregate view proposal

After allocation, the reader view may expose only bounded dimensions such as:

- source track;
- target track;
- integration scope;
- source/target contract versions;
- verdict;
- severity/failure-code aggregate counts;
- validation date bucket;
- retention class/policy version;
- expiry bucket.

It must not expose subject/user/session/request/exposure/source row identifiers, raw mappings, raw payloads, raw lineage membership, raw expected/observed values or reversible fingerprint dimensions.

## Current result

The privacy and retention design is complete enough for an SC allocation decision. No runtime privacy/retention validation or safe view exists, so those checks are `NOT_EXECUTED` and cannot be reported as PASS.
