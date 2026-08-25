#!/usr/bin/env python3
"""Execute the protected RCA-2 verifier with canonical successor SQL support.

The closed RCA-2 verifier is preserved byte-for-byte in
run_rca2_verification_closed_baseline.py. This adapter keeps the runtime,
authority, metric and production-boundary assertions intact while interpreting
01..54 as the closed baseline rather than a permanent database upper bound.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLOSED_SOURCE = ROOT / "verification/rca2/run_rca2_verification_closed_baseline.py"
EXPECTED_CLOSED_BLOB = "624bd7b2855f590d33e1fe3d2a5fc2d685014e4f"

actual_blob = subprocess.check_output(
    ["git", "hash-object", str(CLOSED_SOURCE)],
    cwd=ROOT,
    text=True,
).strip()
if actual_blob != EXPECTED_CLOSED_BLOB:
    raise SystemExit(
        f"FAIL: protected RCA-2 verifier source drift: actual={actual_blob} expected={EXPECTED_CLOSED_BLOB}"
    )

source = CLOSED_SOURCE.read_text(encoding="utf-8")
anchor = (
    '    check("sql_53_54_successor_only", set(inventory)==set(range(1,55)) and all(len(paths)==1 for paths in inventory.values()) and inventory[53][0].endswith("53_admin_control_plane_hardening.sql") and inventory[54][0].endswith("54_admin_control_plane_hardening_smoke_test.sql"), "exact ADM-3 successor SQL 53/54")\n'
)
if source.count(anchor) != 1:
    raise SystemExit("FAIL: RCA-2 canonical SQL compatibility anchor missing")
source = source.replace(
    anchor,
    '    check("sql_53_54_successor_only", set(range(1,55)).issubset(inventory) and set(inventory)==set(range(1,max(inventory)+1)) and all(len(paths)==1 for paths in inventory.values()) and inventory[53][0].endswith("53_admin_control_plane_hardening.sql") and inventory[54][0].endswith("54_admin_control_plane_hardening_smoke_test.sql"), "closed ADM-3 successor SQL 53/54 with contiguous later successors")\n',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / "verification/rca2/run_rca2_verification.py"),
    "__package__": None,
}
exec(compile(source, namespace["__file__"], "exec"), namespace)
