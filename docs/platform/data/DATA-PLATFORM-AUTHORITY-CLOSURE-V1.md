# Data Platform Authority Closure V1

`TECHNICAL AUTHORITY CLOSED / PRODUCTION AUTHORITY NOT ACQUIRED`

| Object | Owner | Writer | Readers | Validation | Production authority | Mutation | Activation | Handoff |
|---|---|---|---|---|---|---|---|---|
| canonical event | Data | event function/role | Data approved | Data | Operations deployment | append-only | inactive | Operations |
| adapter evidence | Data | adapter owner | aggregate/validation | Data | none | append-only | shadow | Recommendation |
| checkpoint/projection/snapshot/lineage | Data | projection owner | target comparison | Data | target+Operations | append-only | shadow | target tracks |
| quality verdict | Data | quality owner | integration/Reliability | Data quality | Reliability | append-only | evidence | Reliability |
| integration verdict | Data | integration owner | target/SC | Data compatibility | target+Ops+Reliability | append-only | evidence | target tracks |
| Recommendation decision | Recommendation | protected path | approved | Recommendation | Recommendation+Ops | owner contract | protected | Recommendation |
| P2 exposure | Reliability semantic | protected P2 path | P2/Data shadow | Reliability | Reliability | append-only | protected | Reliability |
| Intelligence result | Intelligence | runtime | approved | Intelligence | Intelligence+Ops | owner contract | disabled | Intelligence |
| Search document/index | Search | Search pipeline | Search/Ops | Search | Search+Ops | rebuildable | disabled | Search |
| traffic control | Operations | control plane | runtime/Reliability | Operations | Operations | audited | disabled | Operations |
| retention purge | Operations+Privacy/SC | none | audit | Operations | Operations | executor absent | disabled | Operations |
| replay/backfill | Reliability approval+Ops execution | none | audit/targets | Reliability | joint | executor absent | unauthorized | Reliability/Ops |

Data `VALIDATED` or `COMPATIBLE` never implies production readiness. Authority changes require SC decision and version/migration review.
