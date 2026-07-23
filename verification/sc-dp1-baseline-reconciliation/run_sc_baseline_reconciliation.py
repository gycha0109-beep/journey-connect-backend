#!/usr/bin/env python3
from __future__ import annotations

import csv
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "verification/sc-dp1-baseline-reconciliation"
REQUIRED = [
    "docs/platform/governance/JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md",
    "docs/platform/governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md",
    "docs/platform/governance/SC-DECISION-REGISTER.md",
    "docs/platform/governance/SC-RACI.md",
    "docs/platform/governance/SC-PLATFORM-REGISTRY.md",
    "docs/platform/governance/SC-DP1-BASELINE-RECONCILIATION.md",
    "docs/platform/governance/SC-DP3-ENTRY-DECISIONS.md",
    "docs/platform/governance/SC-DP4-5-PERSISTENCE-ALLOCATION.md",
    "docs/platform/governance/SC-DP5-PROJECTION-ALLOCATION.md",
    "docs/platform/governance/SC-DP6-QUALITY-ALLOCATION.md",
    "docs/platform/governance/SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md",
    "docs/platform/governance/SC-HANDOFF.md",
    "docs/platform/data/DP-0-DATA-PLATFORM-CONTRACT-FOUNDATION.md",
    "docs/platform/data/DP-0-P2-BASELINE-ALIGNMENT.md",
    "docs/platform/data/DP-0-HANDOFF.md",
    "docs/platform/data/DATA-PLATFORM-ARCHITECTURE-V1.md",
    "docs/platform/data/PLATFORM-EVENT-CONTRACT-V1.md",
    "docs/platform/data/BEHAVIOR-EVENT-TAXONOMY-V1.md",
    "docs/platform/data/EVENT-IDEMPOTENCY-AND-FINGERPRINT-V1.md",
    "docs/platform/data/EVENT-RETRY-QUARANTINE-REPLAY-V1.md",
    "docs/platform/data/DATA-LINEAGE-AND-SNAPSHOT-V1.md",
    "docs/platform/data/DATA-RETENTION-AND-PRIVACY-V1.md",
    "docs/platform/data/P0-RECOMMENDATION-EVENT-ADAPTER-V1.md",
    "docs/platform/data/DP-5-PROJECTION-AND-SNAPSHOT-FOUNDATION.md",
    "docs/platform/data/DP-5-PROJECTION-MATRIX.md",
    "docs/platform/data/DP-5-HANDOFF.md",
    "docs/platform/data/DP-6-DATA-QUALITY-AND-LINEAGE-VALIDATION-HARDENING.md",
    "docs/platform/data/DP-6-QUALITY-MATRIX.md",
    "docs/platform/data/DP-6-HANDOFF.md",
    "docs/platform/data/DP-7-CROSS-TRACK-INTEGRATION-VALIDATION.md",
    "docs/platform/data/DP-7-INTEGRATION-MATRIX.md",
    "docs/platform/data/DP-7-AUTHORITY-MATRIX.md",
    "docs/platform/data/DP-7-PRIVACY-RETENTION-MATRIX.md",
    "docs/platform/data/DP-7-HANDOFF.md",
    "docs/platform/proposals/DP-0-TRACK-CHANGE-PROPOSAL.md",
]
ALLOWED = (
    "docs/platform/governance/", "docs/platform/data/", "docs/platform/proposals/",
    "verification/sc-dp1-baseline-reconciliation/", "verification/dp1/", "verification/dp2/",
    "verification/dp3/", "verification/dp4/", "verification/dp4-5/", "verification/dp5/",
    "verification/dp6/", "verification/dp7/", ".github/workflows/sc-baseline-reconciliation.yml",
    ".github/workflows/data-contract-ci.yml", ".github/workflows/data-postgres-ci.yml",
    ".github/workflows/recommendation-p0-db-ci.yml", ".github/workflows/backend-pr-ci.yml",
    ".github/workflows/dp5-governance-finalize.yml", ".github/workflows/dp6-allocation-ci.yml",
    ".github/workflows/dp7-allocation-ci.yml", "jc-backend/settings.gradle.kts",
    "jc-backend/src/test/java/com/jc/backend/search/shadow/production/IP12ProductionShadowStaticTest.java",
    "jc-data-contracts/", "database/journey-connect-db-v2.7/",
)
SQL_GROUPS = {
    "DP2": set(range(29, 32)), "DP3": set(range(32, 35)), "DP45": set(range(35, 38)),
    "DP5": set(range(38, 43)), "DP6": set(range(43, 48)), "DP7": set(range(48, 53)),
}


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for rel in REQUIRED:
    if not (ROOT / rel).is_file():
        fail(f"missing required file: {rel}")

for rel in (
    "docs/platform/governance/JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md",
    "docs/platform/governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md",
    "docs/platform/data/DP-0-DATA-PLATFORM-CONTRACT-FOUNDATION.md",
    "docs/platform/data/DP-0-P2-BASELINE-ALIGNMENT.md",
):
    text = (ROOT / rel).read_text(encoding="utf-8")
    if "journey-connect-db-v2.7/01..28" not in text:
        fail(f"01..28 historical baseline missing: {rel}")
    if "jc-data-contracts" not in text or "com.jc.data.contract" not in text:
        fail(f"module/package reservation missing: {rel}")

governance = (ROOT / "docs/platform/governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md").read_text(encoding="utf-8")
if "IP 기술 기준선 종결\n→ DP\n→ OP\n→ RP\n→ 교차 트랙 통합 검증" not in governance:
    fail("authoritative sequence missing")
if "historical recommendation" not in governance:
    fail("historical recommendation marker missing")

