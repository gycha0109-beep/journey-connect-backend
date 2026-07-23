# DP-7 Integration Matrix

The machine-readable source is `verification/dp7/DP7_INTEGRATION_MATRIX.tsv`.

## Classification

| Source | Target | Status | Binding rule |
|---|---|---|---|
| `recommendation-profile-input-v1` | Recommendation profile input | `CONDITIONALLY_COMPATIBLE` | 7/30/90 windows, semantic/unit mapping, lineage and quality pass; P1 authority condition remains |
| `experiment-outcome-input-v1` | `recommendation-evaluation-dataset-v1` | `CONDITIONALLY_COMPATIBLE` | authoritative P2 exposure, 604800-second outcomes and metric semantics pass; dataset authority remains |
| Data snapshot | `intelligence-input-snapshot-v1` | `INCONCLUSIVE` | generic envelope exists but Data-specific domain mapping is absent |
| Data snapshot | `search-document-projection-v1` | `INCONCLUSIVE` | Data input contract absent; direct subject/exposure mapping is incompatible |

## Required columns

```text
source_track source_contract source_schema_version source_field source_semantic source_unit source_authority
target_track target_contract target_schema_version target_field target_semantic target_unit target_authority
mapping_rule required nullable identity_namespace lineage_required quality_requirement privacy_class retention_class
compatibility_status failure_code
```

`UNVERIFIED` never becomes `COMPATIBLE`. A same-named field does not establish semantic or unit compatibility.

## Key mapping decisions

- Profile `activityWindowDays` is measured in days and permits only 7, 30 or 90.
- Outcome `outcomeWindowSeconds` is measured in seconds and must equal 604800.
- Engagement means click/like/save/share after authoritative exposure; fallback means the bound exposed run was fallback.
- Data quality score is not Intelligence confidence.
- Search `document_id` is `post:<numeric-id>` and is not subject/user identity.
- Region, content and tag mappings require explicit target contract semantics.
- Snapshot and lineage fingerprints remain Data facts; DP-7 produces separate integration fingerprints.
