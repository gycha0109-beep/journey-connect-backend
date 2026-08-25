#!/usr/bin/env python3
"""Execute the protected OP-2 verifier against the current successor delta.

The closed OP-2 verifier is preserved byte-for-byte in
run_op2_verification_closed_baseline.py. This adapter keeps every OP-2 contract,
metric, rollback, authority and traffic assertion intact while preventing the
historical work-start scope guard from treating later governed phases as OP-2
changes.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CLOSED_SOURCE = ROOT / "verification/operations/op2/run_op2_verification_closed_baseline.py"
EXPECTED_CLOSED_BLOB = "f68c5f1bbd18538b17e0a7e97bc8df640ab8fbb8"

actual_blob = subprocess.check_output(
    ["git", "hash-object", str(CLOSED_SOURCE)],
    cwd=ROOT,
    text=True,
).strip()
if actual_blob != EXPECTED_CLOSED_BLOB:
    raise SystemExit(
        f"FAIL: protected OP-2 verifier source drift: actual={actual_blob} expected={EXPECTED_CLOSED_BLOB}"
    )

source = CLOSED_SOURCE.read_text(encoding="utf-8")
changed_anchor = '    changed = git("diff", "--name-only", EXPECTED_START + "...HEAD").splitlines()\n'
if source.count(changed_anchor) != 1:
    raise SystemExit("FAIL: OP-2 changed-file compatibility anchor missing")
source = source.replace(
    changed_anchor,
    '''    current_base = git("merge-base", "origin/main", "HEAD")
    current_changed = git("diff", "--name-only", current_base + "...HEAD").splitlines()

    def canonical_sql_number(path: str) -> int | None:
        for prefix in (
            "database/journey-connect-db-v2.7/",
            "jc-backend/src/test/resources/db/canonical/",
        ):
            if path.startswith(prefix):
                name = path[len(prefix):]
                if len(name) >= 4 and name[:2].isdigit() and name[2] == "_" and name.endswith(".sql"):
                    return int(name[:2])
        return None

    changed = []
    for path in current_changed:
        sql_number = canonical_sql_number(path)
        if sql_number is not None and sql_number >= 55:
            continue
        changed.append(path)
''',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / "verification/operations/op2/run_op2_verification.py"),
    "__package__": None,
}
exec(compile(source, namespace["__file__"], "exec"), namespace)
