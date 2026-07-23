#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import csv
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DP5 = ROOT / "verification/dp5"
SQL_DIR = ROOT / "database/journey-connect-db-v2.7"
JAVA = ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/projection"
TEST = ROOT / "jc-data-contracts/src/test/java/com/jc/data/contract/Dp5ProjectionContractTest.java"
GOLDEN = ROOT / "jc-data-contracts/src/test/resources/dp5-projection-golden-v1.tsv"
DOC = ROOT / "docs/platform/data/DP-5-PROJECTION-AND-SNAPSHOT-FOUNDATION.md"
MATRIX = ROOT / "docs/platform/data/DP-5-PROJECTION-MATRIX.md"
HANDOFF = ROOT / "docs/platform/data/DP-5-HANDOFF.md"
ALLOCATION = ROOT / "docs/platform/governance/SC-DP5-PROJECTION-ALLOCATION.md"
DP6_ALLOCATION = ROOT / "docs/platform/governance/SC-DP6-QUALITY-ALLOCATION.md"
DP7_ALLOCATION = ROOT / "docs/platform/governance/SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md"

DP5_SQL_FILES = {
    "database/journey-connect-db-v2.7/38_data_projection_snapshot_foundation.sql",
    "database/journey-connect-db-v2.7/39_data_recommendation_profile_projection.sql",
    "database/journey-connect-db-v2.7/40_data_experiment_outcome_projection.sql",
    "database/journey-connect-db-v2.7/41_data_projection_persistence_roles.sql",
    "database/journey-connect-db-v2.7/42_data_projection_snapshot_validation.sql",
}
DP6_SQL_FILES = {
    "database/journey-connect-db-v2.7/43_data_quality_validation_foundation.sql",
    "database/journey-connect-db-v2.7/44_data_quality_metrics_and_verdict.sql",
    "database/journey-connect-db-v2.7/45_data_quality_persistence_and_roles.sql",
    "database/journey-connect-db-v2.7/46_data_quality_rebuild_and_safe_views.sql",
    "database/journey-connect-db-v2.7/47_data_quality_validation.sql",
}
DP7_SQL_FILES = {
    "database/journey-connect-db-v2.7/48_cross_track_integration_validation_foundation.sql",
    "database/journey-connect-db-v2.7/49_cross_track_contract_mapping_and_boundary_evidence.sql",
    "database/journey-connect-db-v2.7/50_cross_track_integration_verdict_and_conflict.sql",
    "database/journey-connect-db-v2.7/51_cross_track_integration_persistence_roles_and_safe_view.sql",
    "database/journey-connect-db-v2.7/52_cross_track_integration_validation.sql",
}
EVIDENCE = [
    "DP5_BASELINE.tsv", "DP5_CHANGED_FILES.tsv", "DP5_DB_OBJECTS.tsv",
    "DP5_PROJECTION_MATRIX.tsv", "DP5_PROFILE_PROJECTION.tsv",
    "DP5_EXPERIMENT_OUTCOME.tsv", "DP5_CHECKPOINT.tsv", "DP5_SNAPSHOT.tsv",
    "DP5_LINEAGE.tsv", "DP5_DETERMINISM.tsv", "DP5_DUPLICATE_CONFLICT.tsv",
    "DP5_CONCURRENCY.tsv", "DP5_IDENTITY_BOUNDARY.tsv", "DP5_EXPOSURE_BOUNDARY.tsv",
    "DP5_ROLE_GRANTS.tsv", "DP5_RETENTION.tsv", "DP5_PROTECTED_DIFF.tsv",
    "DP5_VERIFICATION_STATUS.tsv", "DP5_DECISIONS.tsv",
]


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in [DOC, MATRIX, HANDOFF, ALLOCATION, TEST, GOLDEN,
             DP5 / "run_dp5_concurrency.sh", *(DP5 / name for name in EVIDENCE)]:
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing or empty DP-5 artifact: {path.relative_to(ROOT)}")

# Later approved stages may add forward SQL. DP-5 SQL 38..42 remains exact and protected.
dp6_implemented = all((ROOT / path).is_file() for path in DP6_SQL_FILES)
dp7_implemented = all((ROOT / path).is_file() for path in DP7_SQL_FILES)
max_sql = 52 if dp7_implemented else 47 if dp6_implemented else 42
for number in range(1, max_sql + 1):
    matches = list(SQL_DIR.glob(f"{number:02d}_*.sql"))
    if len(matches) != 1:
        fail(f"canonical SQL {number:02d} expected exactly once, found {len(matches)}")
