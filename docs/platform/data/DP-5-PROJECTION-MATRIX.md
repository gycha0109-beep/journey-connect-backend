# DP-5 Projection Matrix

## Status

`IMPLEMENTED / SHADOW-ONLY / VALIDATED`

## Source compatibility classes

| Class | Meaning | Projection use |
|---|---|---|
| `canonical_native` | DP-2 canonical event with validated fingerprint | allowed |
| `adapter_mapped` | successful DP-4.5 mapped output with valid source lineage | allowed |
| `adapter_duplicate` | duplicate observation with existing mapped evidence reference | use existing mapped evidence once |
| `adapter_failure` | unsupported/quarantined mapping evidence | excluded and validation evidence recorded |
| `adapter_conflict` | conflicting mapped result | fail closed |
| `foreign_authority` | source owned by another track without approved read contract | prohibited |

## Recommendation profile input matrix

| Projection | Source event/fact | Compatibility | Identity | Exposure | Aggregation | Window | Target field | Lineage | Failure |
|---|---|---|---|---|---|---|---|---|---|
| `recommendation-profile-input-v1` | canonical view/click/like/save/share/hide/report/follow facts | `canonical_native` or `adapter_mapped` | approved subject binding | general exposure only where event contract requires it | event-type count | 7/30/90d | `interaction_counts` | source event + checkpoint | source/schema/fingerprint mismatch |
| `recommendation-profile-input-v1` | region reference | allowed bounded fact | subject required | none | count, latest time, stable ref | 7/30/90d | `recent_regions` | source event + adapter evidence when used | invalid region ref |
| `recommendation-profile-input-v1` | post/journey/place/content reference | allowed bounded fact | subject required | none | count, latest time, stable ref | 7/30/90d | `recent_content_refs` | exact source reference | missing source |
| `recommendation-profile-input-v1` | tag reference | allowed bounded fact | subject required | none | count, latest time, stable ref | 7/30/90d | `recent_tag_refs` | exact source reference | unsupported tag source |
| `recommendation-profile-input-v1` | click/like/save/share/follow | positive fact only | subject required | binding per source contract | deterministic boolean/count summary | 7/30/90d | `engagement_signals` | event-level lineage | duplicate or fingerprint conflict |
| `recommendation-profile-input-v1` | hide/report/unfollow | negative fact only | subject required | none | deterministic boolean/count summary | 7/30/90d | `negative_signals` | event-level lineage | privacy or source mismatch |
| `recommendation-profile-input-v1` | P1 segment/score/decay/saturation | Intelligence semantic output | n/a | n/a | prohibited | n/a | none | none | foreign authority |

## Experiment outcome input matrix

| Projection | Source event/fact | Compatibility | Identity | Exposure | Aggregation | Window | Target field | Lineage | Failure |
|---|---|---|---|---|---|---|---|---|---|
| `experiment-outcome-input-v1` | P2 experiment exposure | exact protected authority | `user:<numeric-id>` or approved bound subject | required exact P2 exposure | one exposure observation | exposure instant | `exposure_ref`, `exposed_at` | exact exposure ref | exposure binding missing |
| `experiment-outcome-input-v1` | click | canonical/approved mapped outcome | exact exposure subject/session | exact P2 exposure | any matching event | exposed_at to +7d | `clicked` | event + exposure | event outside window |
| `experiment-outcome-input-v1` | like | canonical/approved mapped outcome | exact exposure subject/session | exact P2 exposure | any matching event | exposed_at to +7d | `liked` | event + exposure | identity conflict |
| `experiment-outcome-input-v1` | save | canonical/approved mapped outcome | exact exposure subject/session | exact P2 exposure | any matching event | exposed_at to +7d | `saved` | event + exposure | source mismatch |
| `experiment-outcome-input-v1` | share | canonical/approved mapped outcome | exact exposure subject/session | exact P2 exposure | any matching event | exposed_at to +7d | `shared` | event + exposure | privacy violation |
| `experiment-outcome-input-v1` | exposed Recommendation run status | protected run read | exact run/exposure binding | exact P2 exposure | `run_status == fallback` | exposure-bound run | `fallback_observed` | run + exposure | run binding missing |
| `experiment-outcome-input-v1` | general recommendation exposure | wrong authority | n/a | not P2 exposure | prohibited | n/a | none | none | exposure authority conflict |
| `experiment-outcome-input-v1` | behavior impression without P2 exposure | insufficient authority | n/a | missing | prohibited | n/a | none | none | exposure binding missing |

## Stable ordering

Bounded ranked references use:

```text
count DESC
last_occurred_at DESC
stable_reference ASC
```

Outcome event references use:

```text
occurred_at ASC
event_reference ASC
source_fingerprint ASC
```

No physical row order, map iteration order, locale collation or random tie break is allowed.

## Dedupe boundary

- canonical Data event: approved canonical event identity and source fingerprint;
- DP-4.5 mapped output: mapped evidence identity; duplicate observations resolve to the existing output;
- P2 exposure: exact protected exposure identity;
- profile record: one record per projection subject and snapshot;
- outcome record: one record per experiment/version/exposure logical identity and snapshot.

## Exclusions

- future events;
- events beyond checkpoint ingestion upper bound;
- late events not included in the immutable checkpoint;
- unsupported source schema;
- source fingerprint mismatch;
- adapter failures/conflicts;
- missing identity binding;
- missing P2 exposure;
