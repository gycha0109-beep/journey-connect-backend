#!/usr/bin/env python3
"""Execute the authoritative Data closure verifier with compatibility patches.

The closure verification logic is loaded byte-for-byte from authoritative
pre-SC-6 main. Compatibility patches are limited to:
- allowing the governance-only SC-6 workflow path in the successor allowlist;
- replaying historical diff checks against the verifier's own immutable MAIN
  authority instead of shallow-fetching the repository's current main branch.

Data evidence and protected-state semantics remain unchanged.
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

workflow_anchor = '        ".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",\n'
if workflow_anchor not in source:
    raise SystemExit("FAIL: authoritative Data closure verifier compatibility anchor missing")
source = source.replace(
    workflow_anchor,
    workflow_anchor
    + '        ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n',
    1,
)

fetch_block = '''subprocess.run(\n    ["git", "fetch", "origin", "main", "--depth=1"],\n    cwd=ROOT,\n    check=False,\n    stdout=subprocess.DEVNULL,\n    stderr=subprocess.DEVNULL,\n)\n'''
if fetch_block not in source or '"origin/main...HEAD"' not in source:
    raise SystemExit("FAIL: authoritative Data closure historical diff anchor missing")
source = source.replace(fetch_block, "", 1)
source = source.replace('"origin/main...HEAD"', 'f"{MAIN}...HEAD"', 1)

namespace = {
    "__name__": "__main__",
    "__file__": str(Path(__file__).resolve()),
    "__package__": None,
}
exec(compile(source, str(Path(__file__).resolve()), "exec"), namespace)
