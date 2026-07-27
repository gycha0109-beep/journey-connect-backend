#!/usr/bin/env python3
"""Run the authoritative DP-7 verifier with later SC workflow paths."""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE_COMMIT = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SOURCE_PATH = "verification/dp7/run_dp7_allocation_verification.py"

source = subprocess.check_output(
    ["git", "show", f"{SOURCE_COMMIT}:{SOURCE_PATH}"],
    cwd=ROOT,
    text=True,
)

workflow_anchor = (
    '    ".github/actions/rca2-job/", '
    '".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",\n'
)
if source.count(workflow_anchor) != 1:
    raise SystemExit("FAIL: authoritative DP-7 workflow allowlist anchor mismatch")

source = source.replace(
    workflow_anchor,
    workflow_anchor
    + '    ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n'
    + '    ".github/workflows/sc-op3-entry-governance-ci.yml",\n',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / SOURCE_PATH),
    "__package__": None,
}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
