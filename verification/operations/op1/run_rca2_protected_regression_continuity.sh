#!/usr/bin/env bash
set -euo pipefail

EXPECTED_HEAD="${1:-${GITHUB_SHA:-}}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

test -n "$EXPECTED_HEAD"
test "$(git -C "$ROOT" rev-parse HEAD)" = "$EXPECTED_HEAD"

python3 "$ROOT/verification/rca0/run_rca0_verification.py" --execute-regressions
python3 "$ROOT/verification/operations/op1/run_historical_reconciliation_continuity.py" rca1 --execute-regressions
python3 "$ROOT/verification/operations/op1/run_historical_reconciliation_continuity.py" rca1b
python3 "$ROOT/verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py"
python3 "$ROOT/verification/data-platform-closure/run_data_platform_closure_verification.py"
python3 "$ROOT/verification/dp6/run_dp6_allocation_verification.py"
python3 "$ROOT/verification/dp7/run_dp7_allocation_verification.py"
python3 "$ROOT/verification/dp7/run_dp7_static_verification.py"
(
  cd "$ROOT/jc-backend"
  ./gradlew rca2ControlledRuntimeDarkReadVerification :jc-recommendation-core:check verifyIp125 \
    --stacktrace --no-daemon
)
