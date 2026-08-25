#!/usr/bin/env python3
"""Run the protected RCA-0 verifier with successor SQL compatibility.

RCA-0 owns and protects the closed 01..52 database baseline. Later governed
tracks may append canonical SQL, but must not mutate 01..52, P1/P2 source
authority, or recommendation-core. The authoritative RCA-0 verifier source is
loaded from the current repository and blob-pinned before two historical
upper-bound assertions are adapted in memory.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE_PATH = ROOT / "verification/rca0/run_rca0_verification.py"
EXPECTED_SOURCE_BLOB = "a252b3d9d40bab784f871d4adddeb1c9071c2fd1"

actual_blob = subprocess.check_output(
    ["git", "hash-object", str(SOURCE_PATH)],
    cwd=ROOT,
    text=True,
).strip()
if actual_blob != EXPECTED_SOURCE_BLOB:
    raise SystemExit(
        f"FAIL: protected RCA-0 verifier source drift: actual={actual_blob} expected={EXPECTED_SOURCE_BLOB}"
    )

source = SOURCE_PATH.read_text(encoding="utf-8")

protected_anchor = '''        def protected():
            bad=[p for p in changed if p in {S1,S2} or p.startswith("jc-recommendation-core/") or (p.startswith("database/journey-connect-db-v2.7/") and p.endswith(".sql"))]; need(not bad,f"protected diff {bad}"); return "P1/P2 sources, core, SQL unchanged"
'''
if source.count(protected_anchor) != 1:
    raise SystemExit("FAIL: RCA-0 protected SQL compatibility anchor missing")
source = source.replace(
    protected_anchor,
    '''        def protected():
            def closed_sql(path):
                if not (path.startswith("database/journey-connect-db-v2.7/") and path.endswith(".sql")):
                    return False
                match=re.match(r"database/journey-connect-db-v2\\.7/(\\d+)_",path)
                return match is not None and int(match.group(1)) <= 52
            bad=[p for p in changed if p in {S1,S2} or p.startswith("jc-recommendation-core/") or closed_sql(p)]; need(not bad,f"protected diff {bad}"); return "P1/P2 sources, core, SQL 01..52 unchanged"
''',
    1,
)

sql_anchor = '''        def sqls():
            nums=[int(m.group(1)) for p in (ROOT/"database/journey-connect-db-v2.7").glob("*.sql") if (m:=re.match(r"(\\d+)_",p.name))]; need(set(nums)==set(range(1,53)) and len(nums)==52,"SQL inventory mismatch"); return "SQL 01..52 once; 53+ absent"
'''
if source.count(sql_anchor) != 1:
    raise SystemExit("FAIL: RCA-0 SQL inventory compatibility anchor missing")
source = source.replace(
    sql_anchor,
    '''        def sqls():
            nums=[int(m.group(1)) for p in (ROOT/"database/journey-connect-db-v2.7").glob("*.sql") if (m:=re.match(r"(\\d+)_",p.name))]
            need(nums and set(range(1,53))<=set(nums) and set(nums)==set(range(1,max(nums)+1)) and len(nums)==max(nums),"SQL inventory mismatch")
            return f"SQL 01..52 protected; canonical successors contiguous through {max(nums):02d}"
''',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(SOURCE_PATH),
    "__package__": None,
}
exec(compile(source, str(SOURCE_PATH), "exec"), namespace)
