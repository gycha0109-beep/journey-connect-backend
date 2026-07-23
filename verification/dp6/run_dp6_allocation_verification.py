#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import csv
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DP6 = ROOT / "verification/dp6"
SQL_DIR = ROOT / "database/journey-connect-db-v2.7"
QUALITY_JAVA = ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/quality"
ALLOCATION = ROOT / "docs/platform/governance/SC-DP6-QUALITY-ALLOCATION.md"
DOC = ROOT / "docs/platform/data/DP-6-DATA-QUALITY-AND-LINEAGE-VALIDATION-HARDENING.md"
MATRIX = ROOT / "docs/platform/data/DP-6-QUALITY-MATRIX.md"
HANDOFF = ROOT / "docs/platform/data/DP-6-HANDOFF.md"
DECISIONS = ROOT / "docs/platform/governance/SC-DECISION-REGISTER.md"
REGISTRY = ROOT / "docs/platform/governance/SC-PLATFORM-REGISTRY.md"
SC_HANDOFF = ROOT / "docs/platform/governance/SC-HANDOFF.md"
TEST = ROOT / "jc-data-contracts/src/test/java/com/jc/data/contract/Dp6DataQualityContractTest.java"
GOLDEN = ROOT / "jc-data-contracts/src/test/resources/dp6-quality-golden-v1.tsv"

EVIDENCE = [
    "DP6_BASELINE.tsv", "DP6_CHANGED_FILES.tsv", "DP6_QUALITY_MATRIX.tsv",
    "DP6_SOURCE_COMPLETENESS.tsv", "DP6_PROJECTION_COMPLETENESS.tsv",
    "DP6_SNAPSHOT_CONSISTENCY.tsv", "DP6_LINEAGE_INTEGRITY.tsv",
    "DP6_IDENTITY_INTEGRITY.tsv", "DP6_EXPOSURE_INTEGRITY.tsv",
    "DP6_REBUILD_COMPARISON.tsv", "DP6_LATE_ARRIVAL.tsv", "DP6_QUALITY_METRICS.tsv",
    "DP6_VERDICTS.tsv", "DP6_DUPLICATE_CONFLICT.tsv", "DP6_CONCURRENCY.tsv",
    "DP6_ROLE_GRANTS.tsv", "DP6_RETENTION.tsv", "DP6_PROTECTED_DIFF.tsv",
    "DP6_VERIFICATION_STATUS.tsv", "DP6_DECISIONS.tsv",
]
DP6_SQL_FILES = {
    "database/journey-connect-db-v2.7/43_data_quality_validation_foundation.sql",
    "database/journey-connect-db-v2.7/44_data_quality_metrics_and_verdict.sql",
    "database/journey-connect-db-v2.7/45_data_quality_persistence_and_roles.sql",
    "database/journey-connect-db-v2.7/46_data_quality_rebuild_and_safe_views.sql",
    "database/journey-connect-db-v2.7/47_data_quality_validation.sql",
}
QUALITY_REQUIRED = {
    "DataQualityValidationDefinition.java", "DataQualityValidationRun.java",
    "DataQualityValidationScope.java", "DataQualityValidationStatus.java",
    "DataQualityCheckResult.java", "DataQualityCheckStatus.java", "DataQualitySeverity.java",
    "DataQualityMetric.java", "DataQualityThreshold.java", "DataQualityPolicy.java",
    "DataQualityAnomaly.java", "SnapshotQualityVerdict.java", "SnapshotQualityStatus.java",
    "SnapshotQualityVerdictEvaluator.java", "RebuildComparison.java", "LateArrivalObservation.java",
    "DataQualityFailure.java", "DataQualityPersistenceOutcome.java", "SourceCompletenessValidator.java",
    "ProjectionCompletenessValidator.java", "SnapshotConsistencyValidator.java",
    "LineageIntegrityValidator.java", "IdentityIntegrityValidator.java", "ExposureIntegrityValidator.java",
    "DeterministicRebuildValidator.java", "FullSnapshotQualityValidator.java", "DataQualityFingerprints.java",
}


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in [ALLOCATION, DOC, MATRIX, HANDOFF, DECISIONS, REGISTRY, SC_HANDOFF,
             *(DP6 / name for name in EVIDENCE)]:
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing or empty DP-6 artifact: {path.relative_to(ROOT)}")

allocation = ALLOCATION.read_text(encoding="utf-8")
for marker in (
    "APPROVED / MERGED", "Implementation authority: `GRANTED`",
    "c0f6b5dc8cc7089412a100989109b61315c062d0",
    "43_data_quality_validation_foundation.sql", "44_data_quality_metrics_and_verdict.sql",
    "45_data_quality_persistence_and_roles.sql", "46_data_quality_rebuild_and_safe_views.sql",
    "47_data_quality_validation.sql", "jc_data_quality_writer", "jc_data_quality_reader",
    "jc_data_quality_function_owner", "data-quality-policy-v1", "QUALITY_VERDICT_CONFLICT",
    "SQL `01..42` remains protected and unchanged", "SQL `48+` remains unallocated",
):
    if marker not in allocation:
        fail(f"allocation marker missing: {marker}")

