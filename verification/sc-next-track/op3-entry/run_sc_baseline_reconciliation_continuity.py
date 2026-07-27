#!/usr/bin/env python3
"""Execute SC baseline reconciliation through the OP-3 successor verifier."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE_PATH = ROOT / "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py"
source = SOURCE_PATH.read_text(encoding="utf-8")
anchor = (
    'current_verifier = ROOT / "verification/sc-next-track/rca2-entry/'
    'run_sc_rca2_entry_verification.py"\n'
)
replacement = (
    'current_verifier = ROOT / "verification/sc-next-track/op3-entry/'
    'run_sc_rca2_entry_continuity.py"\n'
)
if source.count(anchor) != 1:
    raise SystemExit("FAIL: SC baseline reconciliation current-verifier anchor mismatch")
source = source.replace(anchor, replacement, 1)
namespace = {"__name__": "__main__", "__file__": str(SOURCE_PATH), "__package__": None}
exec(compile(source, str(SOURCE_PATH), "exec"), namespace)
