# Handoff: Data to Search V1

## Compatibility

`INCONCLUSIVE`

```text
DP-5 subject/exposure record != search-document-projection-v1 post document
```

Required decisions: Data-to-Search source contract, document identity/owner, region/content/tag semantics, deletion/retention/privacy, indexing authority, freshness SLA, reindex and cutover authority, legacy `/api/v1/explore` protection.

| Task | Objective | Acceptance criteria |
|---|---|---|
| `SEARCH-DATA-1` | Data-to-Search Input Contract | grain/document identity/fields/privacy/deletion/lineage approved |
| `SEARCH-DATA-2` | Search Document Projection Mapping | deterministic, no subject reinterpretation, authority preserved |
| `SEARCH-DATA-3` | Offline Index Build Validation | count/fingerprint/deletion/freshness/reindex; no traffic |
| `SEARCH-DATA-4` | Shadow Query Comparison | legacy response authority retained; parity/drift evidence |
| `SEARCH-DATA-5` | Controlled Search Cutover Decision | Ops/Rel approval, cohort, kill switch, rollback |

No document generation, indexing, routing or cutover is performed here.
