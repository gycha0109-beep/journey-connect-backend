# DP-7 Integration Matrix

## Status

`DESIGNED / MACHINE-READABLE MATRIX INCLUDED / IMPLEMENTATION NOT AUTHORIZED`

The authoritative machine-readable matrix is `verification/dp7/DP7_INTEGRATION_MATRIX.tsv`. This document explains its high-value decisions. A compatibility label is a validation design result, not production authority.

## Compatibility status rules

| Status | Meaning |
|---|---|
| `COMPATIBLE` | exact semantic, schema, unit, authority and boundary match is established |
| `INCOMPATIBLE` | a required semantic or authority condition conflicts |
| `CONDITIONALLY_COMPATIBLE` | mapping is defensible only under explicit conditions that are not runtime authority |
| `NOT_APPLICABLE` | the source field/object is not intended for the target contract |
| `UNVERIFIED` | target contract or executable evidence is insufficient; never treated as compatible |

## Data → Recommendation profile

| Source field | Recommendation meaning | Status | Required condition/failure |
|---|---|---|---|
| `subjectRef` | profile subject | `CONDITIONALLY_COMPATIBLE` | explicit supported namespace/binding; otherwise `recommendation_identity_namespace_mismatch` |
| `profileSchemaVersion` | target input schema | `CONDITIONALLY_COMPATIBLE` | target consumer must explicitly support `recommendation-profile-input-v1`; otherwise `recommendation_schema_unsupported` |
| `projectionPolicyVersion` | Data aggregation policy | `COMPATIBLE` as provenance | must not be treated as Recommendation ranking/profile policy |
| `activityWindowDays` | activity windows | `CONDITIONALLY_COMPATIBLE` | exactly 7, 30 and 90 days; otherwise `recommendation_window_mismatch` |
| `interactionCounts` | bounded behavior counts | `CONDITIONALLY_COMPATIBLE` | field vocabulary and count unit must be approved |
| `recentRegions` | ranked region facts | `CONDITIONALLY_COMPATIBLE` | region reference semantics must match; no score inference |
| `recentContentRefs` | ranked content facts | `CONDITIONALLY_COMPATIBLE` | entity refs only; no candidate/ranking authority |
| `recentTagRefs` | ranked tag facts | `CONDITIONALLY_COMPATIBLE` | tag source/vocabulary must be approved |
| `engagementSignals` | positive behavior facts | `CONDITIONALLY_COMPATIBLE` | must not redefine P2 `engagement_rate` denominator |
| `negativeSignals` | negative behavior facts | `CONDITIONALLY_COMPATIBLE` | Recommendation consumption semantics remain Intelligence-owned |
| lineage/fingerprints | source provenance | `COMPATIBLE` as evidence | exact fingerprint and lineage required |
| DP-6 quality verdict | input eligibility | `COMPATIBLE` only when `VALIDATED` | not profile quality score or production approval |

Current classification: `CONDITIONALLY_COMPATIBLE`. The Data projection can support a read-only comparison adapter, but the current P1 source and profile builder remain authoritative.

## Data → Recommendation experiment outcome

| Source field | P2 meaning | Status | Required condition/failure |
|---|---|---|---|
| `experimentRef` + `experimentVersion` | exact experiment identity/version | `CONDITIONALLY_COMPATIBLE` | exact assignment/exposure binding |
| `variantRef` | baseline/treatment variant | `CONDITIONALLY_COMPATIBLE` | exact authoritative exposure variant |
| `exposureRef` | P2 denominator authority | `COMPATIBLE` when source is protected P2 exposure | general exposure/impression forbidden |
| `runRef` | exposed recommendation run | `COMPATIBLE` when exact bound run exists | otherwise `recommendation_authority_violation` |
| `subjectRef` | assigned/exposed subject | `CONDITIONALLY_COMPATIBLE` | explicit `user:`/`subject:` binding required |
| `sessionRef` | exposure session | `CONDITIONALLY_COMPATIBLE` | exact session binding required |
| `exposedAt` | attribution origin | `COMPATIBLE` | UTC instant from authoritative exposure |
| `outcomeWindowSeconds` | attribution window | `COMPATIBLE` only at `604800` | otherwise `recommendation_window_mismatch` |
| `clicked/liked/saved/shared` | engagement observation | `COMPATIBLE` | no view/hide/report/impression substitution |
| `fallbackObserved` | fallback metric observation | `COMPATIBLE` | bound run status only |
| outcome event refs/lineage | source evidence | `COMPATIBLE` | exact event authority and lineage required |
| DP-6 quality verdict | dataset input eligibility | `COMPATIBLE` only when `VALIDATED` | does not replace Reliability Gate B/E |

