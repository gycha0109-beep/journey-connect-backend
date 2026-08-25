#!/usr/bin/env bash
set -euo pipefail

EXPECTED_HEAD="${1:-${GITHUB_SHA:-}}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

test -n "$EXPECTED_HEAD"
test "$(git -C "$ROOT" rev-parse HEAD)" = "$EXPECTED_HEAD"

python3 "$ROOT/verification/operations/op2/run_rca0_successor_continuity.py" --execute-regressions
python3 "$ROOT/verification/operations/op2/run_historical_reconciliation_continuity.py" rca1 --execute-regressions
python3 "$ROOT/verification/operations/op2/run_historical_reconciliation_continuity.py" rca1b
python3 "$ROOT/verification/operations/op2/run_sc_baseline_reconciliation_continuity.py"
python3 "$ROOT/verification/operations/op2/run_data_platform_closure_continuity.py"
python3 "$ROOT/verification/operations/op2/run_dp6_allocation_continuity.py"
python3 "$ROOT/verification/operations/op2/run_dp7_allocation_continuity.py"
python3 "$ROOT/verification/operations/op2/run_dp7_static_continuity.py"
(
  cd "$ROOT/jc-backend"
  ./gradlew rca2ControlledRuntimeDarkReadVerification :jc-recommendation-core:check verifyIp125 \
    --stacktrace --no-daemon
)