if dp6_implemented:
    if not DP6_ALLOCATION.is_file():
        fail("DP-6 implementation exists without SC allocation")
    dp6_allocation = DP6_ALLOCATION.read_text(encoding="utf-8")
    for marker in (
        "APPROVED / MERGED", "Implementation authority: `GRANTED`",
        "SQL `01..42` remains protected and unchanged", "SQL `48+` remains unallocated",
    ):
        if marker not in dp6_allocation:
            fail(f"DP-6 allocation marker missing: {marker}")
if dp7_implemented:
    if not DP7_ALLOCATION.is_file():
        fail("DP-7 implementation exists without SC allocation")
    dp7_allocation = DP7_ALLOCATION.read_text(encoding="utf-8")
    for marker in (
        "APPROVED / MERGED", "Implementation authority: `GRANTED`",
        "SQL `01..47` remains protected", "SQL `53+` remains unallocated",
    ):
        if marker not in dp7_allocation:
            fail(f"DP-7 allocation marker missing: {marker}")
    successor_sql = {
        path.relative_to(ROOT).as_posix()
        for path in SQL_DIR.glob("*.sql")
        if path.name[:2].isdigit() and int(path.name[:2]) >= 48
    }
    if successor_sql != DP7_SQL_FILES:
        fail(f"unexpected DP-7 successor SQL: {sorted(successor_sql)}")
elif dp6_implemented:
    if list(SQL_DIR.glob("4[8-9]_*.sql")) or list(SQL_DIR.glob("[5-9][0-9]_*.sql")):
        fail("SQL 48+ remains unallocated before DP-7 implementation authority")
else:
    if list(SQL_DIR.glob("4[3-9]_*.sql")) or list(SQL_DIR.glob("[5-9][0-9]_*.sql")):
        fail("SQL 43+ is unallocated before DP-6 implementation authority")

allocation = ALLOCATION.read_text(encoding="utf-8")
for marker in (
    "38_data_projection_snapshot_foundation.sql",
    "39_data_recommendation_profile_projection.sql",
    "40_data_experiment_outcome_projection.sql",
    "41_data_projection_persistence_roles.sql",
    "42_data_projection_snapshot_validation.sql",
    "jc_data_projection_writer", "jc_data_projection_reader",
    "jc_data_projection_function_owner", "recommendation_p2_experiment_exposure",
):
    if marker not in allocation:
        fail(f"allocation marker missing: {marker}")

sql38 = (SQL_DIR / "38_data_projection_snapshot_foundation.sql").read_text(encoding="utf-8")
sql39 = (SQL_DIR / "39_data_recommendation_profile_projection.sql").read_text(encoding="utf-8")
sql40 = (SQL_DIR / "40_data_experiment_outcome_projection.sql").read_text(encoding="utf-8")
sql41 = (SQL_DIR / "41_data_projection_persistence_roles.sql").read_text(encoding="utf-8")
sql42 = (SQL_DIR / "42_data_projection_snapshot_validation.sql").read_text(encoding="utf-8")
for marker in (
    "data_source_checkpoint_v1", "data_projection_run_v1",
    "data_projection_run_status_evidence_v1", "data_projection_snapshot_v1",
    "data_projection_lineage_v1", "data_projection_validation_evidence_v1",
    "data_projection_conflict_observation_v1", "projection_evidence_90d",
    "prevent_data_event_append_only_mutation_v1",
):
    if marker not in sql38:
        fail(f"SQL 38 marker missing: {marker}")
for marker in ("data_recommendation_profile_input_projection_v1", "activity_window_days IN (7,30,90)"):
    if marker not in sql39:
        fail(f"SQL 39 marker missing: {marker}")
for marker in ("data_experiment_outcome_input_projection_v1", "recommendation_p2_experiment_exposure", "604800"):
    if marker not in sql40:
        fail(f"SQL 40 marker missing: {marker}")
for marker in (
    "persist_data_source_checkpoint_v1", "persist_data_projection_snapshot_v1",
    "pg_advisory_xact_lock", "PROJECTION_SNAPSHOT_CONFLICT", "SECURITY DEFINER",
    "SET search_path = pg_catalog, public, pg_temp", "data_projection_safe_metrics_v1",
    "jc_data_projection_writer", "jc_data_projection_reader", "jc_data_projection_function_owner",
    "REVOKE ALL ON FUNCTION", "ALTER FUNCTION", "OWNER TO jc_data_projection_function_owner",
):
    if marker not in sql41:
        fail(f"SQL 41 marker missing: {marker}")
