#!/usr/bin/env python3
from __future__ import annotations

import csv
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GOV = ROOT / "docs/platform/governance"
DATA = ROOT / "docs/platform/data"
SQL = ROOT / "database/journey-connect-db-v2.7"
OUT = ROOT / "verification/sc-dp1-baseline-reconciliation"

REQUIRED_EXACT = [
    GOV / "JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md",
    GOV / "JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md",
    GOV / "SC-DECISION-REGISTER.md", GOV / "SC-RACI.md",
    GOV / "SC-PLATFORM-REGISTRY.md", GOV / "SC-HANDOFF.md",
    GOV / "SC-DATA-PLATFORM-TECHNICAL-CLOSURE.md",
    GOV / "SC-DP1-BASELINE-RECONCILIATION.md", GOV / "SC-DP3-ENTRY-DECISIONS.md",
    GOV / "SC-DP4-5-PERSISTENCE-ALLOCATION.md", GOV / "SC-DP5-PROJECTION-ALLOCATION.md",
    GOV / "SC-DP6-QUALITY-ALLOCATION.md", GOV / "SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md",
    DATA / "DP-0-DATA-PLATFORM-CONTRACT-FOUNDATION.md", DATA / "DP-0-P2-BASELINE-ALIGNMENT.md",
    DATA / "DP-0-HANDOFF.md", DATA / "DATA-PLATFORM-ARCHITECTURE-V1.md",
    DATA / "PLATFORM-EVENT-CONTRACT-V1.md", DATA / "BEHAVIOR-EVENT-TAXONOMY-V1.md",
    DATA / "EVENT-IDEMPOTENCY-AND-FINGERPRINT-V1.md", DATA / "EVENT-RETRY-QUARANTINE-REPLAY-V1.md",
    DATA / "DATA-LINEAGE-AND-SNAPSHOT-V1.md", DATA / "DATA-RETENTION-AND-PRIVACY-V1.md",
    DATA / "P0-RECOMMENDATION-EVENT-ADAPTER-V1.md",
    DATA / "DP-5-PROJECTION-AND-SNAPSHOT-FOUNDATION.md", DATA / "DP-5-PROJECTION-MATRIX.md",
    DATA / "DP-5-HANDOFF.md", DATA / "DP-6-DATA-QUALITY-AND-LINEAGE-VALIDATION-HARDENING.md",
    DATA / "DP-6-QUALITY-MATRIX.md", DATA / "DP-6-HANDOFF.md",
    DATA / "DP-7-CROSS-TRACK-INTEGRATION-VALIDATION.md", DATA / "DP-7-INTEGRATION-MATRIX.md",
    DATA / "DP-7-AUTHORITY-MATRIX.md", DATA / "DP-7-PRIVACY-RETENTION-MATRIX.md",
    DATA / "DP-7-HANDOFF.md", ROOT / "docs/platform/proposals/DP-0-TRACK-CHANGE-PROPOSAL.md",
]
CLOSURE_DOCS = [
    DATA / "DATA-PLATFORM-TECHNICAL-BASELINE-V1.md", DATA / "DATA-PLATFORM-AUTHORITY-CLOSURE-V1.md",
    DATA / "DATA-PLATFORM-PRODUCTION-READINESS-GAPS-V1.md",
    DATA / "DATA-PLATFORM-PRODUCTION-ACTIVATION-DEPENDENCIES-V1.md",
    DATA / "DATA-PLATFORM-CHANGE-POLICY-V1.md", DATA / "DATA-PLATFORM-CLOSURE-HANDOFF.md",
    *(DATA / f"HANDOFF-DATA-TO-{target}-V1.md" for target in
      ("RECOMMENDATION", "INTELLIGENCE", "SEARCH", "OPERATIONS", "RELIABILITY")),
]
ALLOWED = (
    "docs/platform/governance/", "docs/platform/data/", "docs/platform/proposals/",
    "verification/sc-dp1-baseline-reconciliation/", "verification/dp1/", "verification/dp2/",
    "verification/dp3/", "verification/dp4/", "verification/dp4-5/", "verification/dp5/",
    "verification/dp6/", "verification/dp7/", "verification/data-platform-closure/",
    ".github/workflows/", "jc-backend/settings.gradle.kts",
    "jc-backend/src/test/java/com/jc/backend/search/shadow/production/IP12ProductionShadowStaticTest.java",
    "jc-data-contracts/", "database/journey-connect-db-v2.7/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in REQUIRED_EXACT + CLOSURE_DOCS:
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing or empty required file: {path.relative_to(ROOT)}")

for path in (GOV / "JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md",
             GOV / "JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md",
             DATA / "DP-0-DATA-PLATFORM-CONTRACT-FOUNDATION.md",
             DATA / "DP-0-P2-BASELINE-ALIGNMENT.md"):
    text = path.read_text(encoding="utf-8")
    for marker in ("journey-connect-db-v2.7/01..28", "jc-data-contracts", "com.jc.data.contract"):
        if marker not in text:
            fail(f"baseline marker {marker} missing: {path.relative_to(ROOT)}")

governance = (GOV / "JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md").read_text(encoding="utf-8")
for marker in ("IP 기술 기준선 종결\n→ DP\n→ OP\n→ RP\n→ 교차 트랙 통합 검증", "historical recommendation"):
    if marker not in governance:
        fail(f"governance marker missing: {marker}")

allocations = {
    "SC-DP4-5-PERSISTENCE-ALLOCATION.md": ("Implementation authority: `GRANTED`", "35_data_", "37_data_"),
    "SC-DP5-PROJECTION-ALLOCATION.md": ("Implementation authority: `GRANTED`", "38_data_", "42_data_", "jc_data_projection_writer"),
    "SC-DP6-QUALITY-ALLOCATION.md": ("APPROVED / MERGED", "43_data_", "47_data_", "data-quality-policy-v1"),
    "SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md": (
        "APPROVED / MERGED", "d18c91a28b271c9f9891b522c6371017a3d0dd79", "48_cross_", "52_cross_",
        "jc_data_integration_writer", "data-cross-track-integration-policy-v1",
        "SQL `01..47` remains protected", "SQL `53+` remains unallocated"),
}
for name, markers in allocations.items():
    text = (GOV / name).read_text(encoding="utf-8")
    if any(marker not in text for marker in markers):
        fail(f"allocation markers missing: {name}")

registry = (GOV / "SC-PLATFORM-REGISTRY.md").read_text(encoding="utf-8")
registry_ids = (
    "platform-event-v1", "data-platform-architecture-v1", "event-idempotency-fingerprint-v1",
    "data-lineage-snapshot-v1", "recommendation-profile-input-v1", "experiment-outcome-input-v1",
    "data-projection-snapshot-v1", "data-quality-policy-v1", "data-quality-verdict-sha256-v1",
    "data-cross-track-integration-policy-v1", "data-cross-track-integration-run-v1",
    "data-cross-track-integration-check-v1", "data-cross-track-contract-mapping-v1",
    "data-cross-track-authority-matrix-v1", "data-cross-track-privacy-retention-matrix-v1",
    "data-cross-track-integration-verdict-v1", "integration-input-sha256-v1",
    "integration-check-evidence-sha256-v1", "integration-mapping-sha256-v1",
    "integration-verdict-sha256-v1", "cross-track-contract-matrix-sha256-v1",
)
missing = [item for item in registry_ids if item not in registry]
if missing:
    fail(f"registry IDs missing: {missing}")

closure_decision = (GOV / "SC-DATA-PLATFORM-TECHNICAL-CLOSURE.md").read_text(encoding="utf-8")
for closure_id in (
    "data-platform-technical-baseline-v1", "data-platform-authority-closure-v1",
    "data-platform-production-readiness-gaps-v1",
    "data-platform-production-activation-dependencies-v1", "data-platform-change-policy-v1",
):
    if closure_id not in closure_decision:
        fail(f"closure contract ID missing: {closure_id}")

link_re = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
for path in REQUIRED_EXACT + CLOSURE_DOCS:
    for target in link_re.findall(path.read_text(encoding="utf-8")):
        if "://" in target or target.startswith("#"):
            continue
        clean = target.split("#", 1)[0]
        if clean and not (path.parent / clean).resolve().exists():
            fail(f"broken link {target}: {path.relative_to(ROOT)}")

for number in range(1, 53):
    if len(list(SQL.glob(f"{number:02d}_*.sql"))) != 1:
        fail(f"canonical SQL {number:02d} missing or duplicated")
if list(SQL.glob("5[3-9]_*.sql")) or list(SQL.glob("[6-9][0-9]_*.sql")):
    fail("SQL 53+ remains unallocated")

try:
    subprocess.run(["git", "fetch", "origin", "main", "--depth=1"], cwd=ROOT, check=False,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    changed = subprocess.run(["git", "diff", "--name-only", "origin/main...HEAD"], cwd=ROOT,
                             check=True, text=True, capture_output=True).stdout.splitlines()
    for rel in filter(None, changed):
        if not any(rel == prefix or rel.startswith(prefix) for prefix in ALLOWED):
            fail(f"protected/unexpected changed file: {rel}")
    changed_sql = {rel for rel in changed if rel.startswith("database/") and rel.endswith(".sql")}
    expected_dp7 = {str(next(SQL.glob(f"{number:02d}_*.sql")).relative_to(ROOT)).replace('\\', '/')
                    for number in range(48, 53)}
    if changed_sql and changed_sql != expected_dp7:
        fail(f"SQL diff must be empty or DP-7 48..52: {sorted(changed_sql)}")
    protected = [rel for rel in changed if rel.startswith((
        "jc-backend/src/main/", "jc-recommendation-core/", "jc-intelligence-contracts/", "jc-search-"))]
    if protected:
        fail(f"production/target source changed: {protected}")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

for path in OUT.glob("*.tsv"):
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.reader(handle, delimiter="\t"))
    if not rows:
        fail(f"empty evidence: {path.name}")

print("SC baseline reconciliation through Data Platform technical closure: PASS")
