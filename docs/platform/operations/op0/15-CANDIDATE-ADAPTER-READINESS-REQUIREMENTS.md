# Candidate Adapter Readiness Requirements

| Field | Value |
|---|---|
| Official phase | `OP-0 RCA-2 Stage 1 Operations Preparation Baseline` |
| Work-start / authoritative main | `40ff229e2401e7d5d9c5323d469bcd012530e882` |
| RCA-2 exact final head | `511b19f80cdd42bb2fafde0563c7388b4f5b5f48` |
| RCA-2 merge commit | `b57c344c9b4e332966fe9f6d36a5da66a5faae71` |
| SC-6 exact final head | `20da93e932c50b5bebd549a56db40edb00ca1eea` |
| SC-6 merge commit | `40ff229e2401e7d5d9c5323d469bcd012530e882` |
| Artifact version | `op0-rca2-stage1-operations-preparation-v1` |
| Updated at | `2026-07-26T14:15:55Z` |


Current implementation path: `jc-backend/src/main/java/com/jc/backend/recommendation/rca2/Rca2CandidateAdapter.java`.

Current adapter state: `CONTRACT_ONLY_PRIMARY_MIRROR`. It mirrors the primary digest/count/checkpoint/lineage and is not an actual candidate source. Therefore WS-5 remains blocked.

OP-1 must name the actual target source, protocol and version; define endpoint/audience, finite timeout, read-only operations, fallback to primary, no-serving condition, no mutation/write/cache/event/notification/ranking-feedback boundary, lineage/checkpoint mapping and lane-specific kill behavior. No source is authoritative until separately approved.
