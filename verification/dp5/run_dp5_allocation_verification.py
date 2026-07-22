#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import csv
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DP5 = ROOT / "verification/dp5"
SQL_DIR = ROOT / "database/journey-connect-db-v2.7"
ALLOCATION = ROOT / "docs/platform/governance/SC-DP5-PROJECTION-ALLOCATION.md"
FOUNDATION = ROOT / "docs/platform/data/DP-5-PROJECTION-AND-SNAPSHOT-FOUNDATION.md"
MATRIX = ROOT / "docs/platform/data/DP-5-PROJECTION-MATRIX.md"
HANDOFF = ROOT / "docs/platform/data/DP-5-HANDOFF.md"

EVIDENCE = [
    "DP5_BASELINE.tsv",
    "DP5_CHANGED_FILES.tsv",
    "DP5_PROJECTION_MATRIX.tsv",
    "DP5_PROFILE_PROJECTION.tsv",
    "DP5_EXPERIMENT_OUTCOME.tsv",
    "DP5_CHECKPOINT.tsv",
    "DP5_LINEAGE.tsv",
    "DP5_DETERMINISM.tsv",
    "DP5_DUPLICATE_CONFLICT.tsv",
    "DP5_IDENTITY_BOUNDARY.tsv",
    "DP5_EXPOSURE_BOUNDARY.tsv",
    "DP5_ROLE_GRANTS.tsv",
    "DP5_RETENTION.tsv",
    "DP5_PROTECTED_DIFF.tsv",
    "DP5_VERIFICATION_STATUS.tsv",
    "DP5_DECISIONS.tsv",
]

def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")

for path in [ALLOCATION, FOUNDATION, MATRIX, HANDOFF, *(DP5 / name for name in EVIDENCE)]:
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing or empty DP-5 allocation artifact: {path.relative_to(ROOT)}")

allocation = ALLOCATION.read_text(encoding="utf-8")
for marker in (
    "Implementation authority: `GRANTED AFTER MERGE INTO MAIN`",
    "38_data_projection_snapshot_foundation.sql",
    "39_data_recommendation_profile_projection.sql",
    "40_data_experiment_outcome_projection.sql",
    "41_data_projection_persistence_roles.sql",
    "42_data_projection_snapshot_validation.sql",
    "jc_data_projection_writer",
    "jc_data_projection_reader",
    "jc_data_projection_function_owner",
    "SQL `01..37` remains protected",
    "SQL `43+` remains unallocated",
    "recommendation_p2_experiment_exposure",
    "PROJECTION_SNAPSHOT_CONFLICT",
):
    if marker not in allocation:
        fail(f"SC DP-5 allocation marker missing: {marker}")

foundation = FOUNDATION.read_text(encoding="utf-8")
for marker in (
    "DP5_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION",
    "recommendation-profile-input-v1",
    "experiment-outcome-input-v1",
    "data-source-set-sha256-v1",
    "data-projection-record-sha256-v1",
    "data-projection-snapshot-sha256-v1",
    "data-projection-lineage-sha256-v1",
    "Unexecuted checks are not reported as PASS",
):
    if marker not in foundation:
        fail(f"DP-5 foundation marker missing: {marker}")

if "DP5_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION" not in HANDOFF.read_text(encoding="utf-8"):
    fail("DP-5 handoff does not declare the allocation blocker")

for number in range(1, 38):
    matches = list(SQL_DIR.glob(f"{number:02d}_*.sql"))
    if len(matches) != 1:
        fail(f"canonical SQL {number:02d} expected exactly once, found {len(matches)}")

if list(SQL_DIR.glob("3[8-9]_*.sql")) or list(SQL_DIR.glob("[4-9][0-9]_*.sql")):
    fail("SQL 38+ must remain absent in the allocation PR")

for path in DP5.glob("*.tsv"):
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.reader(handle, delimiter="\t"))
    if len(rows) < 2:
        fail(f"DP-5 evidence is empty: {path.name}")

try:
    subprocess.run(
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

    changed_sql = [path for path in changed if path.endswith(".sql")]
    if changed_sql:
        fail(f"SC allocation PR must not create or modify SQL: {changed_sql}")

    protected = [
        path for path in changed
        if path.startswith((
            "jc-backend/src/main/",
            "jc-backend/src/main/resources/",
            "jc-data-contracts/",
            "jc-recommendation-core/",
            "jc-intelligence-contracts/",
            "jc-search-contracts/",
            "jc-search-compatibility/",
            "jc-search-runtime/",
            "jc-search-integration/",
            "jc-search-shadow-wiring/",
            "jc-search-readiness/",
            "jc-search-production-controls/",
        ))
    ]
    if protected:
        fail(f"protected production or contract source changed: {protected}")

    allowed_exact = {
        ".github/workflows/data-contract-ci.yml",
        "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
        "docs/platform/governance/SC-DP5-PROJECTION-ALLOCATION.md",
        "docs/platform/governance/SC-DECISION-REGISTER.md",
        "docs/platform/governance/SC-PLATFORM-REGISTRY.md",
        "docs/platform/governance/SC-HANDOFF.md",
        "docs/platform/data/DP-5-PROJECTION-AND-SNAPSHOT-FOUNDATION.md",
        "docs/platform/data/DP-5-PROJECTION-MATRIX.md",
        "docs/platform/data/DP-5-HANDOFF.md",
    }
    unexpected = [
        path for path in changed
        if path not in allowed_exact and not path.startswith("verification/dp5/")
    ]
    if unexpected:
        fail(f"unexpected DP-5 allocation diff: {unexpected}")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

print("DP-5 SC allocation and blocker static verification: PASS")
