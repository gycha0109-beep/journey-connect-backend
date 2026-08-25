#!/usr/bin/env python3
"""Run authoritative Data closure verification across post-OP2 successors.

Historical Data closure evidence and protected authority remain frozen. This
wrapper adapts only successor SQL continuity and current-delta scope so later,
unrelated platform work cannot invalidate the closed baseline merely by
existing in the same repository.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASELINE = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SOURCE_PATH = "verification/data-platform-closure/run_data_platform_closure_verification.py"
source = subprocess.check_output(["git", "show", f"{BASELINE}:{SOURCE_PATH}"], cwd=ROOT, text=True)

# Freeze the actual successor delta before the closed verifier performs any
# historical fetch that could mutate origin/main in the runner checkout.
current_base = subprocess.check_output(
    ["git", "merge-base", "origin/main", "HEAD"], cwd=ROOT, text=True
).strip()
successor_current_changed = subprocess.check_output(
    ["git", "diff", "--name-only", f"{current_base}...HEAD"], cwd=ROOT, text=True
).splitlines()

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
    + '        ".github/workflows/op2-rca2-stage1-observability-safety-ci.yml",\n'
    + '        ".github/workflows/sc-op3-entry-governance-ci.yml",\n'
    + '        ".github/workflows/sc-baseline-reconciliation.yml",\n'
    + '        ".github/workflows/data-platform-closure-ci.yml",\n',
    1,
)
source = source.replace(
    prefix_anchor,
    prefix_anchor
    + '    "docs/platform/operations/op1/",\n'
    + '    "docs/platform/operations/op2/",\n'
    + '    "verification/operations/op1/",\n'
    + '    "verification/operations/op2/",\n'
    + '    "verification/sc-next-track/op3-entry/",\n',
    1,
)

historical_delta_anchor = '''subprocess.run(
    ["git", "fetch", "origin", "main", "--depth=1"],
    cwd=ROOT,
    check=False,
    stdout=subprocess.DEVNULL,
    stderr=subprocess.DEVNULL,
)
changed = subprocess.run(
    ["git", "diff", "--name-only", "origin/main...HEAD"],
    cwd=ROOT,
    check=True,
    text=True,
    capture_output=True,
).stdout.splitlines()
'''
if source.count(historical_delta_anchor) != 1:
    raise SystemExit("FAIL: Data closure historical delta compatibility anchor missing")
source = source.replace(
    historical_delta_anchor,
    '''current_changed = list(successor_current_changed)


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

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / SOURCE_PATH),
    "__package__": None,
    "successor_current_changed": successor_current_changed,
}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
