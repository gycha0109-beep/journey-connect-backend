# SC Cross-Track Verification Plan

## Scope

Define evidence required to integrate RCA-0 without changing current authority or production state.

## Current Baseline

The repository already has Data, Recommendation, backend, PostgreSQL, DP6/DP7, SC and closure gates. RCA-0 adds a contract/fixture-specific gate but does not claim runtime or DB validation.

## Contract Impact

Required RCA-0 verification domains:

| Domain | Required evidence |
|---|---|
| baseline | branch starts from authoritative main |
| naming | RCA/RP conflict absent |
| registry | new IDs unique and reserved |
| P1 | current source unchanged; profile fields classified exact/derived/missing |
| P2 | exposure authority, seven-day outcomes, fallback and assignment gaps preserved |
| identity | no real join; failure is fail-closed |
| DB | SQL `01..52` unchanged; SQL `53+` absent |
| production | protected config/source unchanged |
| regression | Recommendation core/backend gates pass |
| documentation | all required decisions and handoff present |

## Authority

Intelligence signs P1 fixture results. Reliability signs P2 fixture results. SC signs registry, protected diff and final entry classification.

## Dependencies

Existing CI workflows remain authoritative only for the exact SHA they test. A changed head requires fresh results.

## Allowed Changes

A new non-production verifier, fixtures, contract tests and CI path coverage.

## Forbidden Changes

Representing unexecuted PostgreSQL, runtime, shadow, canary, load, replay or production checks as PASS.

## Verification

Minimum scenarios:

1. valid Data profile contract parses;
2. unsupported profile version fails;
3. missing required profile field fails;
4. profile aggregate cannot be silently converted to current P1 event stream;
5. valid Data outcome contract parses;
6. non-P2 exposure fails;
7. outcome window other than 604800 seconds fails;
8. view/hide/report cannot become P2 engagement;
9. unbound fallback fails;
10. mismatched identity fails;
11. no source, SQL or production diff;
12. current Recommendation regressions pass.

## Compatibility

Final RCA-0 verdict must be lane-specific. A combined PASS is prohibited if either P1 or P2 is unresolved.

## Risks

Broad existing CI can pass while semantic fixture coverage is incomplete. The RCA-0 verifier must assert exact scenarios and protected paths.

## Handoff

Implementation must create machine-readable verification evidence and an independent verifier whose output names the tested SHA and distinguishes executed, not executed and not applicable checks.