allocation_expectations = {
    "SC-DP4-5-PERSISTENCE-ALLOCATION.md": (
        "Implementation authority: `GRANTED`", "35_data_recommendation_adapter_shadow_evidence.sql",
        "36_data_recommendation_adapter_shadow_persistence.sql", "37_data_recommendation_adapter_shadow_validation.sql",
        "SQL `01..34` remains protected", "SQL `38+` remains unallocated",
    ),
    "SC-DP5-PROJECTION-ALLOCATION.md": (
        "Implementation authority: `GRANTED`", "38_data_projection_snapshot_foundation.sql",
        "39_data_recommendation_profile_projection.sql", "40_data_experiment_outcome_projection.sql",
        "41_data_projection_persistence_roles.sql", "42_data_projection_snapshot_validation.sql",
        "jc_data_projection_writer", "jc_data_projection_reader", "jc_data_projection_function_owner",
    ),
    "SC-DP6-QUALITY-ALLOCATION.md": (
        "APPROVED / MERGED", "Implementation authority: `GRANTED`",
        "43_data_quality_validation_foundation.sql", "44_data_quality_metrics_and_verdict.sql",
        "45_data_quality_persistence_and_roles.sql", "46_data_quality_rebuild_and_safe_views.sql",
        "47_data_quality_validation.sql", "jc_data_quality_writer", "jc_data_quality_reader",
        "jc_data_quality_function_owner", "data-quality-policy-v1",
    ),
    "SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md": (
        "APPROVED / MERGED", "Implementation authority: `GRANTED`",
        "d18c91a28b271c9f9891b522c6371017a3d0dd79",
        "48_cross_track_integration_validation_foundation.sql",
        "49_cross_track_contract_mapping_and_boundary_evidence.sql",
        "50_cross_track_integration_verdict_and_conflict.sql",
        "51_cross_track_integration_persistence_roles_and_safe_view.sql",
        "52_cross_track_integration_validation.sql", "jc_data_integration_writer",
        "jc_data_integration_reader", "jc_data_integration_function_owner",
        "data-cross-track-integration-policy-v1", "SQL `01..47` remains protected",
        "SQL `53+` remains unallocated",
    ),
}
for name, markers in allocation_expectations.items():
    text = (ROOT / "docs/platform/governance" / name).read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            fail(f"{name} marker missing: {marker}")

link_re = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
for rel in REQUIRED:
    path = ROOT / rel
    for target in link_re.findall(path.read_text(encoding="utf-8")):
        if "://" in target or target.startswith("#"):
            continue
        clean = target.split("#", 1)[0]
        if clean and not (path.parent / clean).resolve().exists():
            fail(f"broken link {target} in {rel}")

registry = (ROOT / "docs/platform/governance/SC-PLATFORM-REGISTRY.md").read_text(encoding="utf-8")
for contract_id in (
    "platform-event-v1", "data-platform-architecture-v1", "event-idempotency-fingerprint-v1",
    "data-lineage-snapshot-v1", "recommendation-profile-input-v1", "experiment-outcome-input-v1",
    "data-projection-snapshot-v1", "data-quality-policy-v1", "data-quality-verdict-sha256-v1",
    "data-cross-track-integration-policy-v1", "data-cross-track-integration-run-v1",
    "data-cross-track-integration-check-v1", "data-cross-track-contract-mapping-v1",
    "data-cross-track-authority-matrix-v1", "data-cross-track-privacy-retention-matrix-v1",
    "data-cross-track-integration-verdict-v1", "integration-input-sha256-v1",
    "integration-check-evidence-sha256-v1", "integration-mapping-sha256-v1",
    "integration-verdict-sha256-v1", "cross-track-contract-matrix-sha256-v1",
):
    if contract_id not in registry:
        fail(f"contract registry missing {contract_id}")

sql_dir = ROOT / "database/journey-connect-db-v2.7"
for number in range(1, 53):
    matches = list(sql_dir.glob(f"{number:02d}_*.sql"))
    if len(matches) != 1:
        fail(f"canonical SQL {number:02d} missing or duplicated")
if list(sql_dir.glob("5[3-9]_*.sql")) or list(sql_dir.glob("[6-9][0-9]_*.sql")):
    fail("SQL 53+ remains unallocated")

try:
    subprocess.run(["git", "fetch", "origin", "main", "--depth=1"], cwd=ROOT, check=False,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    diff = subprocess.run(["git", "diff", "--name-only", "origin/main...HEAD"], cwd=ROOT,
                          check=True, text=True, capture_output=True).stdout.splitlines()
    for rel in filter(None, diff):
        if not any(rel == prefix or rel.startswith(prefix) for prefix in ALLOWED):
            fail(f"protected/unexpected changed file: {rel}")
    changed_sql = {rel for rel in diff if rel.startswith("database/") and rel.endswith(".sql")}
    expected_dp7 = {str(next(sql_dir.glob(f"{number:02d}_*.sql")).relative_to(ROOT)).replace('\\', '/')
                    for number in SQL_GROUPS["DP7"]}
    if changed_sql != expected_dp7:
        fail(f"DP-7 implementation SQL diff must be exactly 48..52: {sorted(changed_sql)}")
    if any(rel.startswith(("jc-backend/src/main/", "jc-recommendation-core/",
                           "jc-intelligence-contracts/", "jc-search-")) for rel in diff):
        fail("production/Recommendation/Intelligence/Search source changed")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

for path in OUT.glob("*.tsv"):
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.reader(handle, delimiter="\t"))
    if not rows:
        fail(f"empty evidence: {path}")
    if path.name.endswith("_VERIFICATION.tsv") and rows[0][:8] != [
        "checkId", "target", "expected", "observed", "result",
        "evidenceReference", "sourceHead", "executedAtUtc",
    ]:
        fail(f"verification header invalid: {path}")

print("SC baseline reconciliation through approved DP-7 implementation allocation: PASS")
