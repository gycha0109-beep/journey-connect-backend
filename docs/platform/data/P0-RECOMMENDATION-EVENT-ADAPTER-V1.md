# P0 Recommendation Event Adapter V1

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `p0-recommendation-event-adapter-v1` |
| 상태 | `RECOVERED / ACTIVE DESIGN / IMPLEMENTATION_NOT_STARTED` |
| source owner | Intelligence |
| adapter owner | Data |
| source schema | `recommendation-behavior-event-v1` |
| target | `platform-event-v1` / `user_behavior` |

## Protected source

`recommendation_behavior_event` preserves event/idempotency identity, source schema, source canonical bytes/SHA-256 fingerprint, user/session/run/type/entity, occurred/received times and metadata. Existing P0 duplicate means exact protected field equality; any mismatch is `IDEMPOTENCY_CONFLICT`. No-change state mutations produce no new source event.

The adapter verifies source fingerprint/canonical bytes first and never weakens or rewrites P0 semantics.

## Ownership

- source row/idempotency/run-user-session-candidate binding: Intelligence
- adapter checkpoint/quarantine/future Data projection: Data
- recommendation policy/profile: Intelligence
- P2 metric/attribution/release: Reliability

## Mapping

| Source | Target/lineage | Rule |
|---|---|---|
| source event ID | `sourceEventRef`; deterministic future adapter event ID | do not reuse directly |
| idempotency key | adapter-scoped key | preserve exact source string, isolate producer scope |
| source schema/fingerprint | lineage | preserve exact |
| source canonical bytes | source reference | do not copy into target payload |
| numeric user ID | `actorRef` | approved mapping to `subject:<opaque-id>` only; otherwise quarantine |
| session/run | target refs | preserve binding |
| entity key | `entityRef` | validate exact `<type>:<id>`; no extra numeric copy |
| occurred/received | target timestamps | preserve UTC/microsecond source semantics |
| metadata | allowlisted payload | no free text/token/raw query/ID; dropped keys recorded in lineage |
| missing correlation/causation/build | null/limited mapping | never invent |

The proposed deterministic adapter event ID remains SC-approved registry material; implementation occurs in DP-4, not DP-1.

## Type mapping

- impression: conditional recommendation impression only after general exposure authority/dedupe checks
- view/click: post view/recommendation click after binding
- like/unlike/save/unsave: corresponding actual state-transition Data types
- share/hide/report/search: corresponding versioned Data type with privacy allowlist
- tag/follow/crew events: require correct entity/metadata binding; otherwise quarantine

General recommendation exposure remains authoritative; behavior impression must not create duplicate exposure/P2 denominator.

## Identity and privacy

Actual identity join is prohibited in DP-1. Existing `user:<numeric-id>` P2 evidence is not rewritten. Mapping failure never falls back to anonymous/another subject.

## Replay/dual-write

- adapter replay cannot change P0 row/replay result
- same adapter version + same source set must reproduce target identity/output after the fingerprint contract is approved
- source fingerprint mismatch quarantines; no auto-repair
- dual-write is prohibited through DP-4 initial transition
- any future dual-write requires atomicity/outbox, duplicate suppression, rollback, P0 replay non-regression and SC High-risk approval

## P1/P2

Data may later produce shadow `recommendation-profile-input-v1` and `experiment-outcome-input-v1`; neither is current runtime authority. Cutover requires exact reconciliation, new source/schema/consumer version and full protected regression.
