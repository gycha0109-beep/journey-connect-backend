# RCA-2 Worklog

`ACTUAL_WORK_START_SHA=ed5708bd4da12eaea8180043f5cd7f6eb13c3099`

## Stage 0 — Entry verification
- 목적: SC-5 merge authorization and exact tree validation.
- 변경 파일: none.
- 구현 내용: actual main, PR #28, SC-5 head/tree, marker, SQL and handoff inventory.
- 실행 명령: GitHub commit/PR/tree comparison and repository inventory.
- 검증 결과: PASS; work-start `ed5708bd4da12eaea8180043f5cd7f6eb13c3099`.
- 자체 리뷰: production/authority/SQL gates preserved.
- 보완 사항: none.
- 잔여 리스크: human blocking review pending.

## Stage 1 — Runtime boundary
- 목적: post-response-only isolated shadow submission.
- 변경 파일: `RecommendationFeedService`, RCA-2 registrar/filter/orchestrator/config.
- 구현 내용: immutable primary digest registration; commit wrapper; no primary join/mutation.
- 실행 명령: RCA-2 unit tests and response mutation verifier.
- 검증 결과: exact-head CI required.
- 자체 리뷰: fail-closed if no commit/context/profile.
- 보완 사항: commit callback wrapper added after review.
- 잔여 리스크: no production request execution authorized.

## Stage 2 — Resource/failure controls
- 목적: bounded executor, timeout, breaker, kill, fallback.
- 변경 파일: executor, breaker, kill, metrics, tests.
- 구현 내용: concurrency 4, queue 100, 50ms offer, age 1000ms, total timeout 500ms, no retry.
- 실행 명령: saturation/timeout/breaker tests.
- 검증 결과: exact-head CI required.
- 자체 리뷰: no common pool/caller runs/unbounded queue.
- 보완 사항: deterministic nano-time breaker tests.
- 잔여 리스크: load/replay are NOT_EXECUTED.

## Stage 3 — Data/identity/privacy controls
- 목적: contract-only candidate, checkpoint/lineage, synthetic/test identity, redaction.
- 변경 파일: adapter/comparator/identity/redaction/credential-network contract.
- 구현 내용: P1/P2 lane assertions, exact P2 event allowlist, fail-closed identity and route/credential checks.
- 실행 명령: identity, comparator, redaction, credential/network tests.
- 검증 결과: exact-head CI required; actual credential/route NOT_EXECUTED.
- 자체 리뷰: raw IDs/results/query/secret/endpoint absent from evidence.
- 보완 사항: exact event-set equality and invalidation classification corrected.
- 잔여 리스크: actual Data runtime adapter requires a later approved stage.

## Stage 4 — Verification/documentation
- 목적: independent machine evidence, 21-job CI, 25-document review package.
- 변경 파일: `verification/rca2`, RCA-2 CI, reports.
- 구현 내용: static/history/source/runtime controls and protected regressions.
- 실행 명령: verifier, Gradle RCA-2 verification, protected workflows.
- 검증 결과: bound to exact final PR head artifact.
- 자체 리뷰: no production-only assertion recorded PASS.
- 보완 사항: pending CI findings are corrected before final evidence.
- 잔여 리스크: `APPROVAL_STATUS=PENDING_USER_REVIEW`.
