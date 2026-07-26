#!/usr/bin/env python3
"""Execute the authoritative SC-5 verifier with approved successor paths.

The SC-5 verification logic is loaded byte-for-byte from the authoritative
pre-SC-6 main commit. Only SC-6, OP-0 and OP-1 successor path allowlist
entries are inserted; no SC-5 evidence, thresholds, authority rules or runtime
checks are changed.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASELINE = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SOURCE_PATH = "verification/sc-next-track/rca2-entry/run_sc_rca2_entry_verification.py"

source = subprocess.run(
    ["git", "show", f"{BASELINE}:{SOURCE_PATH}"],
    cwd=ROOT,
    check=True,
    text=True,
    stdout=subprocess.PIPE,
).stdout

workflow_anchor = '                ".github/workflows/sc-rca2-entry-ci.yml",\n'
evidence_anchor = '                "verification/sc-next-track/rca2-entry/",\n'
doc_anchor = '                "docs/platform/recommendation/rca2/",\n'
if workflow_anchor not in source or evidence_anchor not in source or doc_anchor not in source:
    raise SystemExit("FAIL: authoritative SC-5 verifier compatibility anchors missing")

source = source.replace(
    workflow_anchor,
    workflow_anchor
    + '                ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n'
    + '                ".github/workflows/op0-rca2-stage1-operations-preparation-governance-ci.yml",\n'
    + '                ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",\n',
    1,
)
source = source.replace(
    doc_anchor,
    doc_anchor
    + '                "docs/platform/operations/op0/",\n'
    + '                "docs/platform/operations/op1/",\n',
    1,
)
source = source.replace(
    evidence_anchor,
    evidence_anchor
    + '                "verification/sc-next-track/rca2-nonzero-nonprod-entry/",\n'
    + '                "verification/operations/op0/",\n'
    + '                "verification/operations/op1/",\n',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(Path(__file__).resolve()),
    "__package__": None,
}
exec(compile(source, str(Path(__file__).resolve()), "exec"), namespace)