implemented = all((ROOT / path).is_file() for path in DP6_SQL_FILES) or (
    QUALITY_JAVA.is_dir() and any(QUALITY_JAVA.glob("*.java"))
)
if implemented:
    for number in range(1, 48):
        matches = list(SQL_DIR.glob(f"{number:02d}_*.sql"))
        if len(matches) != 1:
            fail(f"canonical SQL {number:02d} expected exactly once, found {len(matches)}")
    if list(SQL_DIR.glob("4[8-9]_*.sql")) or list(SQL_DIR.glob("[5-9][0-9]_*.sql")):
        fail("SQL 48+ remains unallocated")
    missing_java = sorted(name for name in QUALITY_REQUIRED if not (QUALITY_JAVA / name).is_file())
    if missing_java:
        fail(f"missing DP-6 Java implementation: {missing_java}")
    for path in [TEST, GOLDEN, DP6 / "run_dp6_static_verification.py", DP6 / "run_dp6_concurrency.sh"]:
        if not path.is_file() or not path.read_text(encoding="utf-8").strip():
            fail(f"missing implementation verification artifact: {path.relative_to(ROOT)}")
else:
    for number in range(1, 43):
        matches = list(SQL_DIR.glob(f"{number:02d}_*.sql"))
        if len(matches) != 1:
            fail(f"canonical SQL {number:02d} expected exactly once, found {len(matches)}")
    if list(SQL_DIR.glob("4[3-9]_*.sql")) or list(SQL_DIR.glob("[5-9][0-9]_*.sql")):
        fail("SQL 43+ must remain absent before implementation")

foundation = DOC.read_text(encoding="utf-8")
for marker in (
    "SourceCompletenessValidator", "ProjectionCompletenessValidator", "SnapshotConsistencyValidator",
    "LineageIntegrityValidator", "IdentityIntegrityValidator", "ExposureIntegrityValidator",
    "DeterministicRebuildValidator", "data-quality-validation-input-sha256-v1",
    "data-quality-verdict-sha256-v1", "subject:<opaque-id> != user:<numeric-id>",
    "recommendation_p2_experiment_exposure", "NOT_APPLICABLE", "INCONCLUSIVE",
):
    if marker not in foundation:
        fail(f"foundation marker missing: {marker}")

matrix = MATRIX.read_text(encoding="utf-8")
for marker in (
    "SOURCE_COMPLETENESS", "PROJECTION_COMPLETENESS", "SNAPSHOT_CONSISTENCY",
    "LINEAGE_INTEGRITY", "IDENTITY_INTEGRITY", "EXPOSURE_INTEGRITY",
    "DETERMINISTIC_REBUILD", "source_count_mismatch", "lineage_orphan",
    "general_exposure_used_as_p2", "non_deterministic_output",
):
    if marker not in matrix:
        fail(f"quality matrix marker missing: {marker}")

for name in EVIDENCE:
    with (DP6 / name).open(encoding="utf-8", newline="") as handle:
        rows = list(csv.reader(handle, delimiter="\t"))
    if len(rows) < 2:
        fail(f"empty evidence: {name}")

try:
    subprocess.run(["git", "fetch", "origin", "main", "--depth=1"], cwd=ROOT, check=False,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    changed = subprocess.run(["git", "diff", "--name-only", "origin/main...HEAD"], cwd=ROOT,
                             check=True, text=True, capture_output=True).stdout.splitlines()
    changed_sql = {path for path in changed if path.endswith(".sql")}
    if implemented and changed_sql != DP6_SQL_FILES:
        fail(f"DP-6 implementation SQL diff must be exactly 43..47: {sorted(changed_sql)}")
    if not implemented and changed_sql:
        fail(f"allocation-only phase cannot change SQL: {sorted(changed_sql)}")
    protected_sql = [path for path in changed_sql if int(Path(path).name[:2]) <= 42]
    if protected_sql:
        fail(f"protected SQL 01..42 changed: {protected_sql}")
    forbidden_prefixes = (
        "jc-backend/src/main/", "jc-backend/src/main/resources/", "jc-recommendation-core/",
        "jc-intelligence-contracts/", "jc-search-contracts/", "jc-search-compatibility/",
        "jc-search-runtime/", "jc-search-integration/", "jc-search-shadow-wiring/",
        "jc-search-readiness/", "jc-search-production-controls/",
    )
    protected = [path for path in changed if path.startswith(forbidden_prefixes)]
    if protected:
        fail(f"protected production/Recommendation/Search source changed: {protected}")
    temporary = [path for path in changed if path in {
        ".github/workflows/dp6-ci-diagnostic.yml", ".github/workflows/dp6-workspace-export.yml"
    } or "/.transport/" in path or "DIAGNOSTIC" in path]
    if temporary:
        fail(f"temporary DP-6 workflow or transport artifact remains: {temporary}")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

print("DP-6 merged allocation and implementation boundary verification: PASS")
