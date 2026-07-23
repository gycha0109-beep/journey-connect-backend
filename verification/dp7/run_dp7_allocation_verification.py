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
    "database/journey-connect-db-v2.7/", "jc-data-contracts/", "docs/platform/data/DP-7-",
    "docs/platform/governance/SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md",
    "docs/platform/governance/SC-DECISION-REGISTER.md", "docs/platform/governance/SC-PLATFORM-REGISTRY.md",
    "docs/platform/governance/SC-HANDOFF.md", "verification/dp7/",
    "verification/dp5/run_dp5_static_verification.py",
    "verification/dp6/run_dp6_allocation_verification.py",
    "verification/dp6/run_dp6_static_verification.py",
    "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
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
    if changed_sql != DP7_SQL:
        fail(f"DP-7 implementation SQL diff must be exactly 48..52: {sorted(changed_sql)}")
    protected_sql = [rel for rel in changed_sql if int(Path(rel).name[:2]) <= 47]
    if protected_sql:
        fail(f"protected SQL 01..47 changed: {protected_sql}")
    protected_sources = [rel for rel in changed if rel.startswith((
        "jc-backend/src/main/", "jc-recommendation-core/", "jc-intelligence-contracts/",
        "jc-search-contracts/", "jc-search-compatibility/", "jc-search-runtime/",
        "jc-search-integration/", "jc-search-shadow-wiring/", "jc-search-readiness/",
        "jc-search-production-controls/",
    ))]
    if protected_sources:
        fail(f"protected production/target-track source changed: {protected_sources}")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

print("DP-7 approved allocation, implementation inventory and protected diff verification: PASS")
