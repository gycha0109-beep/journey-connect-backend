#!/usr/bin/env python3
"""Run the authoritative SC-5 RCA-2 entry verifier with approved successor paths.

The SC-5 source is loaded from its authoritative baseline. Only SC-6, OP-0,
OP-1 and OP-2 successor workflow/document/evidence paths are appended to its
diff allowlist. All SC-5 decisions, SQL checks, traffic boundaries and authority
checks remain unchanged.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASELINE = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SOURCE_PATH = "verification/sc-next-track/rca2-entry/run_sc_rca2_entry_verification.py"

source = subprocess.check_output(
    ["git", "show", f"{BASELINE}:{SOURCE_PATH}"], cwd=ROOT, text=True
)
workflow_anchor = '                ".github/workflows/sc-rca2-entry-ci.yml",\n'
evidence_anchor = '                "verification/sc-next-track/rca2-entry/",\n'
if source.count(workflow_anchor) != 1 or source.count(evidence_anchor) != 1:
    raise SystemExit("FAIL: authoritative SC-5 verifier compatibility anchors missing")

source = source.replace(
    workflow_anchor,
    workflow_anchor
    + '                ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n'
    + '                ".github/workflows/op0-rca2-stage1-operations-preparation-governance-ci.yml",\n'
    + '                ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",\n'
    + '                ".github/workflows/op2-rca2-stage1-observability-safety-ci.yml",\n',
    1,
)
source = source.replace(
    evidence_anchor,
    evidence_anchor
    + '                "verification/sc-next-track/rca2-nonzero-nonprod-entry/",\n'
    + '                "verification/operations/op0/",\n'
    + '                "verification/operations/op1/",\n'
    + '                "verification/operations/op2/",\n'
    + '                "docs/platform/operations/op0/",\n'
    + '                "docs/platform/operations/op1/",\n'
    + '                "docs/platform/operations/op2/",\n'
    + '                "ops/observability/rca2/op2/",\n',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / SOURCE_PATH),
    "__package__": None,
}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
