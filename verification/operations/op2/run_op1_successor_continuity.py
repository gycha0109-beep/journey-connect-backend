#!/usr/bin/env python3
"""Run the protected OP-1 verifier for the authorised OP-2 successor scope.

The validated OP-1 verifier source is loaded from its protected exact commit.
All OP-1 checks remain intact. This wrapper extends the existing scope allowlist
with OP-2-owned paths and evaluates historical scope guards against the current
successor delta instead of replaying every repository change since OP-1.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASE_SOURCE_COMMIT = "be4e6c7d60b26c422ad5f1b92ac04d3904f28f61"
SOURCE_PATH = "verification/operations/op1/run_op1_verification.py"
EXPECTED_SOURCE_BLOB = "134323b8c28584156484b57e312008f7e5bca781"

actual_blob = subprocess.check_output(
    ["git", "rev-parse", f"{BASE_SOURCE_COMMIT}:{SOURCE_PATH}"],
    cwd=ROOT,
    text=True,
).strip()
if actual_blob != EXPECTED_SOURCE_BLOB:
    raise SystemExit(
        f"FAIL: validated OP-1 verifier source drift: actual={actual_blob} expected={EXPECTED_SOURCE_BLOB}"
    )

source = subprocess.check_output(
    ["git", "show", f"{BASE_SOURCE_COMMIT}:{SOURCE_PATH}"],
    cwd=ROOT,
    text=True,
)

scope_anchor = (
    '        ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",\n'
    '    )\n'
)
if source.count(scope_anchor) != 1:
    raise SystemExit("FAIL: OP-1 verifier scope compatibility anchor missing")
source = source.replace(
    scope_anchor,
    '        ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",\n'
    '        ".github/actions/rca2-job/action.yml",\n'
    '        "docs/platform/operations/op2/",\n'
    '        "verification/operations/op2/",\n'
    '        ".github/workflows/op2-rca2-stage1-observability-safety-ci.yml",\n'
    '    )\n',
    1,
)

changed_anchor = (
    '    changed = git(repo, "diff", "--name-only", f"{WORK_START}...HEAD").splitlines()\n'
)
if source.count(changed_anchor) != 1:
    raise SystemExit("FAIL: OP-1 changed-file compatibility anchor missing")
source = source.replace(
    changed_anchor,
    '''    current_base = git(repo, "merge-base", "origin/main", "HEAD")
    current_changed = git(repo, "diff", "--name-only", f"{current_base}...HEAD").splitlines()

    def is_canonical_successor_sql(path: str) -> bool:
        for prefix in (
            "database/journey-connect-db-v2.7/",
            "jc-backend/src/test/resources/db/canonical/",
        ):
            if path.startswith(prefix):
                name = path[len(prefix):]
                return (
                    len(name) >= 4
                    and name[:2].isdigit()
                    and name[2] == "_"
                    and name.endswith(".sql")
                    and int(name[:2]) >= 55
                )
        return False

    successor_compatibility_maintenance_paths = {
        "verification/rca2/run_rca2_verification.py",
        "verification/rca2/run_rca2_verification_closed_baseline.py",
    }
    successor_relevant_prefixes = (
        "docs/platform/recommendation/rca2/",
        "docs/platform/governance/sc-next-track/",
        "docs/platform/operations/op0/",
        "verification/rca2/",
        "verification/sc-next-track/",
        "verification/operations/op0/",
        "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/",
        "jc-backend/src/test/java/com/jc/backend/recommendation/rca2/",
        "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml",
        "docs/platform/operations/op1/",
        "verification/operations/op1/",
        ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",
        ".github/actions/rca2-job/action.yml",
        "docs/platform/operations/op2/",
        "verification/operations/op2/",
        ".github/workflows/op2-rca2-stage1-observability-safety-ci.yml",
    )
    changed = [
        path
        for path in current_changed
        if path not in successor_compatibility_maintenance_paths
        and not is_canonical_successor_sql(path)
        and (
            path.endswith(".sql")
            or path.startswith("database/")
            or path.startswith(successor_relevant_prefixes)
        )
    ]
''',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / SOURCE_PATH),
    "__package__": None,
}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
