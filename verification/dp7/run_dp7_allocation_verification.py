#!/usr/bin/env python3
from __future__ import annotations

import csv
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DP7 = ROOT / "verification/dp7"
SQL_DIR = ROOT / "database/journey-connect-db-v2.7"
JAVA_DIR = ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/integration"
ALLOCATION = ROOT / "docs/platform/governance/SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md"
FOUNDATION = ROOT / "docs/platform/data/DP-7-CROSS-TRACK-INTEGRATION-VALIDATION.md"
HANDOFF = ROOT / "docs/platform/data/DP-7-HANDOFF.md"
REGISTRY = ROOT / "docs/platform/governance/SC-PLATFORM-REGISTRY.md"

DP7_SQL = {
    "database/journey-connect-db-v2.7/48_cross_track_integration_validation_foundation.sql",
    "database/journey-connect-db-v2.7/49_cross_track_contract_mapping_and_boundary_evidence.sql",
    "database/journey-connect-db-v2.7/50_cross_track_integration_verdict_and_conflict.sql",
    "database/journey-connect-db-v2.7/51_cross_track_integration_persistence_roles_and_safe_view.sql",
    "database/journey-connect-db-v2.7/52_cross_track_integration_validation.sql",
}
JAVA_REQUIRED = {
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
EVIDENCE = [
    "DP7_BASELINE.tsv", "DP7_CHANGED_FILES.tsv", "DP7_DB_OBJECTS.tsv", "DP7_CONTRACT_INVENTORY.tsv",
    "DP7_INTEGRATION_MATRIX.tsv", "DP7_RECOMMENDATION_COMPATIBILITY.tsv",
    "DP7_INTELLIGENCE_COMPATIBILITY.tsv", "DP7_SEARCH_COMPATIBILITY.tsv",
    "DP7_IDENTITY_BOUNDARY.tsv", "DP7_AUTHORITY_BOUNDARY.tsv", "DP7_PRIVACY_BOUNDARY.tsv",
    "DP7_RETENTION_BOUNDARY.tsv", "DP7_QUALITY_VERDICT_BOUNDARY.tsv", "DP7_FINGERPRINT_BOUNDARY.tsv",
    "DP7_VERDICTS.tsv", "DP7_DUPLICATE_CONFLICT.tsv", "DP7_CONCURRENCY.tsv", "DP7_ROLE_GRANTS.tsv",
    "DP7_SAFE_VIEW.tsv", "DP7_RETENTION.tsv", "DP7_PROTECTED_DIFF.tsv", "DP7_VERIFICATION_STATUS.tsv",
    "DP7_DECISIONS.tsv",
]
ALLOWED = (
    ".github/workflows/data-postgres-ci.yml", ".github/workflows/dp7-allocation-ci.yml",
    ".github/workflows/data-contract-ci.yml", ".github/workflows/backend-pr-ci.yml",
    ".github/workflows/recommendation-p0-db-ci.yml", ".github/workflows/dp6-allocation-ci.yml",
    ".github/workflows/sc-baseline-reconciliation.yml", ".github/workflows/data-platform-closure-ci.yml",
    "database/journey-connect-db-v2.7/", "jc-data-contracts/", "docs/platform/data/DP-7-",
    "docs/platform/data/DATA-PLATFORM-", "docs/platform/data/HANDOFF-DATA-TO-",
    "docs/platform/governance/SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md",
    "docs/platform/governance/SC-DATA-PLATFORM-TECHNICAL-CLOSURE.md",
    "docs/platform/governance/SC-DECISION-REGISTER.md", "docs/platform/governance/SC-PLATFORM-REGISTRY.md",
    "docs/platform/governance/SC-HANDOFF.md", "docs/platform/governance/SC-RACI.md",
    "docs/platform/governance/SC-2-POST-DP-CLOSURE-NEXT-TRACK-BASELINE-RECONCILIATION.md",
    "docs/platform/governance/sc-next-track/",
    "docs/platform/governance/JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md",
    "docs/platform/governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md", "verification/dp7/",
    "verification/data-platform-closure/", "verification/sc-next-track/",
    ".github/actions/rca2-job/", ".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",
    "docs/platform/recommendation/rca2/", "jc-backend/build.gradle.kts",
    "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java",
    "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/",
    "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml",
    "jc-backend/src/test/java/com/jc/backend/recommendation/rca2/",
    "jc-backend/src/test/java/com/jc/backend/verification/IP9ControlledBackendHookStaticTest.java",
    "jc-search-readiness/src/test/java/com/jc/intelligence/readiness/search/SearchShadowReadinessContractTest.java",
    "verification/rca0/run_rca0_verification.py", "verification/rca1/run_rca1_verification.py",
    "verification/rca1b/run_rca1b_verification.py", "verification/rca2/",
    "verification/dp5/run_dp5_static_verification.py",
    "verification/dp6/run_dp6_allocation_verification.py", "verification/dp6/run_dp6_static_verification.py",
    "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
    "jc-backend/src/test/java/com/jc/backend/search/shadow/production/IP12ProductionShadowStaticTest.java",
)


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in (ALLOCATION, FOUNDATION, HANDOFF, REGISTRY, *(DP7 / name for name in EVIDENCE)):
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing or empty DP-7 artifact: {path.relative_to(ROOT)}")

allocation = ALLOCATION.read_text(encoding="utf-8")
for marker in (
    "APPROVED / MERGED", "Implementation authority: `GRANTED`",
    "d18c91a28b271c9f9891b522c6371017a3d0dd79",
    "48_cross_track_integration_validation_foundation.sql",
    "49_cross_track_contract_mapping_and_boundary_evidence.sql",
    "50_cross_track_integration_verdict_and_conflict.sql",
    "51_cross_track_integration_persistence_roles_and_safe_view.sql",
    "52_cross_track_integration_validation.sql", "jc_data_integration_writer",
    "jc_data_integration_reader", "jc_data_integration_function_owner",
    "data-cross-track-integration-policy-v1", "CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT",
    "SQL `01..47` remains protected", "SQL `53+` remains unallocated",
):
    if marker not in allocation:
        fail(f"approved allocation marker missing: {marker}")

for number in range(1, 53):
    matches = list(SQL_DIR.glob(f"{number:02d}_*.sql"))
    if len(matches) != 1:
        fail(f"canonical SQL {number:02d} expected exactly once, found {len(matches)}")
if list(SQL_DIR.glob("5[3-9]_*.sql")) or list(SQL_DIR.glob("[6-9][0-9]_*.sql")):
    fail("SQL 53+ remains unallocated")
actual_dp7_sql = {
    str(path.relative_to(ROOT)).replace('\\', '/')
    for number in range(48, 53)
    for path in SQL_DIR.glob(f"{number:02d}_*.sql")
}
if actual_dp7_sql != DP7_SQL:
    fail(f"DP-7 SQL allocation mismatch: {sorted(actual_dp7_sql)}")

missing_java = sorted(name for name in JAVA_REQUIRED if not (JAVA_DIR / name).is_file())
if missing_java:
    fail(f"missing DP-7 Java implementation: {missing_java}")
for path in (
    ROOT / "jc-data-contracts/src/test/java/com/jc/data/contract/Dp7CrossTrackIntegrationContractTest.java",
    ROOT / "jc-data-contracts/src/test/resources/dp7-cross-track-golden-v1.tsv",
    DP7 / "run_dp7_static_verification.py", DP7 / "run_dp7_concurrency.sh",
):
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing implementation verification artifact: {path.relative_to(ROOT)}")

for name in EVIDENCE:
    with (DP7 / name).open(encoding="utf-8", newline="") as handle:
        rows = list(csv.reader(handle, delimiter="\t"))
    if len(rows) < 2:
        fail(f"empty evidence: {name}")

try:
    subprocess.run(["git", "fetch", "origin", "main", "--depth=1"], cwd=ROOT, check=False,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    changed = subprocess.run(["git", "diff", "--name-only", "origin/main...HEAD"], cwd=ROOT,
                             check=True, text=True, capture_output=True).stdout.splitlines()
    for rel in filter(None, changed):
        if not any(rel == prefix or rel.startswith(prefix) for prefix in ALLOWED):
            fail(f"protected/unexpected changed file: {rel}")
    changed_sql = {rel for rel in changed if rel.endswith(".sql") and rel.startswith("database/")}
    if changed_sql and changed_sql != DP7_SQL:
        fail(f"DP-7 implementation or closure SQL diff must be empty or exactly 48..52: {sorted(changed_sql)}")
    protected_sql = [rel for rel in changed_sql if int(Path(rel).name[:2]) <= 47]
    if protected_sql:
        fail(f"protected SQL 01..47 changed: {protected_sql}")
    protected_sources = [rel for rel in changed if rel.startswith((
        "jc-recommendation-core/", "jc-intelligence-contracts/", "jc-search-contracts/",
        "jc-search-compatibility/", "jc-search-runtime/", "jc-search-integration/",
        "jc-search-shadow-wiring/", "jc-search-production-controls/",
    ))]
    protected_sources += [rel for rel in changed if rel in (
        "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java",
        "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java",
    )]
    production_configs = [rel for rel in changed
        if rel.startswith("jc-backend/src/main/resources/application")
        and rel != "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml"]
    if protected_sources or production_configs:
        fail(f"protected production/target-track source changed: {protected_sources + production_configs}")
    rca2_config = (ROOT / "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml").read_text(encoding="utf-8")
    for marker in ("flag: off", "traffic-percent: 0", "max-production-dark-read-percent: 0",
                   "production-route-allowed: false", "db-change: NONE", "sql-allocation: NOT_REQUIRED"):
        if marker not in rca2_config:
            fail(f"RCA2 isolated config marker missing: {marker}")
    if "http://" in rca2_config or "https://" in rca2_config or "jdbc:" in rca2_config:
        fail("RCA2 isolated config contains concrete route or DB connection")
    feed = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java").read_text(encoding="utf-8")
    if "RCA-2 request registration failed open" not in feed or "return response;" not in feed:
        fail("RCA2 primary response boundary missing")
    if "return registrar.registerFeed" in feed or "return rca2Registrar" in feed:
        fail("RCA2 hook became response authority")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

print("DP-7 approved allocation, implementation inventory and protected diff verification: PASS")
