# IP-11.5 Verification and Self-review

## Self-review 1 — Architecture

Found and fixed:
1. IP-8 test rejected all post-26 migrations; changed to protect exact 01..26 and allow numbered 27+.
2. Stale projection could look empty; added explicit `STALE` in memory and JDBC paths.
3. Added separate read-only JDBC adapter and explicit rebuild service; no PostService transaction change.

## Self-review 2 — Failure and Operations

Found and fixed:
1. metric sink exception could escape gate; all observational calls are now isolated.
2. kill-switch supplier/control exception now fails closed.
3. production-approved non-zero sampling construction was possible; IP-11.5 now rejects it.
4. queue saturation and shutdown remain bounded and isolated.

## Self-review 3 — Security and Independent Verification

Confirmed:
- no active production bridge/config/sample
- default killed, empty cohort, 0 bps
- no raw identity/query in metric/evidence schema
- private/deleted/moderation/unknown status fail closed
- 01..26 exact and new migration starts at 27
- previous PASS counts were re-executed, not copied
- human approvals and external attestation remain open

## Result

Direct source tests PASS. External Gradle/Spring/PostgreSQL evidence remains pending; final status is not COMPLETE.

## 보호 결론

- Production shadow: `DISABLED`
- Effective production sampling: `0 BPS`
- Legacy `/api/v1/explore` response authority: `legacy`
- Search response cutover: `NOT STARTED`
- Recommendation exposure/persistence/release authority: `NONE`
- IP-12: `HOLD`
- Gradle/Spring/PostgreSQL: `NOT EXECUTED — USER-DIRECTED SKIP`
