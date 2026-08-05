#!/usr/bin/env python3
"""Execute SC baseline reconciliation through OP-3 with approved successor SQL continuity."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE_PATH = ROOT / "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py"
source = SOURCE_PATH.read_text(encoding="utf-8")

current_anchor = (
    'current_verifier = ROOT / "verification/sc-next-track/rca2-entry/'
    'run_sc_rca2_entry_verification.py"\n'
)
current_replacement = (
    'current_verifier = ROOT / "verification/sc-next-track/op3-entry/'
    'run_sc_rca2_entry_continuity.py"\n'
)
if source.count(current_anchor) != 1:
    raise SystemExit("FAIL: SC baseline reconciliation current-verifier anchor mismatch")
source = source.replace(current_anchor, current_replacement, 1)

sql_root_anchor = 'SQL = ROOT / "database/journey-connect-db-v2.7"\n'
sql_root_replacement = (
    sql_root_anchor
    + 'SQL_V28 = ROOT / "database/journey-connect-db-v2.8"\n'
)
if source.count(sql_root_anchor) != 1:
    raise SystemExit("FAIL: SC baseline reconciliation SQL root anchor mismatch")
source = source.replace(sql_root_anchor, sql_root_replacement, 1)

range_anchor = "for number in range(1, 53):\n"
if source.count(range_anchor) != 1:
    raise SystemExit("FAIL: SC baseline reconciliation SQL range anchor mismatch")
source = source.replace(range_anchor, "for number in range(1, 55):\n", 1)

allocation_anchor = '''if list(SQL.glob("5[3-9]_*.sql")) or list(SQL.glob("[6-9][0-9]_*.sql")):
    fail("SQL 53+ remains unallocated")
'''
allocation_replacement = '''expected_v27_successors = {
    "53_admin_control_plane_hardening.sql",
    "54_admin_control_plane_hardening_smoke_test.sql",
}
actual_v27_successors = {path.name for path in SQL.glob("5[3-9]_*.sql")}
if actual_v27_successors != expected_v27_successors or list(SQL.glob("[6-9][0-9]_*.sql")):
    fail(f"unexpected v2.7 successor SQL inventory: {sorted(actual_v27_successors)}")
expected_v28 = {
    "01_search_exposure_persistence.sql",
    "02_search_exposure_digest_privilege.sql",
    "03_search_exposure_persistence_smoke_test.sql",
}
actual_v28 = {path.name for path in SQL_V28.glob("[0-9][0-9]_*.sql")}
if actual_v28 != expected_v28:
    fail(f"unexpected v2.8 SQL inventory: {sorted(actual_v28)}")
'''
if source.count(allocation_anchor) != 1:
    raise SystemExit("FAIL: SC baseline reconciliation SQL allocation anchor mismatch")
source = source.replace(allocation_anchor, allocation_replacement, 1)

namespace = {"__name__": "__main__", "__file__": str(SOURCE_PATH), "__package__": None}
exec(compile(source, str(SOURCE_PATH), "exec"), namespace)
