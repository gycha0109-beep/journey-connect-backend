# DP-7 Authority Matrix

| Object | Owner | Data read | DP-7 validate | DP-7 write | Production authority |
|---|---|---:|---:|---:|---|
| canonical event | Data | yes | yes | no | Data |
| adapter evidence | Data | yes | yes | no | Data |
| checkpoint | Data | yes | yes | no | Data |
| projection | Data | yes | yes | no | Data |
| snapshot | Data | yes | yes | no | Data |
| quality verdict | Data | yes | yes | no | Data quality |
| Recommendation decision | Recommendation | approved facts only | yes | no | Recommendation |
| P2 experiment exposure | Recommendation/Reliability | approved facts only | yes | no | Recommendation/Reliability |
| Intelligence input/result | Intelligence | contract metadata only | yes | no | Intelligence |
| Search document/index | Search | contract metadata only | yes | no | Search |
| production traffic control | Operations/owning track | no | boundary check only | no | Operations/owning track |
| integration evidence | Data DP-7 | yes | yes | atomic function only | none |

The writer executes one SECURITY DEFINER function and cannot write tables. The reader selects one aggregate-safe view and cannot read raw evidence. The function owner is NOLOGIN and has no broad owner or superuser attributes.
