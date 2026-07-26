#!/usr/bin/env python3
"""Execute SC baseline reconciliation with the OP-1 SC-5 continuity verifier.

The original reconciliation source and every required document, registry, SQL
and historical-evidence check are preserved. Only its final current-verifier
path is redirected to the successor-aware wrapper.
"""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE_PATH = ROOT / "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py"
source = SOURCE_PATH.read_text(encoding="utf-8")
anchor = (
    'current_verifier = ROOT / "verification/sc-next-track/rca2-entry/'
    'run_sc_rca2_entry_verification.py"\n'
)
replacement = (
    'current_verifier = ROOT / "verification/operations/op1/'
    'run_sc_rca2_entry_continuity.py"\n'
)
if source.count(anchor) != 1:
    raise SystemExit("FAIL: SC baseline reconciliation current-verifier anchor mismatch")
source = source.replace(anchor, replacement, 1)

namespace = {
    "__name__": "__main__",
    "__file__": str(SOURCE_PATH),
    "__package__": None,
}
exec(compile(source, str(SOURCE_PATH), "exec"), namespace)
