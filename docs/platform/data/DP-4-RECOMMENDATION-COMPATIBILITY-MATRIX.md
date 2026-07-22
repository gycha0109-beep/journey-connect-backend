# DP-4 Recommendation Compatibility Matrix

## Authority direction

The authoritative repository contract is **Recommendation P0 source → Data shadow candidate**. The source is `recommendation_behavior_event`; the target is a non-authoritative `platform-event-v1` user-behavior candidate. DP-4 does not feed Data events into the production Recommendation runtime.

| P0 source wire | Data target wire | Class | Required source/binding | Payload mapping | Failure boundary |
|---|---|---|---|---|---|
| `impression` | `recommendation_impression` | semantic | actor binding, entity, run, general exposure | run ref, absolute rank, surface, exposure episode | missing/general-vs-P2 exposure conflict |
| `view` | `post_view` | semantic | actor binding, post entity, surface | surface | missing entity/surface |
| `click` | `recommendation_click` | semantic | actor binding, entity, run, general exposure | run ref, absolute rank, surface | missing/general-vs-P2 exposure conflict |
| `like` | `post_like` | semantic | actor binding, post entity | source event state-transition ref | missing entity |
| `unlike` | `post_unlike` | semantic | actor binding, post entity | source event state-transition ref | missing entity |
| `save` | `post_bookmark` | semantic | actor binding, post entity | source event state-transition ref | missing entity |
| `unsave` | `post_unbookmark` | semantic | actor binding, post entity | source event state-transition ref | missing entity |
| `share` | `post_share` | semantic | actor binding, post entity, channel class | allowlisted channel class | missing channel class |
| `follow` | `follow` | semantic | actor binding, user entity | source event state-transition ref | wrong entity type |
| `unfollow` | `unfollow` | semantic | actor binding, user entity | source event state-transition ref | wrong entity type |
| `hide` | `post_hide` | semantic | actor binding, post entity, stable reason | reason code | missing reason |
| `report` | `post_report` | semantic | actor binding, post entity, stable reason | reason code, optional report ref | missing reason |
| `search` | `search_submit` | semantic | actor binding, no entity, search-run/query references | search-run ref, query ref, optional surface | raw query or missing references |
| `tag_click` | `tag_click` | semantic | actor binding, tag ref, surface | tag ref, surface | missing/malformed tag ref |
| `crew_join` | `crew_join` | semantic | actor binding, crew entity | source event state-transition ref | wrong entity type |
| `crew_leave` | `crew_leave` | semantic | actor binding, crew entity | source event state-transition ref | wrong entity type |

## Shared gates

All rows require the exact source schema, valid P0 canonical bytes/SHA-256, UTC timestamps, an approved `user:<numeric>` → `subject:<opaque>` binding, bounded scalar metadata and the existing P0 taxonomy. Source idempotency and P0 fingerprint semantics are preserved as lineage and are never rewritten.

`impression` and `click` accept only the general Recommendation exposure authority. P2 experiment exposure is rejected and never becomes the P2 denominator through this adapter.
