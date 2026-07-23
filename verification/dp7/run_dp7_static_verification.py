#!/usr/bin/env python3
from __future__ import annotations

import csv
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SQL = ROOT / "database/journey-connect-db-v2.7"
JAVA = ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/integration"
TEST = ROOT / "jc-data-contracts/src/test/java/com/jc/data/contract/Dp7CrossTrackIntegrationContractTest.java"
GOLDEN = ROOT / "jc-data-contracts/src/test/resources/dp7-cross-track-golden-v1.tsv"
DP7 = ROOT / "verification/dp7"
SQL_FILES = [SQL / f"{number:02d}_{name}.sql" for number, name in (
    (48, "cross_track_integration_validation_foundation"),
    (49, "cross_track_contract_mapping_and_boundary_evidence"),
    (50, "cross_track_integration_verdict_and_conflict"),
    (51, "cross_track_integration_persistence_roles_and_safe_view"),
    (52, "cross_track_integration_validation"),
)]


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in (*SQL_FILES, TEST, GOLDEN):
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing DP-7 implementation artifact: {path.relative_to(ROOT)}")
for number in range(1, 53):
    if len(list(SQL.glob(f"{number:02d}_*.sql"))) != 1:
        fail(f"canonical SQL {number:02d} missing or duplicated")
if list(SQL.glob("5[3-9]_*.sql")) or list(SQL.glob("[6-9][0-9]_*.sql")):
    fail("SQL 53+ is unallocated")

sql48, sql49, sql50, sql51_wrapper, sql52_wrapper = (path.read_text(encoding="utf-8") for path in SQL_FILES)
sql51 = "\n".join(path.read_text(encoding="utf-8") for path in sorted(
    (DP7 / "sql").glob("51_cross_track_integration_persistence_roles_and_safe_view_part*.inc")
))
sql52 = "\n".join(path.read_text(encoding="utf-8") for path in sorted(
    (DP7 / "sql").glob("52_cross_track_integration_validation_part*.inc")
))
if not sql51 or not sql52:
    fail("DP-7 SQL include fragments missing")
for marker in (
    "data_cross_track_integration_policy_v1", "data_cross_track_integration_run_v1",
    "data_cross_track_integration_status_evidence_v1", "data_cross_track_integration_check_result_v1",
    "data_cross_track_integration_anomaly_v1", "data-cross-track-integration-policy-v1",
):
    if marker not in sql48:
        fail(f"SQL 48 marker missing: {marker}")
for marker in (
    "data_cross_track_contract_mapping_evidence_v1", "data_cross_track_identity_evidence_v1",
    "data_cross_track_authority_evidence_v1", "data_cross_track_privacy_evidence_v1",
    "data_cross_track_retention_evidence_v1",
):
    if marker not in sql49:
        fail(f"SQL 49 marker missing: {marker}")
for marker in (
    "data_cross_track_quality_verdict_binding_v1", "data_cross_track_integration_verdict_v1",
    "data_cross_track_integration_conflict_evidence_v1", "CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT",
    "COMPATIBLE", "CONDITIONALLY_COMPATIBLE", "INCOMPATIBLE", "INCONCLUSIVE",
):
    if marker not in sql50:
        fail(f"SQL 50 marker missing: {marker}")
for marker in (
    "persist_data_cross_track_integration_v1", "pg_advisory_xact_lock", "SECURITY DEFINER",
    "SET search_path=pg_catalog,public,pg_temp", "jc_data_integration_writer",
    "jc_data_integration_reader", "jc_data_integration_function_owner", "NOLOGIN",
    "data_cross_track_integration_safe_metrics_v1", "REVOKE ALL ON FUNCTION",
    "ALTER VIEW public.data_cross_track_integration_safe_metrics_v1 OWNER TO jc_data_integration_function_owner",
):
    if marker not in sql51:
        fail(f"SQL 51 marker missing: {marker}")
for marker in (
    "integration-input-sha256-v1", "integration-check-evidence-sha256-v1",
    "integration-mapping-sha256-v1", "integration-verdict-sha256-v1",
    "cross-track-contract-matrix-sha256-v1",
):
    if marker not in "\n".join((sql48, sql49, sql50, sql51)):
        fail(f"DP-7 fingerprint domain missing: {marker}")
for marker in (
    "Recommendation conditional NEW failed", "Intelligence missing domain mapping",
    "Search missing contract", "Integration CONFLICT failed", "Partial DP-7 insert was not rolled back",
    "append-only UPDATE unexpectedly succeeded", "writer direct table write unexpectedly succeeded",
    "reader raw evidence access unexpectedly succeeded", "function owner role is unsafe",
    "PUBLIC function execute not revoked", "safe view exposes forbidden columns", "ROLLBACK;",
):
    if marker not in sql52:
        fail(f"SQL 52 validation marker missing: {marker}")

