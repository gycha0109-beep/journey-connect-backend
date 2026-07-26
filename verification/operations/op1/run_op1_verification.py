#!/usr/bin/env python3
"""Execute the validated OP-1 verifier with narrow continuity allowances.

The verifier source is loaded from the last exact head that passed every OP-1
application, unit, integration and independent-verifier job. Only the composite
RCA-2 action path and the successor-owned OP-2 documentation, evidence,
observability configuration and workflow paths are added to the OP-1 scope
allowlist. No OP-1 runtime, traffic, security, SQL or historical-evidence check
is removed or weakened.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASE_SOURCE_COMMIT = "be4e6c7d60b26c422ad5f1b92ac04d3904f28f61"
SOURCE_PATH = "verification/operations/op1/run_op1_verification.py"
EXPECTED_SOURCE_BLOB = "134323b8c28584156484b57e312008f7e5bca781"

actual_blob = subprocess.check_output(
    ["git", "rev-parse", f"{BASE_SOURCE_COMMIT}:{SOURCE_PATH}"],
    cwd=ROOT,
    text=True,
).strip()
if actual_blob != EXPECTED_SOURCE_BLOB:
    raise SystemExit(
        f"FAIL: validated OP-1 verifier source drift: actual={actual_blob} expected={EXPECTED_SOURCE_BLOB}"
    )

source = subprocess.check_output(
    ["git", "show", f"{BASE_SOURCE_COMMIT}:{SOURCE_PATH}"],
    cwd=ROOT,
    text=True,
)
anchor = (
    '        ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",\n'
    '    )\n'
)
if source.count(anchor) != 1:
    raise SystemExit("FAIL: OP-1 verifier scope compatibility anchor missing")
source = source.replace(
    anchor,
    '        ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",\n'
    '        ".github/actions/rca2-job/action.yml",\n'
    '        "docs/platform/operations/op2/",\n'
    '        "verification/operations/op2/",\n'
    '        "ops/observability/rca2/op2/",\n'
    '        ".github/workflows/op2-rca2-stage1-observability-safety-ci.yml",\n'
    '    )\n',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / SOURCE_PATH),
    "__package__": None,
}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
