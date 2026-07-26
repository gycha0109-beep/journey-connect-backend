#!/usr/bin/env python3
"""Execute the authoritative Data closure verifier with the SC-6 workflow allowed.

The closure verification logic is loaded byte-for-byte from authoritative
pre-SC-6 main. Only the governance-only SC-6 workflow path is inserted into
its successor allowlist; Data evidence and protected-state semantics remain unchanged.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASELINE = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SOURCE_PATH = "verification/data-platform-closure/run_data_platform_closure_verification.py"

source = subprocess.run(
    ["git", "show", f"{BASELINE}:{SOURCE_PATH}"],
    cwd=ROOT,
    check=True,
    text=True,
    stdout=subprocess.PIPE,
).stdout

anchor = '        ".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",\n'
if anchor not in source:
    raise SystemExit("FAIL: authoritative Data closure verifier compatibility anchor missing")
source = source.replace(
    anchor,
    anchor
    + '        ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(Path(__file__).resolve()),
    "__package__": None,
}
exec(compile(source, str(Path(__file__).resolve()), "exec"), namespace)
