#!/usr/bin/env python3
"""Run authoritative Data closure verifier with OP-2 successor paths."""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASELINE = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SOURCE_PATH = "verification/data-platform-closure/run_data_platform_closure_verification.py"
source = subprocess.check_output(["git", "show", f"{BASELINE}:{SOURCE_PATH}"], cwd=ROOT, text=True)

sql_anchor = '''if list(sql.glob("5[3-9]_*.sql")) or list(sql.glob("[6-9][0-9]_*.sql")):
    fail("SQL 53+ present")
'''
if source.count(sql_anchor) != 1:
    raise SystemExit("FAIL: Data closure SQL successor compatibility anchor missing")
source = source.replace(
    sql_anchor,
    '''successor_numbers = sorted(
    int(path.name[:2])
    for path in sql.glob("[0-9][0-9]_*.sql")
    if path.name[:2].isdigit() and int(path.name[:2]) >= 53
)
if successor_numbers and successor_numbers != list(range(53, max(successor_numbers) + 1)):
    fail("canonical SQL successor sequence gap or duplicate")
''',
    1,
)

workflow_anchor = '        ".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",\n'
prefix_anchor = '    "docs/platform/recommendation/rca2/",\n'
if source.count(workflow_anchor) != 1 or source.count(prefix_anchor) != 1:
    raise SystemExit("FAIL: authoritative Data closure compatibility anchor missing")
source = source.replace(
    workflow_anchor,
    workflow_anchor
    + '        ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n'
    + '        ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",\n'
    + '        ".github/workflows/op2-rca2-stage1-observability-safety-ci.yml",\n',
    1,
)
source = source.replace(
    prefix_anchor,
    prefix_anchor
    + '    "docs/platform/operations/op1/",\n'
    + '    "docs/platform/operations/op2/",\n'
    + '    "verification/operations/op1/",\n'
    + '    "verification/operations/op2/",\n',
    1,
)

changed_anchor = '''changed = subprocess.run(
    ["git", "diff", "--name-only", "origin/main...HEAD"],
    cwd=ROOT,
    check=True,
    text=True,
    capture_output=True,
).stdout.splitlines()
'''
if source.count(changed_anchor) != 1:
    raise SystemExit("FAIL: Data closure current-delta compatibility anchor missing")
source = source.replace(
    changed_anchor,
    '''current_changed = subprocess.run(
    ["git", "diff", "--name-only", "origin/main...HEAD"],
    cwd=ROOT,
    check=True,
    text=True,
    capture_output=True,
).stdout.splitlines()


def canonical_sql_number(rel: str) -> int | None:
    prefix = "database/journey-connect-db-v2.7/"
    if not rel.startswith(prefix):
        return None
    name = rel[len(prefix):]
    if len(name) >= 4 and name[:2].isdigit() and name[2] == "_" and name.endswith(".sql"):
        return int(name[:2])
    return None


def data_sensitive(rel: str) -> bool:
    if rel.startswith("verification/data-platform-closure/"):
        return True
    if any(rel.startswith(prefix) for prefix in allowed_prefixes):
        return True
    if rel in allowed:
        return True
    if rel.startswith(("database/", "jc-recommendation-core/", "jc-intelligence-contracts/")):
        return True
    if rel in (
        "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java",
        "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java",
    ):
        return True
    return rel.startswith("jc-backend/src/main/resources/application")


changed = [
    rel for rel in current_changed
    if not ((number := canonical_sql_number(rel)) is not None and number >= 53)
    and data_sensitive(rel)
]
''',
    1,
)

namespace = {"__name__": "__main__", "__file__": str(ROOT / SOURCE_PATH), "__package__": None}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