for marker in (
    "checkpoint CONFLICT", "profile snapshot NEW", "outcome snapshot NEW",
    "snapshot CONFLICT", "UPDATE unexpectedly succeeded", "DELETE unexpectedly succeeded",
    "function owner role is unsafe", "automatic projection purge unexpectedly exists", "ROLLBACK;",
):
    if marker not in sql42:
        fail(f"SQL 42 validation marker missing: {marker}")

combined = "\n".join((sql38, sql39, sql40, sql41, sql42))
for forbidden in (
    "INSERT INTO public.recommendation_" + "profile_snapshot",
    "UPDATE public.recommendation_", "DELETE FROM public.recommendation_",
    "UPDATE public.data_platform_event_v1", "DELETE FROM public.data_platform_event_v1",
    "UPDATE public.data_recommendation_adapter_", "DELETE FROM public.data_recommendation_adapter_",
    "CREATE EXTENSION", "TRUNCATE public.data_",
):
    if forbidden in "\n".join((sql38, sql39, sql40, sql41)):
        fail(f"protected authority mutation found: {forbidden}")
if re.search(r"(?i)\b(CREATE|ALTER)\s+ROLE\s+(postgres|jc_security_owner)\b", combined):
    fail("broad owner role mutation is forbidden")

java_required = {
    "ProjectionDefinition.java", "ProjectionRun.java", "ProjectionRunStatus.java",
    "SourceCheckpoint.java", "ProjectionRecord.java", "ProjectionSnapshot.java",
    "ProjectionLineage.java", "ProjectionResult.java", "ProjectionFailure.java",
    "ProjectionPersistenceOutcome.java", "RecommendationProfileInputProjection.java",
    "RecommendationProfileProjectionEngine.java", "ExperimentOutcomeInputProjection.java",
    "ExperimentOutcomeProjectionEngine.java", "ProjectionFingerprints.java",
}
missing_java = [name for name in sorted(java_required) if not (JAVA / name).is_file()]
if missing_java:
    fail(f"missing Java projection contracts: {missing_java}")
java_text = "\n".join(path.read_text(encoding="utf-8") for path in JAVA.glob("*.java"))
for forbidden in ("org.springframework", "jakarta.persistence", "java.sql", "System.currentTimeMillis",
                  "Instant.now(", "UUID.randomUUID", "Math.random"):
    if forbidden in java_text:
        fail(f"forbidden Java dependency or nondeterminism: {forbidden}")
for marker in (
    "data-source-set-sha256-v1", "data-projection-record-sha256-v1",
    "data-projection-snapshot-sha256-v1", "data-projection-lineage-sha256-v1",
    "recommendation_p2_experiment_exposure", "OUTCOME_WINDOW_VIOLATION",
):
    if marker not in java_text:
        fail(f"Java contract marker missing: {marker}")

for path in DP5.glob("*.tsv"):
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.reader(handle, delimiter="\t"))
    if len(rows) < 2:
        fail(f"empty evidence: {path.name}")

try:
    subprocess.run(["git", "fetch", "origin", "main", "--depth=1"], cwd=ROOT,
                   check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    changed = subprocess.run(["git", "diff", "--name-only", "origin/main...HEAD"], cwd=ROOT,
                             check=True, text=True, capture_output=True).stdout.splitlines()
    changed_sql = {
        path for path in changed
        if path.startswith("database/journey-connect-db-v2.7/") and path.endswith(".sql")
    }
    if changed_sql & DP5_SQL_FILES:
        fail(f"protected DP-5 SQL 38..42 changed: {sorted(changed_sql & DP5_SQL_FILES)}")
    allowed_successor_sql = DP6_SQL_FILES | DP7_SQL_FILES if dp6_implemented else set()
    unexpected_sql = changed_sql - allowed_successor_sql
    if unexpected_sql:
        fail(f"unexpected SQL after protected DP-5 range: {sorted(unexpected_sql)}")
    protected = [path for path in changed if path.startswith((
        "jc-recommendation-core/", "jc-intelligence-contracts/", "jc-search-contracts/",
        "jc-search-compatibility/", "jc-search-runtime/", "jc-search-integration/",
        "jc-search-shadow-wiring/", "jc-search-readiness/", "jc-search-production-controls/",
        "jc-backend/src/main/", "jc-backend/src/main/resources/",
    ))]
    if protected:
        fail(f"protected production/Recommendation/Search source changed: {protected}")
    old_sql = [path for path in changed_sql if int(Path(path).name[:2]) <= 42]
    if old_sql:
        fail(f"protected SQL 01..42 changed: {old_sql}")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

print("DP-5 protected implementation static verification: PASS")
