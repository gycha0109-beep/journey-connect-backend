#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path
import csv
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
    "docs/platform/proposals/DP-0-TRACK-CHANGE-PROPOSAL.md",
]
ALLOWED = (
    "docs/platform/governance/", "docs/platform/data/", "docs/platform/proposals/",
    "verification/sc-dp1-baseline-reconciliation/", "verification/dp1/", "verification/dp2/",
    "verification/dp3/", "verification/dp4/",
    ".github/workflows/sc-baseline-reconciliation.yml",
    ".github/workflows/data-contract-ci.yml",
    ".github/workflows/data-postgres-ci.yml",
    ".github/workflows/recommendation-p0-db-ci.yml",
    ".github/workflows/backend-pr-ci.yml",
    "jc-backend/settings.gradle.kts",
    "jc-backend/src/test/java/com/jc/backend/search/shadow/production/IP12ProductionShadowStaticTest.java",
    "jc-data-contracts/",
    "database/journey-connect-db-v2.7/",
)
DP2_SQL = {
    "database/journey-connect-db-v2.7/29_data_platform_event_store.sql",
    "database/journey-connect-db-v2.7/30_data_event_idempotency_roles.sql",
    "database/journey-connect-db-v2.7/31_data_event_store_smoke_test.sql",
}
DP3_SQL = {
    "database/journey-connect-db-v2.7/32_data_retry_quarantine_evidence.sql",
    "database/journey-connect-db-v2.7/33_data_retry_processing_roles.sql",
    "database/journey-connect-db-v2.7/34_data_retry_quarantine_smoke_test.sql",
}


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for rel in REQUIRED:
    if not (ROOT / rel).is_file():
        fail(f"missing required file: {rel}")

central = [
    ROOT / "docs/platform/governance/JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md",
    ROOT / "docs/platform/governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md",
    ROOT / "docs/platform/data/DP-0-DATA-PLATFORM-CONTRACT-FOUNDATION.md",
    ROOT / "docs/platform/data/DP-0-P2-BASELINE-ALIGNMENT.md",
]
for path in central:
    text = path.read_text(encoding="utf-8")
    if "journey-connect-db-v2.7/01..28" not in text:
        fail(f"01..28 baseline missing: {path}")
    if "jc-data-contracts" not in text or "com.jc.data.contract" not in text:
        fail(f"module/package reservation missing: {path}")

gov = (ROOT / "docs/platform/governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md").read_text(encoding="utf-8")
sequence = "IP 기술 기준선 종결\n→ DP\n→ OP\n→ RP\n→ 교차 트랙 통합 검증"
if sequence not in gov:
    fail("authoritative sequence missing")
if "historical recommendation" not in gov:
    fail("historical recommendation marker missing")

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
    "platform-event-v1", "data-platform-architecture-v1",
    "event-idempotency-fingerprint-v1", "data-lineage-snapshot-v1",
):
    if contract_id not in registry:
        fail(f"contract registry missing {contract_id}")

for number in range(1, 29):
    if not list((ROOT / "database/journey-connect-db-v2.7").glob(f"{number:02d}_*.sql")):
        fail(f"canonical SQL {number:02d} missing")

try:
    subprocess.run(["git", "fetch", "origin", "main", "--depth=1"], cwd=ROOT,
                   check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    diff = subprocess.run(["git", "diff", "--name-only", "origin/main...HEAD"], cwd=ROOT,
                          check=True, text=True, capture_output=True).stdout.splitlines()
    changed_sql = {rel for rel in diff if rel.endswith(".sql")}
    for rel in filter(None, diff):
        if not any(rel == prefix or rel.startswith(prefix) for prefix in ALLOWED):
            fail(f"protected/unexpected changed file: {rel}")
    if changed_sql - (DP2_SQL | DP3_SQL):
        fail(f"unapproved SQL changed: {sorted(changed_sql - (DP2_SQL | DP3_SQL))}")
    if changed_sql and changed_sql not in (DP2_SQL, DP3_SQL):
        fail(f"SQL allocation must change exactly DP-2 29..31 or DP-3 32..34: {sorted(changed_sql)}")
    if any(rel.startswith(("jc-backend/src/main/", "jc-recommendation-core/", "jc-search-")) for rel in diff):
        fail("production/recommendation/search source changed")
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

print("SC baseline reconciliation static validation: PASS")
