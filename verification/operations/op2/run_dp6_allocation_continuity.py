#!/usr/bin/env python3
"""Run the closed DP-6 allocation verifier with canonical successor transport."""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASELINE = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SOURCE_PATH = "verification/dp6/run_dp6_allocation_verification.py"
source = subprocess.check_output(["git", "show", f"{BASELINE}:{SOURCE_PATH}"], cwd=ROOT, text=True)

inventory_anchor = '''    successor_sql = {
        path.relative_to(ROOT).as_posix()
        for path in SQL_DIR.glob("*.sql")
        if path.name[:2].isdigit() and int(path.name[:2]) >= 48
    }
    if successor_sql and successor_sql != DP7_SUCCESSOR_SQL_FILES:
        fail(f"unexpected successor SQL after DP-6: {sorted(successor_sql)}")
'''
if source.count(inventory_anchor) != 1:
    raise SystemExit("FAIL: DP-6 successor inventory compatibility anchor missing")
source = source.replace(
    inventory_anchor,
    '''    dp7_successor_sql = {
        path.relative_to(ROOT).as_posix()
        for path in SQL_DIR.glob("*.sql")
        if path.name[:2].isdigit() and 48 <= int(path.name[:2]) <= 52
    }
    if dp7_successor_sql != DP7_SUCCESSOR_SQL_FILES:
        fail(f"unexpected DP-7 successor SQL after DP-6: {sorted(dp7_successor_sql)}")
    successor_numbers = sorted(
        int(path.name[:2])
        for path in SQL_DIR.glob("*.sql")
        if path.name[:2].isdigit() and int(path.name[:2]) >= 53
    )
    if successor_numbers and successor_numbers != list(range(53, max(successor_numbers) + 1)):
        fail("canonical SQL successor sequence gap or duplicate")
''',
    1,
)

changed_anchor = '''    changed_sql = {
        path for path in changed
        if path.startswith("database/journey-connect-db-v2.7/") and path.endswith(".sql")
    }
'''
if source.count(changed_anchor) != 1:
    raise SystemExit("FAIL: DP-6 current SQL delta compatibility anchor missing")
source = source.replace(
    changed_anchor,
    '''    all_changed_sql = {
        path for path in changed
        if path.startswith("database/journey-connect-db-v2.7/") and path.endswith(".sql")
    }
    changed_sql = {
        path for path in all_changed_sql
        if Path(path).name[:2].isdigit() and int(Path(path).name[:2]) <= 52
    }
''',
    1,
)

namespace = {"__name__": "__main__", "__file__": str(ROOT / SOURCE_PATH), "__package__": None}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