combined_sql = "\n".join((sql48, sql49, sql50, sql51))
for forbidden in (
    "INSERT INTO public.recommendation_", "UPDATE public.recommendation_", "DELETE FROM public.recommendation_",
    "INSERT INTO public.search_document_projection", "UPDATE public.search_document_projection",
    "DELETE FROM public.search_document_projection", "TRUNCATE ", "CREATE EXTENSION",
):
    if forbidden in combined_sql:
        fail(f"protected authority mutation found: {forbidden}")
if re.search(r"(?i)\b(CREATE|ALTER)\s+ROLE\s+(postgres|jc_security_owner)\b", combined_sql):
    fail("broad owner role mutation is forbidden")
if "automatic_purge_enabled boolean NOT NULL CHECK (automatic_purge_enabled=false)" not in sql49:
    fail("automatic purge fail-closed constraint missing")

required_java = {
    "CrossTrackIntegrationDefinition.java", "CrossTrackIntegrationRun.java", "CrossTrackIntegrationScope.java",
    "CrossTrackIntegrationCheck.java", "CrossTrackIntegrationCheckStatus.java", "CrossTrackIntegrationStatus.java",
    "CrossTrackIntegrationSeverity.java", "CrossTrackContractMapping.java", "CrossTrackAuthorityRule.java",
    "CrossTrackPrivacyRule.java", "CrossTrackRetentionRule.java", "CrossTrackIdentityBinding.java",
    "CrossTrackIntegrationVerdict.java", "CrossTrackIntegrationFailure.java",
    "CrossTrackIntegrationPersistenceOutcome.java", "DataRecommendationIntegrationValidator.java",
    "DataIntelligenceIntegrationValidator.java", "DataSearchIntegrationValidator.java",
    "CrossTrackIdentityValidator.java", "CrossTrackAuthorityValidator.java", "CrossTrackPrivacyValidator.java",
    "CrossTrackRetentionValidator.java", "CrossTrackQualityVerdictValidator.java",
    "CrossTrackFingerprintValidator.java", "FullCrossTrackIntegrationValidator.java",
}
missing = sorted(name for name in required_java if not (JAVA / name).is_file())
if missing:
    fail(f"missing Java DP-7 contracts: {missing}")
java_text = "\n".join(path.read_text(encoding="utf-8") for path in JAVA.glob("*.java"))
for forbidden in (
    "org.springframework", "jakarta.persistence", "java.sql", "java.net", "Instant.now(",
    "System.currentTimeMillis", "UUID.randomUUID", "Math.random", "static final Map<",
):
    if forbidden in java_text:
        fail(f"forbidden Java dependency or nondeterminism: {forbidden}")
for marker in (
    "integration-input-sha256-v1", "integration-check-evidence-sha256-v1",
    "integration-mapping-sha256-v1", "integration-verdict-sha256-v1",
    "cross-track-contract-matrix-sha256-v1", "DataRecommendationIntegrationValidator",
    "DataIntelligenceIntegrationValidator", "DataSearchIntegrationValidator",
    "FullCrossTrackIntegrationValidator", "CONDITIONALLY_COMPATIBLE", "INCONCLUSIVE",
):
    if marker not in java_text:
        fail(f"Java implementation marker missing: {marker}")
if "cross-track-identity-binding-sha256-v1" in java_text:
    fail("unallocated fingerprint domain introduced")

with GOLDEN.open(encoding="utf-8", newline="") as handle:
    rows = list(csv.reader(handle, delimiter="\t"))
if len(rows) < 5:
    fail("DP-7 golden fixture coverage is insufficient")

evidence_expected = {
    "DP7_DB_OBJECTS.tsv", "DP7_VERDICTS.tsv", "DP7_RETENTION.tsv", "DP7_CONCURRENCY.tsv",
    "DP7_ROLE_GRANTS.tsv", "DP7_SAFE_VIEW.tsv", "DP7_VERIFICATION_STATUS.tsv",
}
for name in evidence_expected:
    path = DP7 / name
    if not path.is_file():
        fail(f"missing DP-7 evidence: {name}")
    with path.open(encoding="utf-8", newline="") as handle:
        if len(list(csv.reader(handle, delimiter="\t"))) < 2:
            fail(f"empty DP-7 evidence: {name}")

try:
    subprocess.run(["git", "fetch", "origin", "main", "--depth=1"], cwd=ROOT, check=False,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    changed = subprocess.run(["git", "diff", "--name-only", "origin/main...HEAD"], cwd=ROOT,
                             check=True, text=True, capture_output=True).stdout.splitlines()
    changed_sql = {rel for rel in changed if rel.startswith("database/") and rel.endswith(".sql")}
    expected = {str(path.relative_to(ROOT)).replace('\\', '/') for path in SQL_FILES}
    if changed_sql != expected:
        fail(f"DP-7 SQL diff must be exactly 48..52: {sorted(changed_sql)}")
    if any(rel.startswith(("jc-backend/src/main/", "jc-recommendation-core/", "jc-intelligence-contracts/",
                           "jc-search-")) for rel in changed):
        fail("protected production or target-track source changed")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

print("DP-7 Java, SQL, authority, fingerprint and production-protection static verification: PASS")
