#!/usr/bin/env python3
"""Run the authoritative Data closure verifier with OP-1 successor paths.

The verifier source is loaded from the authoritative pre-SC-6 baseline. Only
SC-6 and OP-1 successor workflow/document/evidence paths are added to its diff
allowlist. Data evidence, SQL inventory, authority, production configuration and
runtime side-effect checks remain unchanged.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASELINE = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SOURCE_PATH = "verification/data-platform-closure/run_data_platform_closure_verification.py"

source = subprocess.check_output(
    ["git", "show", f"{BASELINE}:{SOURCE_PATH}"], cwd=ROOT, text=True
)
workflow_anchor = '        ".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",\n'
prefix_anchor = '    "docs/platform/recommendation/rca2/",\n'
if source.count(workflow_anchor) != 1 or source.count(prefix_anchor) != 1:
    raise SystemExit("FAIL: authoritative Data closure compatibility anchor missing")

source = source.replace(
    workflow_anchor,
    workflow_anchor
    + '        ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n'
    + '        ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",\n',
    1,
)
source = source.replace(
    prefix_anchor,
    prefix_anchor
    + '    "docs/platform/operations/op1/",\n'
    + '    "verification/operations/op1/",\n',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / SOURCE_PATH),
    "__package__": None,
}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
