#!/usr/bin/env python3
"""Execute the original SC-6 verifier with continuity-wrapper paths allowed.

The SC-6 verification logic is loaded from the last exact head before the
continuity wrappers were introduced. Only the two historical verifier wrapper
paths are added to the governance-only diff allowlist.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASELINE = "7603081fa07b14946c66799954846eed84f62f39"
SOURCE_PATH = "verification/sc-next-track/rca2-nonzero-nonprod-entry/run_sc6_rca2_nonzero_nonprod_entry_verification.py"

source = subprocess.run(
    ["git", "show", f"{BASELINE}:{SOURCE_PATH}"],
    cwd=ROOT,
    check=True,
    text=True,
    stdout=subprocess.PIPE,
).stdout

anchor = '        "docs/platform/governance/SC-HANDOFF.md",\n'
if anchor not in source:
    raise SystemExit("FAIL: original SC-6 verifier compatibility anchor missing")

source = source.replace(
    anchor,
    anchor
    + '        "verification/sc-next-track/rca2-entry/run_sc_rca2_entry_verification.py",\n'
    + '        "verification/data-platform-closure/run_data_platform_closure_verification.py",\n',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(Path(__file__).resolve()),
    "__package__": None,
}
exec(compile(source, str(Path(__file__).resolve()), "exec"), namespace)
