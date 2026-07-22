#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import csv
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DP6 = ROOT / "verification/dp6"
SQL_DIR = ROOT / "database/journey-connect-db-v2.7"
ALLOCATION = ROOT / "docs/platform/governance/SC-DP6-QUALITY-ALLOCATION.md"
DOC = ROOT / "docs/platform/data/DP-6-DATA-QUALITY-AND-LINEAGE-VALIDATION-HARDENING.md"
MATRIX = ROOT / "docs/platform/data/DP-6-QUALITY-MATRIX.md"
HANDOFF = ROOT / "docs/platform/data/DP-6-HANDOFF.md"
DECISIONS = ROOT / "docs/platform/governance/SC-DECISION-REGISTER.md"
REGISTRY = ROOT / "docs/platform/governance/SC-PLATFORM-REGISTRY.md"
SC_HANDOFF = ROOT / "docs/platform/governance/SC-HANDOFF.md"

EVIDENCE = [
    "DP6_BASELINE.tsv",
    "DP6_CHANGED_FILES.tsv",
    "DP6_QUALITY_MATRIX.tsv",
    "DP6_SOURCE_COMPLETENESS.tsv",
    "DP6_PROJECTION_COMPLETENESS.tsv",
    "DP6_SNAPSHOT_CONSISTENCY.tsv",
    "DP6_LINEAGE_INTEGRITY.tsv",
    "DP6_IDENTITY_INTEGRITY.tsv",
    "DP6_EXPOSURE_INTEGRITY.tsv",
    "DP6_REBUILD_COMPARISON.tsv",
    "DP6_LATE_ARRIVAL.tsv",
    "DP6_QUALITY_METRICS.tsv",
    "DP6_VERDICTS.tsv",
    "DP6_DUPLICATE_CONFLICT.tsv",
    "DP6_CONCURRENCY.tsv",
    "DP6_ROLE_GRANTS.tsv",
    "DP6_RETENTION.tsv",
    "DP6_PROTECTED_DIFF.tsv",
    "DP6_VERIFICATION_STATUS.tsv",
    "DP6_DECISIONS.tsv",
]

ALLOWED_DIFF = (
    "docs/platform/governance/SC-DP6-QUALITY-ALLOCATION.md",
    "docs/platform/governance/SC-DECISION-REGISTER.md",
    "docs/platform/governance/SC-PLATFORM-REGISTRY.md",
    "docs/platform/governance/SC-HANDOFF.md",
    "docs/platform/data/DP-6-",
    "verification/dp6/",
    "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
    ".github/workflows/dp6-allocation-ci.yml",
)


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in [ALLOCATION, DOC, MATRIX, HANDOFF, DECISIONS, REGISTRY, SC_HANDOFF,
             *(DP6 / name for name in EVIDENCE)]:
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing or empty DP-6 allocation artifact: {path.relative_to(ROOT)}")

for number in range(1, 43):
    matches = list(SQL_DIR.glob(f"{number:02d}_*.sql"))
    if len(matches) != 1:
        fail(f"canonical SQL {number:02d} expected exactly once, found {len(matches)}")
if list(SQL_DIR.glob("4[3-9]_*.sql")) or list(SQL_DIR.glob("[5-9][0-9]_*.sql")):
    fail("SQL 43+ must remain absent until the allocation is merged")

allocation = ALLOCATION.read_text(encoding="utf-8")
for marker in (
    "PROPOSED / NON-AUTHORITATIVE UNTIL MERGED",
    "BLOCKED UNTIL THIS ALLOCATION IS MERGED",
    "05a25771cd99d87891504fc00890ab918b970acf",
    "43_data_quality_validation_foundation.sql",
    "44_data_quality_metrics_and_verdict.sql",
    "45_data_quality_persistence_and_roles.sql",
    "46_data_quality_rebuild_and_safe_views.sql",
    "47_data_quality_validation.sql",
    "jc_data_quality_writer",
    "jc_data_quality_reader",
    "jc_data_quality_function_owner",
    "data-quality-policy-v1",
    "QUALITY_VERDICT_CONFLICT",
    "SQL `01..42` remains protected and unchanged",
    "SQL `48+` remains unallocated",
):
    if marker not in allocation:
        fail(f"allocation marker missing: {marker}")

