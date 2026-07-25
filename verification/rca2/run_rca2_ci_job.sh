#!/usr/bin/env bash
set -euo pipefail

LANE="${1:?lane required}"
EXPECTED_HEAD="${2:-${GITHUB_SHA:-}}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_START="ed5708bd4da12eaea8180043f5cd7f6eb13c3099"
SC5_HEAD="a3e7045c42bf854967263f8911389afd96fda4f4"
HANDOFF="docs/platform/governance/sc-next-track/56-RCA-2-IMPLEMENTATION-HANDOFF-PROMPT.md"

run_test() {
  local classes=("$@")
  local args=()
  for class_name in "${classes[@]}"; do args+=(--tests "$class_name"); done
  (cd "$ROOT/jc-backend" && ./gradlew test "${args[@]}" --stacktrace --no-daemon)
}

case "$LANE" in
  baseline)
    git -C "$ROOT" cat-file -e "$WORK_START^{commit}"
    git -C "$ROOT" cat-file -e "$SC5_HEAD^{commit}"
    test "$(git -C "$ROOT" show -s --format=%s "$WORK_START")" = "docs(sc): authorize controlled RCA-2 runtime dark read"
    git -C "$ROOT" diff --quiet "$SC5_HEAD" "$WORK_START"
    test -f "$ROOT/$HANDOFF"
    grep -q 'RCA2_ENTRY_AUTHORIZED' "$ROOT/docs/platform/governance/sc-next-track/SC-5-RCA-2-ENTRY-AUTHORIZATION-AND-EXECUTION-BOUNDARY.md"
    ;;
  compile-unit)
    (cd "$ROOT/jc-backend" && ./gradlew rca2ControlledRuntimeDarkReadTest --stacktrace --no-daemon)
    ;;
  feature-flag)
    run_test 'com.jc.backend.recommendation.rca2.Rca2FeatureFlagAndIdentityTest'
    ;;
  traffic-zero)
    run_test 'com.jc.backend.recommendation.rca2.Rca2FeatureFlagAndIdentityTest' 'com.jc.backend.recommendation.rca2.Rca2StaticBoundaryTest'
    ;;
  authority)
    run_test 'com.jc.backend.recommendation.rca2.Rca2RuntimeOrchestratorTest' 'com.jc.backend.recommendation.rca2.Rca2ResponseMutationVerifierTest'
    ;;
  async-boundary)
    run_test 'com.jc.backend.recommendation.rca2.Rca2RuntimeOrchestratorTest' 'com.jc.backend.recommendation.rca2.Rca2StaticBoundaryTest'
    ;;
  executor)
    run_test 'com.jc.backend.recommendation.rca2.Rca2BoundedExecutorTest'
    ;;
  timeout)
    run_test 'com.jc.backend.recommendation.rca2.Rca2BoundedExecutorTest' 'com.jc.backend.recommendation.rca2.Rca2RuntimeOrchestratorTest'
    ;;
  breaker)
    run_test 'com.jc.backend.recommendation.rca2.Rca2CircuitBreakerTest'
    ;;
  kill-switch)
    run_test 'com.jc.backend.recommendation.rca2.Rca2FeatureFlagAndIdentityTest' 'com.jc.backend.recommendation.rca2.Rca2RuntimeOrchestratorTest'
    ;;
  identity)
    run_test 'com.jc.backend.recommendation.rca2.Rca2FeatureFlagAndIdentityTest' 'com.jc.backend.recommendation.rca2.Rca2CredentialNetworkRollbackTest'
    ;;
  p1-lane|p2-lane|checkpoint-lineage)
    run_test 'com.jc.backend.recommendation.rca2.Rca2RuntimeOrchestratorTest' 'com.jc.backend.recommendation.rca2.Rca2StaticBoundaryTest'
    ;;
  response-mutation)
    run_test 'com.jc.backend.recommendation.rca2.Rca2ResponseMutationVerifierTest'
    ;;
  no-write-event)
    run_test 'com.jc.backend.recommendation.rca2.Rca2RuntimeOrchestratorTest' 'com.jc.backend.recommendation.rca2.Rca2StaticBoundaryTest'
    ;;
  metrics-redaction)
    run_test 'com.jc.backend.recommendation.rca2.Rca2RuntimeOrchestratorTest' 'com.jc.backend.recommendation.rca2.Rca2StaticBoundaryTest'
    ;;
  rollback)
    run_test 'com.jc.backend.recommendation.rca2.Rca2CredentialNetworkRollbackTest'
    ;;
  protected-regression)
    python3 "$ROOT/verification/rca0/run_rca0_verification.py" --execute-regressions
    python3 "$ROOT/verification/rca1/run_rca1_verification.py" --execute-regressions
    python3 "$ROOT/verification/rca1b/run_rca1b_verification.py"
    python3 "$ROOT/verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py"
    python3 "$ROOT/verification/data-platform-closure/run_data_platform_closure_verification.py"
    python3 "$ROOT/verification/dp6/run_dp6_allocation_verification.py"
    python3 "$ROOT/verification/dp7/run_dp7_allocation_verification.py"
    python3 "$ROOT/verification/dp7/run_dp7_static_verification.py"
    (cd "$ROOT/jc-backend" && ./gradlew rca2ControlledRuntimeDarkReadVerification :jc-recommendation-core:check verifyIp125 --stacktrace --no-daemon)
    ;;
  exact-head)
    test -n "$EXPECTED_HEAD"
    test "$(git -C "$ROOT" rev-parse HEAD)" = "$EXPECTED_HEAD"
    python3 "$ROOT/verification/rca2/run_rca2_verification.py" --repo "$ROOT" --expected-head "$EXPECTED_HEAD"
    ;;
  artifact)
    test -n "$EXPECTED_HEAD"
    (cd "$ROOT/jc-backend" && ./gradlew rca2ControlledRuntimeDarkReadTest --stacktrace --no-daemon)
    python3 "$ROOT/verification/rca2/run_rca2_verification.py" --repo "$ROOT" --expected-head "$EXPECTED_HEAD"
    ;;
  *)
    echo "Unknown RCA-2 CI lane: $LANE" >&2
    exit 2
    ;;
esac
