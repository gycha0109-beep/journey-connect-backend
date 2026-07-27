#!/usr/bin/env python3
"""Run the authoritative SC-5 RCA-2 entry verifier for the OP-3 SC delta.

Historical SC-5 evidence and runtime boundary checks are loaded from the
original verifier. The protected-diff comparison is rebased to the merged
OP-2 tree, and only the later SC governance verifier/workflow paths are added.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE_COMMIT = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SUCCESSOR_WORK_START = "83da5e5a075cdde2e5fd7c3e81d81f77d8c987f8"
SOURCE_PATH = "verification/sc-next-track/rca2-entry/run_sc_rca2_entry_verification.py"

source = subprocess.check_output(
    ["git", "show", f"{SOURCE_COMMIT}:{SOURCE_PATH}"],
    cwd=ROOT,
    text=True,
)

changed_anchor = 'changed = git("diff", "--name-only", f"{START}..{head}").splitlines()'
changed_replacement = (
    'changed = git("diff", "--name-only", '
    'f"83da5e5a075cdde2e5fd7c3e81d81f77d8c987f8..{head}").splitlines()'
)
workflow_anchor = '                ".github/workflows/sc-rca2-entry-ci.yml",\n'
evidence_anchor = '                "verification/sc-next-track/rca2-entry/",\n'

if source.count(changed_anchor) != 1:
    raise SystemExit("FAIL: authoritative SC-5 changed-path anchor mismatch")
if source.count(workflow_anchor) != 1:
    raise SystemExit("FAIL: authoritative SC-5 workflow allowlist anchor mismatch")
if source.count(evidence_anchor) != 1:
    raise SystemExit("FAIL: authoritative SC-5 evidence allowlist anchor mismatch")

source = source.replace(changed_anchor, changed_replacement, 1)
source = source.replace(
    workflow_anchor,
    workflow_anchor
    + '                ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n'
    + '                ".github/workflows/sc-op3-entry-governance-ci.yml",\n'
    + '                ".github/workflows/sc-baseline-reconciliation.yml",\n'
    + '                ".github/workflows/data-platform-closure-ci.yml",\n',
    1,
)
source = source.replace(
    evidence_anchor,
    evidence_anchor + '                "verification/sc-next-track/op3-entry/",\n',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / SOURCE_PATH),
    "__package__": None,
}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
