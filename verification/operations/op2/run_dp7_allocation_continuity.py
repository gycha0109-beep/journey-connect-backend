#!/usr/bin/env python3
"""Run authoritative DP-7 verifier with OP-2 successor paths."""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASELINE = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SOURCE_PATH = "verification/dp7/run_dp7_allocation_verification.py"
source = subprocess.check_output(["git", "show", f"{BASELINE}:{SOURCE_PATH}"], cwd=ROOT, text=True)
workflow_anchor = '    ".github/actions/rca2-job/", ".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",\n'
doc_anchor = '    "docs/platform/recommendation/rca2/", "jc-backend/build.gradle.kts",\n'
verification_anchor = '    "verification/rca1b/run_rca1b_verification.py", "verification/rca2/",\n'
if source.count(workflow_anchor) != 1 or source.count(doc_anchor) != 1 or source.count(verification_anchor) != 1:
    raise SystemExit("FAIL: authoritative DP-7 compatibility anchor missing")
source = source.replace(
    workflow_anchor,
    workflow_anchor
    + '    ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n'
    + '    ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",\n'
    + '    ".github/workflows/op2-rca2-stage1-observability-safety-ci.yml",\n',
    1,
)
source = source.replace(
    doc_anchor,
    '    "docs/platform/recommendation/rca2/", "docs/platform/operations/op1/",\n'
    + '    "docs/platform/operations/op2/", "jc-backend/build.gradle.kts",\n',
    1,
)
source = source.replace(
    verification_anchor,
    '    "verification/rca1b/run_rca1b_verification.py", "verification/rca2/",\n'
    + '    "verification/operations/op1/", "verification/operations/op2/",\n',
    1,
)
namespace = {"__name__": "__main__", "__file__": str(ROOT / SOURCE_PATH), "__package__": None}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
