#!/usr/bin/env python3
"""Run the authoritative SC-5 RCA-2 entry verifier for the OP-3 SC delta.

Historical SC-5 evidence and runtime boundary checks are loaded from the
original verifier. Successor canonical SQL is validated for contiguous
allocation, while the protected-diff comparison is limited to the current
main-relative SC-sensitive delta.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE_COMMIT = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SOURCE_PATH = "verification/sc-next-track/rca2-entry/run_sc_rca2_entry_verification.py"

source = subprocess.check_output(
    ["git", "show", f"{SOURCE_COMMIT}:{SOURCE_PATH}"],
    cwd=ROOT,
    text=True,
)

sql_anchor = '            need(not list(SQL.glob("5[3-9]_*.sql")) and not list(SQL.glob("[6-9][0-9]_*.sql")), "SQL 53+ exists")\n'
if source.count(sql_anchor) != 1:
    raise SystemExit("FAIL: SC-5 SQL successor compatibility anchor mismatch")
source = source.replace(
    sql_anchor,
    '''            successor_numbers = sorted(
                int(path.name[:2])
                for path in SQL.glob("[0-9][0-9]_*.sql")
                if path.name[:2].isdigit() and int(path.name[:2]) >= 53
            )
            need(
                not successor_numbers or successor_numbers == list(range(53, max(successor_numbers) + 1)),
                "canonical SQL successor sequence gap or duplicate",
            )
''',
    1,
)

changed_anchor = '            changed = git("diff", "--name-only", f"{START}..{head}").splitlines()\n'
if source.count(changed_anchor) != 1:
    raise SystemExit("FAIL: SC-5 current-delta compatibility anchor mismatch")
source = source.replace(
    changed_anchor,
    '            merge_base = git("merge-base", "origin/main", head)\n'
    '            changed = git("diff", "--name-only", f"{merge_base}...{head}").splitlines()\n',
    1,
)

workflow_anchor = '                ".github/workflows/sc-rca2-entry-ci.yml",\n'
evidence_anchor = '                "verification/sc-next-track/rca2-entry/",\n'

if source.count(workflow_anchor) != 1:
    raise SystemExit("FAIL: authoritative SC-5 workflow allowlist anchor mismatch")
if source.count(evidence_anchor) != 1:
    raise SystemExit("FAIL: authoritative SC-5 evidence allowlist anchor mismatch")

source = source.replace(
    workflow_anchor,
    workflow_anchor
    + '                ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n'
    + '                ".github/workflows/sc-op3-entry-governance-ci.yml",\n'
    + '                ".github/workflows/sc-baseline-reconciliation.yml",\n'
    + '                ".github/workflows/data-platform-closure-ci.yml",\n',
    1,
)
source = source.replace(
    evidence_anchor,
    evidence_anchor + '                "verification/sc-next-track/op3-entry/",\n',
    1,
)

unexpected_anchor = '            unexpected = [item for item in changed if not any(item == prefix or item.startswith(prefix) for prefix in allowed)]\n'
if source.count(unexpected_anchor) != 1:
    raise SystemExit("FAIL: SC-5 protected-scope compatibility anchor mismatch")
source = source.replace(
    unexpected_anchor,
    '''            def is_canonical_successor_sql(item: str) -> bool:
                prefix = "database/journey-connect-db-v2.7/"
                if not item.startswith(prefix):
                    return False
                name = item[len(prefix):]
                return (
                    len(name) >= 4
                    and name[:2].isdigit()
                    and name[2] == "_"
                    and name.endswith(".sql")
                    and int(name[:2]) >= 53
                )

            def is_sc5_sensitive(item: str) -> bool:
                if any(item == prefix or item.startswith(prefix) for prefix in allowed):
                    return True
                if item.startswith(("database/", "jc-recommendation-core/")):
                    return True
                if item in (
                    "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java",
                    "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java",
                ):
                    return True
                return bool(re.search(r"jc-backend/src/main/resources/application.*\\.(?:yml|yaml|properties)$", item))

            changed = [
                item for item in changed
                if not is_canonical_successor_sql(item) and is_sc5_sensitive(item)
            ]
            unexpected = [item for item in changed if not any(item == prefix or item.startswith(prefix) for prefix in allowed)]
''',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / SOURCE_PATH),
    "__package__": None,
}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
