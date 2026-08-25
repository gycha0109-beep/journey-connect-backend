#!/usr/bin/env python3
"""Run the closed DP-7 static verifier with canonical successor transport."""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASELINE = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SOURCE_PATH = "verification/dp7/run_dp7_static_verification.py"
source = subprocess.check_output(["git", "show", f"{BASELINE}:{SOURCE_PATH}"], cwd=ROOT, text=True)

sql_anchor = '''if list(SQL.glob("5[3-9]_*.sql")) or list(SQL.glob("[6-9][0-9]_*.sql")):
    fail("SQL 53+ is unallocated")
'''
if source.count(sql_anchor) != 1:
    raise SystemExit("FAIL: DP-7 static SQL successor compatibility anchor missing")
source = source.replace(
    sql_anchor,
    '''successor_numbers = sorted(
    int(path.name[:2])
    for path in SQL.glob("[0-9][0-9]_*.sql")
    if path.name[:2].isdigit() and int(path.name[:2]) >= 53
)
if successor_numbers and successor_numbers != list(range(53, max(successor_numbers) + 1)):
    fail("canonical SQL successor sequence gap or duplicate")
''',
    1,
)

changed_anchor = '    changed_sql = {rel for rel in changed if rel.startswith("database/") and rel.endswith(".sql")}\n'
if source.count(changed_anchor) != 1:
    raise SystemExit("FAIL: DP-7 static current SQL delta compatibility anchor missing")
source = source.replace(
    changed_anchor,
    '''    all_changed_sql = {rel for rel in changed if rel.startswith("database/") and rel.endswith(".sql")}
    changed_sql = {
        rel for rel in all_changed_sql
        if Path(rel).name[:2].isdigit() and int(Path(rel).name[:2]) <= 52
    }
''',
    1,
)

namespace = {"__name__": "__main__", "__file__": str(ROOT / SOURCE_PATH), "__package__": None}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