Current classification: `CONDITIONALLY_COMPATIBLE`. Semantics align, but `recommendation-evaluation-dataset-v1` remains the authoritative P2 dataset and no consumer cutover is approved.

## Data → Intelligence

| Source field/object | Intelligence target | Status | Reason/failure |
|---|---|---|---|
| Data snapshot reference | `IntelligenceInputSnapshotV1.snapshotId/sourceRefs` | `CONDITIONALLY_COMPATIBLE` | immutable reference mapping is possible |
| Data schema version | generic `schemaVersion` | `UNVERIFIED` | no approved Data-specific Intelligence payload schema |
| source lineage | input provenance | `CONDITIONALLY_COMPATIBLE` | lineage reference can be preserved, raw lineage access remains restricted |
| identity binding | `identityContextRef` | `CONDITIONALLY_COMPATIBLE` | explicit binding required; automatic join prohibited |
| Data privacy classification | `PrivacyClass` | `CONDITIONALLY_COMPATIBLE` | an approved class mapping is required |
| snapshot fingerprint | `contentHash` | `CONDITIONALLY_COMPATIBLE` | canonical byte domains differ and must not be reinterpreted |
| DP-6 quality verdict | input quality evidence | `CONDITIONALLY_COMPATIBLE` | must not be copied into model confidence |
| Data profile/outcome payload | Recommendation/Search/Content/Planner feature/context | `UNVERIFIED` | target domain input contract absent |
| runtime activation | `IntelligenceRunV1` execution | `NOT_APPLICABLE` | DP-7 cannot activate runtime |

Current classification: `INCONCLUSIVE`. The generic snapshot envelope is structurally usable, but semantic target contracts are missing.

## Data → Search

| Source field/object | Search target | Status | Reason/failure |
|---|---|---|---|
| DP-5 profile subject | Search `document_id` | `INCOMPATIBLE` | user/profile subject is not a Search document identity |
| DP-5 outcome exposure | Search document | `INCOMPATIBLE` | experiment observation is not indexable document content |
| recent region refs | `region_reference` | `UNVERIFIED` | similar name does not establish document region semantics |
| recent content refs | `document_id/source_post_id` | `UNVERIFIED` | aggregate recency reference is not an authoritative document row |
| recent tag refs | future Search tag fields | `UNVERIFIED` | current Search document projection has no approved Data tag input mapping |
| source timestamps | `source_updated_at` | `INCOMPATIBLE` for direct mapping | profile/outcome observation time is not content update time |
| DP-6 quality verdict | Search input eligibility | `CONDITIONALLY_COMPATIBLE` | would be required for a future Data input, but current Search projection has different source authority |
| Data fingerprint | Search deterministic content hash | `INCOMPATIBLE` for reuse | different canonical domains must not be reused |
| Search index/rebuild/cutover | runtime operation | `NOT_APPLICABLE` | prohibited in DP-7 |

Current classification: `INCONCLUSIVE` for a future Data-to-Search contract. Direct DP-5 profile/outcome mapping to `search-document-projection-v1` is `INCOMPATIBLE`.

## Cross-cutting boundaries

| Boundary | Required result | Allocation-time status |
|---|---|---|
| Identity | explicit supported namespace and binding | design fixed; executable validation not implemented |
| Authority | no target-track write or ownership transfer | design fixed; protected diff required |
| Privacy | no raw payload/PII, purpose-bound lineage | design fixed; persistence not implemented |
| Retention | no silent target extension/deletion conflict | design fixed; source/target policy mapping required |
| Quality verdict | exact snapshot-bound `VALIDATED` only | design fixed; executable validator not implemented |
| Fingerprint | separate deterministic domains | proposed; not allocated/implemented |
| Duplicate/conflict | append-only atomic classification | proposed; not allocated/implemented |

## Fail-closed rule

Any unsupported contract/schema/policy, missing required field, semantic/unit/identity/authority/privacy/retention mismatch, missing lineage, fingerprint mismatch, invalid quality verdict, exposure mismatch, target ambiguity or production boundary violation prevents `COMPATIBLE`.
