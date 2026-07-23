# Handoff: Data to Intelligence V1

## Compatibility

`INCONCLUSIVE`

`intelligence-input-snapshot-v1` generic envelope exists, but a Data-specific domain semantic mapping is not approved. Data quality verdict and model confidence are distinct.

Required decisions: target domain, input contract/version, required fields, semantic mapping, feature ownership, identity namespace, privacy class, lineage/quality requirements, retention and runtime activation authority.

| Task | Objective | Acceptance criteria |
|---|---|---|
| `IP-DATA-1` | Data-specific Intelligence Input Contract | domain/fields/semantics/version/identity/privacy/lineage/quality approved |
| `IP-DATA-2` | Feature Semantic Mapping and Provenance | owner/derivation/fingerprint/freshness/override recorded |
| `IP-DATA-3` | Offline Intelligence Integration Validation | no activation; deterministic fixture; failure isolation; verdict != confidence |
| `IP-DATA-4` | Runtime Activation Readiness | Ops/Rel gates, model/policy versions, observability, rollback approved |

This closure does not invent the missing contract.
