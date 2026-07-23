# DP-7 Authority Matrix

## Status

`DESIGNED / VALIDATION-ONLY / IMPLEMENTATION NOT AUTHORIZED`

DP-7 records compatibility evidence. It does not acquire read, write, runtime, release or production authority merely because a contract is compatible.

## Object authority

| Object | Owning track | Read allowed tracks | Write allowed tracks | Validation allowed tracks | Production authority |
|---|---|---|---|---|---|
| canonical platform event | Data | approved Data consumers through versioned contracts | Data only | Data; DP-7 read evidence only after approval | none in DP-7 |
| Recommendation P0 source event | Intelligence/Recommendation compatibility authority | approved Recommendation consumers and Data adapter | protected Recommendation writer | Data adapter/DP-7 read validation only | Recommendation |
| adapter mapped evidence | Data | approved Data validators | Data adapter evidence function only | Data/DP-7 | none |
| source checkpoint | Data | approved Data validators | Data projection persistence only | Data/DP-7 | none |
| projection record | Data | approved snapshot consumers | Data projection persistence only | Data/DP-7 | none |
| projection snapshot | Data | approved target read adapter after gate | Data projection persistence only | Data/DP-6/DP-7 | none |
| projection lineage | Data | purpose-bound validators; no aggregate reader raw access | Data projection persistence only | Data/DP-6/DP-7 | none |
| snapshot quality verdict | Data | approved integration validators | Data quality persistence only | Data/DP-7 | quality evidence only |
| Recommendation decision/run/result | Intelligence/Recommendation | approved application/Reliability/Operations reads | protected Recommendation path | DP-7 reference validation only | Recommendation/Operations gate |
| P2 experiment assignment | Reliability semantic authority; protected Recommendation physical path | approved P2/Data shadow reads | protected current P2 writer | DP-7 reference validation only | Reliability |
| P2 experiment exposure | Reliability semantic authority; protected Recommendation physical path | approved P2/Data shadow reads | protected current P2 writer | DP-7 exact authority validation | Reliability metric denominator |
| Intelligence input/run/result | Intelligence | approved domain consumers | Intelligence only | DP-7 contract validation only | Intelligence + applicable release/control gates |
| Search document projection | Intelligence/Search | Search runtime/application approved reads | protected Search projection owner | DP-7 contract validation only | Search |
| Search index/runtime/exposure | Intelligence/Search | approved Search/Reliability/Operations reads | Search only | DP-7 contract validation only | Search/Reliability/Operations gates |
| production traffic control | Operations/System-controlled runtime boundary | approved control readers | authorized Operations/runtime path only | DP-7 protected-state validation only | Operations/System Coordination |
| cross-track integration evidence | Data DP-7 after allocation | proposed aggregate reader only | proposed DP-7 persistence function only | Data/System Coordination | none |

## Validation authority rules

DP-7 may:

- read immutable references supplied through an approved contract;
- compare contract IDs, schema/policy versions, semantics, units and required fields;
- compare explicit identity binding evidence;
- validate lineage and fingerprint references without copying raw payloads;
- confirm the exact DP-6 verdict and source snapshot binding;
- emit append-only integration checks and verdict evidence after allocation.

DP-7 may not:

- write another track's table or repository;
- create Recommendation decisions or P2 exposure;
- create Intelligence runs/results or execute a model;
- create/update Search documents/indexes or invoke rebuild/cutover;
- mutate Data source/projection/snapshot/quality evidence;
- change release, traffic, sampling, cohort, kill switch or authority state;
- convert validation evidence into production approval.

## Required authority failure codes

```text
cross_track_read_authority_violation
cross_track_write_authority_violation
cross_track_validation_authority_violation
cross_track_production_authority_violation
cross_track_object_ownership_conflict
recommendation_authority_violation
recommendation_production_write_detected
intelligence_authority_violation
intelligence_runtime_activation_detected
search_authority_violation
search_production_index_write_detected
search_cutover_violation
```

## Existing compatibility arrangements preserved

1. Recommendation P1/P2 physical paths remain protected even where semantic ownership is assigned to Intelligence or Reliability.
2. Data profile/outcome projections remain shadow-only and do not replace current runtime sources.
3. Search document projection remains Search-owned and is not a Data snapshot target.
4. `VALIDATED` remains Data quality evidence and cannot satisfy Reliability Gate E, Operations approval or Search cutover.
5. Identity mapping remains unresolved at the physical owner level; DP-7 cannot implement a mapping repository.

## Allocation requirement

The proposed DP-7 writer/reader/function-owner roles do not exist until the SC allocation proposal is merged and implemented in a separate PR. This allocation-only phase records no runtime authority PASS.
