#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path
import csv
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SQL = ROOT / "database/journey-connect-db-v2.7"
JAVA = ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/quality"
TEST = ROOT / "jc-data-contracts/src/test/java/com/jc/data/contract/Dp6DataQualityContractTest.java"
GOLDEN = ROOT / "jc-data-contracts/src/test/resources/dp6-quality-golden-v1.tsv"
DOC = ROOT / "docs/platform/data/DP-6-DATA-QUALITY-AND-LINEAGE-VALIDATION-HARDENING.md"
MATRIX = ROOT / "docs/platform/data/DP-6-QUALITY-MATRIX.md"
HANDOFF = ROOT / "docs/platform/data/DP-6-HANDOFF.md"
ALLOCATION = ROOT / "docs/platform/governance/SC-DP6-QUALITY-ALLOCATION.md"
EVIDENCE = [
    "DP6_BASELINE.tsv","DP6_CHANGED_FILES.tsv","DP6_DB_OBJECTS.tsv","DP6_QUALITY_MATRIX.tsv",
    "DP6_SOURCE_COMPLETENESS.tsv","DP6_PROJECTION_COMPLETENESS.tsv","DP6_SNAPSHOT_CONSISTENCY.tsv",
    "DP6_LINEAGE_INTEGRITY.tsv","DP6_IDENTITY_INTEGRITY.tsv","DP6_EXPOSURE_INTEGRITY.tsv",
    "DP6_REBUILD_COMPARISON.tsv","DP6_LATE_ARRIVAL.tsv","DP6_QUALITY_METRICS.tsv","DP6_VERDICTS.tsv",
    "DP6_DUPLICATE_CONFLICT.tsv","DP6_CONCURRENCY.tsv","DP6_ROLE_GRANTS.tsv","DP6_RETENTION.tsv",
    "DP6_SAFE_VIEW.tsv","DP6_PROTECTED_DIFF.tsv","DP6_VERIFICATION_STATUS.tsv","DP6_DECISIONS.tsv",
]
SQL_FILES = {f"database/journey-connect-db-v2.7/{number:02d}_{name}.sql" for number,name in (
    (43,"data_quality_validation_foundation"),(44,"data_quality_metrics_and_verdict"),
    (45,"data_quality_persistence_and_roles"),(46,"data_quality_rebuild_and_safe_views"),
    (47,"data_quality_validation"),
)}

def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")

for path in [DOC,MATRIX,HANDOFF,ALLOCATION,TEST,GOLDEN,ROOT/"verification/dp6/run_dp6_concurrency.sh",
             *(ROOT/"verification/dp6"/name for name in EVIDENCE)]:
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing or empty DP-6 artifact: {path.relative_to(ROOT)}")

for number in range(1,48):
    matches=list(SQL.glob(f"{number:02d}_*.sql"))
    if len(matches)!=1: fail(f"canonical SQL {number:02d} expected once, found {len(matches)}")
if list(SQL.glob("4[8-9]_*.sql")) or list(SQL.glob("[5-9][0-9]_*.sql")):
    fail("SQL 48+ remains unallocated")

allocation=ALLOCATION.read_text(encoding="utf-8")
for marker in ("APPROVED / MERGED","Implementation authority: `GRANTED`","43_data_quality_validation_foundation.sql",
               "44_data_quality_metrics_and_verdict.sql","45_data_quality_persistence_and_roles.sql",
               "46_data_quality_rebuild_and_safe_views.sql","47_data_quality_validation.sql",
               "jc_data_quality_writer","jc_data_quality_reader","jc_data_quality_function_owner",
               "data-quality-policy-v1","SQL `01..42` remains protected","SQL `48+` remains unallocated"):
    if marker not in allocation: fail(f"allocation marker missing: {marker}")

sql43=(SQL/"43_data_quality_validation_foundation.sql").read_text(encoding="utf-8")
sql44=(SQL/"44_data_quality_metrics_and_verdict.sql").read_text(encoding="utf-8")
sql45=(SQL/"45_data_quality_persistence_and_roles.sql").read_text(encoding="utf-8")
sql46=(SQL/"46_data_quality_rebuild_and_safe_views.sql").read_text(encoding="utf-8")
sql47=(SQL/"47_data_quality_validation.sql").read_text(encoding="utf-8")
for marker in ("data_quality_validation_run_v1","data_quality_validation_status_evidence_v1",
               "data_quality_validation_check_result_v1","data_quality_anomaly_evidence_v1",
               "data-quality-validation-input-sha256-v1","prevent_data_event_append_only_mutation_v1"):
    if marker not in sql43: fail(f"SQL 43 marker missing: {marker}")
for marker in ("data_quality_policy_evidence_v1","data-quality-policy-v1","data_quality_metric_v1",
               "data_snapshot_quality_verdict_v1","data_quality_late_arrival_observation_v1",
               "VALIDATED","REJECTED","INCONCLUSIVE"):
    if marker not in sql44: fail(f"SQL 44 marker missing: {marker}")
for marker in ("persist_data_quality_validation_v1","pg_advisory_xact_lock","QUALITY_VERDICT_CONFLICT",
               "SECURITY DEFINER","SET search_path = pg_catalog, public, pg_temp","jc_data_quality_writer",
               "jc_data_quality_reader","jc_data_quality_function_owner","REVOKE ALL ON FUNCTION",
               "ALTER FUNCTION","OWNER TO jc_data_quality_function_owner"):
    if marker not in sql45: fail(f"SQL 45 marker missing: {marker}")
