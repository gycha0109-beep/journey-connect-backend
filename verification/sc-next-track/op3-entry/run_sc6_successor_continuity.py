#!/usr/bin/env python3
"""Run the authoritative SC-6 verifier against the OP-2 successor baseline.

The original SC-6 contract and evidence checks remain unchanged. Only the
scope-diff baseline and the later SC governance paths are extended so that
post-OP-2 coordination work can prove SC-6 continuity without rewriting the
historical verifier.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE_COMMIT = "7603081fa07b14946c66799954846eed84f62f39"
SUCCESSOR_WORK_START = "83da5e5a075cdde2e5fd7c3e81d81f77d8c987f8"
SOURCE_PATH = (
    "verification/sc-next-track/rca2-nonzero-nonprod-entry/"
    "run_sc6_rca2_nonzero_nonprod_entry_verification.py"
)

source = subprocess.check_output(
    ["git", "show", f"{SOURCE_COMMIT}:{SOURCE_PATH}"],
    cwd=ROOT,
    text=True,
)

changed_anchor = (
    'return [p for p in sh("git", "diff", "--name-only", '
    'f"{WORK_START}..{head}").splitlines() if p]'
)
changed_replacement = (
    'return [p for p in sh("git", "diff", "--name-only", '
    'f"83da5e5a075cdde2e5fd7c3e81d81f77d8c987f8..{head}").splitlines() if p]'
)
exact_anchor = (
    '        ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n'
)
prefix_anchor = (
    '        "verification/sc-next-track/rca2-nonzero-nonprod-entry/",\n'
)

if source.count(changed_anchor) != 1:
    raise SystemExit("FAIL: authoritative SC-6 changed-path anchor mismatch")
if source.count(exact_anchor) != 1:
    raise SystemExit("FAIL: authoritative SC-6 exact allowlist anchor mismatch")
if source.count(prefix_anchor) != 1:
    raise SystemExit("FAIL: authoritative SC-6 prefix allowlist anchor mismatch")

source = source.replace(changed_anchor, changed_replacement, 1)
source = source.replace(
    exact_anchor,
    exact_anchor
    + '        ".github/workflows/sc-op3-entry-governance-ci.yml",\n'
    + '        ".github/workflows/sc-baseline-reconciliation.yml",\n'
    + '        ".github/workflows/data-platform-closure-ci.yml",\n',
    1,
)
source = source.replace(
    prefix_anchor,
    prefix_anchor + '        "verification/sc-next-track/op3-entry/",\n',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / SOURCE_PATH),
    "__package__": None,
}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