foundation = DOC.read_text(encoding="utf-8")
for marker in (
    "DP6_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION",
    "SourceCompletenessValidator",
    "ProjectionCompletenessValidator",
    "SnapshotConsistencyValidator",
    "LineageIntegrityValidator",
    "IdentityIntegrityValidator",
    "ExposureIntegrityValidator",
    "DeterministicRebuildValidator",
    "data-quality-validation-input-sha256-v1",
    "data-quality-verdict-sha256-v1",
    "subject:<opaque-id> != user:<numeric-id>",
    "recommendation_p2_experiment_exposure",
    "NOT_APPLICABLE",
    "INCONCLUSIVE",
):
    if marker not in foundation:
        fail(f"foundation marker missing: {marker}")

matrix = MATRIX.read_text(encoding="utf-8")
for marker in (
    "SOURCE_COMPLETENESS",
    "PROJECTION_COMPLETENESS",
    "SNAPSHOT_CONSISTENCY",
    "LINEAGE_INTEGRITY",
    "IDENTITY_INTEGRITY",
    "EXPOSURE_INTEGRITY",
    "DETERMINISTIC_REBUILD",
    "source_count_mismatch",
    "lineage_orphan",
    "general_exposure_used_as_p2",
    "non_deterministic_output",
):
    if marker not in matrix:
        fail(f"quality matrix marker missing: {marker}")

handoff = HANDOFF.read_text(encoding="utf-8")
for marker in (
    "DP6_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION",
    "SQL `43+`: unallocated and absent",
    "No unexecuted runtime validation is reported as PASS",
    "allocation merge commit becomes the authoritative DP-6 implementation base",
):
    if marker not in handoff:
        fail(f"handoff marker missing: {marker}")

for name in EVIDENCE:
    path = DP6 / name
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.reader(handle, delimiter="\t"))
    if len(rows) < 2:
        fail(f"empty evidence: {name}")

status_rows: dict[str, str] = {}
with (DP6 / "DP6_VERIFICATION_STATUS.tsv").open(encoding="utf-8", newline="") as handle:
    for row in list(csv.reader(handle, delimiter="\t"))[1:]:
        if len(row) >= 2:
            status_rows[row[0]] = row[1]
for runtime_check in (
    "java_runtime_validation",
    "postgresql_15_dp6_runtime",
    "postgresql_18_dp6_runtime",
    "concurrency_runtime",
    "role_grant_runtime",
):
    if status_rows.get(runtime_check) != "NOT_EXECUTED":
        fail(f"unexecuted runtime check must remain NOT_EXECUTED: {runtime_check}")
if status_rows.get("final_verdict") != "DP6_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION":
    fail("allocation-stage final verdict mismatch")

if (ROOT / "jc-data-quality").exists():
    fail("jc-data-quality module is forbidden before allocation merge")
quality_java = ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/quality"
if quality_java.exists() and any(quality_java.rglob("*.java")):
    fail("DP-6 Java implementation is forbidden before allocation merge")

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
    for path in filter(None, changed):
        if not any(path == prefix or path.startswith(prefix) for prefix in ALLOWED_DIFF):
            fail(f"unexpected allocation-stage changed file: {path}")
    changed_sql = [path for path in changed if path.endswith(".sql")]
    if changed_sql:
        fail(f"no SQL may change before allocation merge: {changed_sql}")
    forbidden_prefixes = (
        "jc-data-contracts/",
        "jc-backend/src/main/",
        "jc-backend/src/main/resources/",
        "jc-recommendation-core/",
        "jc-intelligence-contracts/",
        "jc-search-contracts/",
        "jc-search-compatibility/",
        "jc-search-runtime/",
        "jc-search-integration/",
        "jc-search-shadow-wiring/",
        "jc-search-readiness/",
        "jc-search-production-controls/",
    )
    protected = [path for path in changed if path.startswith(forbidden_prefixes)]
    if protected:
        fail(f"protected source changed: {protected}")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

print("DP-6 SC allocation static verification: PASS")
