#!/usr/bin/env python3
"""Execute the authoritative DP-7 verifier with approved successor paths.

The DP-7 verification logic is loaded byte-for-byte from authoritative
pre-SC-6 main. Only SC-6 and Operations successor paths are inserted into its
allowlist; DP-7 allocation, SQL and production protection semantics remain
unchanged.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASELINE = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SOURCE_PATH = "verification/dp7/run_dp7_allocation_verification.py"

source = subprocess.run(
    ["git", "show", f"{BASELINE}:{SOURCE_PATH}"],
    cwd=ROOT,
    check=True,
    text=True,
    stdout=subprocess.PIPE,
).stdout

workflow_anchor = '    ".github/actions/rca2-job/", ".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",\n'
doc_anchor = '    "docs/platform/recommendation/rca2/", "jc-backend/build.gradle.kts",\n'
verification_anchor = '    "verification/rca1b/run_rca1b_verification.py", "verification/rca2/",\n'
if workflow_anchor not in source or doc_anchor not in source or verification_anchor not in source:
    raise SystemExit("FAIL: authoritative DP-7 verifier compatibility anchor missing")

source = source.replace(
    workflow_anchor,
    workflow_anchor
    + '    ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n'
    + '    ".github/workflows/op0-rca2-stage1-operations-preparation-governance-ci.yml",\n'
    + '    ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",\n',
    1,
)
source = source.replace(
    doc_anchor,
    '    "docs/platform/recommendation/rca2/", "docs/platform/operations/op0/",\n'
    + '    "docs/platform/operations/op1/", "jc-backend/build.gradle.kts",\n',
    1,
)
source = source.replace(
    verification_anchor,
    '    "verification/rca1b/run_rca1b_verification.py", "verification/rca2/",\n'
    + '    "verification/operations/op0/", "verification/operations/op1/",\n',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(Path(__file__).resolve()),
    "__package__": None,
}
exec(compile(source, str(Path(__file__).resolve()), "exec"), namespace)
