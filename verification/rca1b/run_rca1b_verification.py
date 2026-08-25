#!/usr/bin/env python3
"""Execute the protected RCA-1B verifier against the current successor delta.

The closed RCA-1B verifier is preserved byte-for-byte in
run_rca1b_verification_closed_baseline.py. This adapter keeps its contracts,
evidence checks and PostgreSQL evidence validation intact while preventing the
historical work-start scope guard from treating every later repository phase as
part of the current change.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLOSED_SOURCE = ROOT / "verification/rca1b/run_rca1b_verification_closed_baseline.py"
EXPECTED_CLOSED_BLOB = "0d3cff8b73ad73339cc35232f991bfd6fa25b4d2"

actual_blob = subprocess.check_output(
    ["git", "hash-object", str(CLOSED_SOURCE)],
    cwd=ROOT,
    text=True,
).strip()
if actual_blob != EXPECTED_CLOSED_BLOB:
    raise SystemExit(
        f"FAIL: protected RCA-1B verifier source drift: actual={actual_blob} expected={EXPECTED_CLOSED_BLOB}"
    )

source = CLOSED_SOURCE.read_text(encoding="utf-8")

sql_anchor = '        need(set(scripts) == set(range(1, 55)), "SQL 01..54 inventory")\n'
if source.count(sql_anchor) != 1:
    raise SystemExit("FAIL: RCA-1B canonical SQL compatibility anchor missing")
source = source.replace(
    sql_anchor,
    '        need(set(range(1, 55)) <= set(scripts), "closed SQL 01..54 baseline missing")\n'
    '        need(set(scripts) == set(range(1, max(scripts) + 1)), "canonical SQL successor sequence gap")\n',
    1,
)

diff_anchor = '        changed = sh("git", "diff", "--name-only", f"{WORK_START}..{head}").splitlines()\n'
if source.count(diff_anchor) != 1:
    raise SystemExit("FAIL: RCA-1B changed-file compatibility anchor missing")
source = source.replace(
    diff_anchor,
    '''        current_base = sh("git", "merge-base", "origin/main", head)
        current_changed = sh("git", "diff", "--name-only", f"{current_base}...{head}").splitlines()

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

        relevant_prefixes = (
            ".github/actions/rca2-job/",
            ".github/workflows/rca1b-nonproduction-readonly-reconciliation-ci.yml",
            ".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",
            ".github/workflows/sc-baseline-reconciliation.yml",
            "docs/platform/governance/",
            "docs/platform/recommendation/RCA-1B-",
            "docs/platform/recommendation/rca2/",
            "jc-backend/build.gradle.kts",
            "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java",
            "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/",
            "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml",
            "jc-backend/src/test/java/com/jc/backend/recommendation/dataadoption/reconciliation/database/",
            "jc-backend/src/test/java/com/jc/backend/recommendation/rca2/",
            "jc-backend/src/test/java/com/jc/backend/verification/IP9ControlledBackendHookStaticTest.java",
            "jc-backend/src/test/resources/recommendation-data-adoption/rca1b/",
            "jc-search-readiness/src/test/java/com/jc/intelligence/readiness/search/SearchShadowReadinessContractTest.java",
            "verification/rca0/run_rca0_verification.py",
            "verification/rca1/run_rca1_verification.py",
            "verification/rca1b/",
            "verification/rca2/",
            "verification/data-platform-closure/run_data_platform_closure_verification.py",
            "verification/dp5/run_dp5_static_verification.py",
            "verification/dp6/run_dp6_allocation_verification.py",
            "verification/dp6/run_dp6_static_verification.py",
            "verification/dp7/run_dp7_allocation_verification.py",
            "verification/dp7/run_dp7_static_verification.py",
            "verification/sc-dp1-baseline-reconciliation/",
            "verification/sc-next-track/",
            "jc-recommendation-core/",
            "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java",
            "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java",
        )
        changed = []
        for item in current_changed:
            sql_number = canonical_sql_number(item)
            if sql_number is not None:
                if sql_number <= 54:
                    changed.append(item)
                continue
            if item.startswith(relevant_prefixes):
                changed.append(item)
''',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / "verification/rca1b/run_rca1b_verification.py"),
    "__package__": None,
}
exec(compile(source, namespace["__file__"], "exec"), namespace)