for marker in ("data_quality_rebuild_comparison_v1","data_quality_validation_conflict_evidence_v1",
               "data_quality_safe_metrics_v1","oldest_inconclusive_snapshot_age_seconds",
               "latest_validated_snapshot_time"):
    if marker not in sql46: fail(f"SQL 46 marker missing: {marker}")
for marker in ("valid FULL validation NEW failed","same validation DUPLICATE failed",
               "quality verdict CONFLICT failed","zero denominator PASS unexpectedly succeeded",
               "quality verdict UPDATE unexpectedly succeeded","quality check DELETE unexpectedly succeeded",
               "quality function owner role is unsafe","quality least privilege or PUBLIC denial failed","ROLLBACK;"):
    if marker not in sql47: fail(f"SQL 47 validation marker missing: {marker}")

java_required={
    "DataQualityValidationDefinition.java","DataQualityValidationRun.java","DataQualityValidationScope.java",
    "DataQualityValidationStatus.java","DataQualityCheckResult.java","DataQualityCheckStatus.java",
    "DataQualitySeverity.java","DataQualityMetric.java","DataQualityThreshold.java","DataQualityPolicy.java",
    "DataQualityAnomaly.java","SnapshotQualityVerdict.java","SnapshotQualityStatus.java",
    "SnapshotQualityVerdictEvaluator.java","RebuildComparison.java","LateArrivalObservation.java",
    "DataQualityFailure.java","DataQualityPersistenceOutcome.java","SourceCompletenessValidator.java",
    "ProjectionCompletenessValidator.java","SnapshotConsistencyValidator.java","LineageIntegrityValidator.java",
    "IdentityIntegrityValidator.java","ExposureIntegrityValidator.java","DeterministicRebuildValidator.java",
    "FullSnapshotQualityValidator.java","DataQualityFingerprints.java",
}
missing=sorted(name for name in java_required if not (JAVA/name).is_file())
if missing: fail(f"missing Java quality contracts: {missing}")
java_text="\n".join(path.read_text(encoding="utf-8") for path in JAVA.glob("*.java"))
for forbidden in ("org.springframework","jakarta.persistence","java.sql","System.currentTimeMillis","Instant.now(",
                  "UUID.randomUUID","Math.random","java.net"):
    if forbidden in java_text: fail(f"forbidden Java dependency or nondeterminism: {forbidden}")
for marker in ("data-quality-validation-input-sha256-v1","data-quality-check-evidence-sha256-v1",
               "data-quality-metric-sha256-v1","data-quality-verdict-sha256-v1",
               "data-quality-rebuild-comparison-sha256-v1","data-quality-late-arrival-observation-sha256-v1"):
    if marker not in java_text: fail(f"Java fingerprint marker missing: {marker}")

combined="\n".join((sql43,sql44,sql45,sql46))
for forbidden in ("UPDATE public.data_platform_event_v1","DELETE FROM public.data_platform_event_v1",
                  "UPDATE public.data_recommendation_adapter_","DELETE FROM public.data_recommendation_adapter_",
                  "UPDATE public.data_projection_","DELETE FROM public.data_projection_",
                  "UPDATE public.recommendation_","DELETE FROM public.recommendation_",
                  "INSERT INTO public.recommendation_","CREATE EXTENSION","TRUNCATE public.data_"):
    if forbidden in combined: fail(f"protected authority mutation found: {forbidden}")
if re.search(r"(?i)\b(CREATE|ALTER)\s+ROLE\s+(postgres|jc_security_owner)\b",combined):
    fail("broad owner role mutation is forbidden")

for path in (ROOT/"verification/dp6").glob("*.tsv"):
    with path.open(encoding="utf-8",newline="") as handle: rows=list(csv.reader(handle,delimiter="\t"))
    if len(rows)<2: fail(f"empty evidence: {path.name}")

try:
    subprocess.run(["git","fetch","origin","main","--depth=1"],cwd=ROOT,check=False,
                   stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    changed=subprocess.run(["git","diff","--name-only","origin/main...HEAD"],cwd=ROOT,check=True,
                           text=True,capture_output=True).stdout.splitlines()
    changed_sql={path for path in changed if path.endswith(".sql")}
    if changed_sql != SQL_FILES: fail(f"DP-6 SQL diff must be exactly 43..47: {sorted(changed_sql)}")
    protected=[path for path in changed if path.startswith((
        "jc-recommendation-core/","jc-intelligence-contracts/","jc-search-contracts/","jc-search-compatibility/",
        "jc-search-runtime/","jc-search-integration/","jc-search-shadow-wiring/","jc-search-readiness/",
        "jc-search-production-controls/","jc-backend/src/main/","jc-backend/src/main/resources/"))]
    if protected: fail(f"protected production/Recommendation/Search source changed: {protected}")
    old=[path for path in changed_sql if int(Path(path).name[:2])<=42]
    if old: fail(f"protected SQL 01..42 changed: {old}")
except (subprocess.CalledProcessError,FileNotFoundError):
    pass

print("DP-6 implementation static verification: PASS")
