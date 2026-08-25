#!/usr/bin/env python3
"""Execute SC baseline reconciliation through the OP-3 successor verifier."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE_PATH = ROOT / "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py"
source = SOURCE_PATH.read_text(encoding="utf-8")

allocation_anchor = (
    'if list(SQL.glob("5[3-9]_*.sql")) or list(SQL.glob("[6-9][0-9]_*.sql")):\n'
    '    fail("SQL 53+ remains unallocated")\n'
)
allocation_replacement = '''successor_numbers = sorted(
    int(path.name[:2])
    for path in SQL.glob("[0-9][0-9]_*.sql")
    if path.name[:2].isdigit() and int(path.name[:2]) >= 53
)
if successor_numbers and successor_numbers != list(range(53, max(successor_numbers) + 1)):
    fail("canonical SQL successor sequence gap or duplicate")
'''
if source.count(allocation_anchor) != 1:
    raise SystemExit("FAIL: SC baseline SQL successor compatibility anchor mismatch")
source = source.replace(allocation_anchor, allocation_replacement, 1)

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
