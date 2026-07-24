#!/usr/bin/env python3
from __future__ import annotations

import csv
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GOV = ROOT / "docs/platform/governance"
DATA = ROOT / "docs/platform/data"
SQL = ROOT / "database/journey-connect-db-v2.7"
OUT = ROOT / "verification/sc-dp1-baseline-reconciliation"

REQUIRED = [
    GOV / "JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md",
    GOV / "JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md",
    GOV / "SC-DECISION-REGISTER.md",
    GOV / "SC-RACI.md",
    GOV / "SC-PLATFORM-REGISTRY.md",
    GOV / "SC-HANDOFF.md",
    GOV / "SC-DATA-PLATFORM-TECHNICAL-CLOSURE.md",
    GOV / "SC-2-POST-DP-CLOSURE-NEXT-TRACK-BASELINE-RECONCILIATION.md",
    GOV / "SC-DP1-BASELINE-RECONCILIATION.md",
    GOV / "SC-DP3-ENTRY-DECISIONS.md",
    GOV / "SC-DP4-5-PERSISTENCE-ALLOCATION.md",
    GOV / "SC-DP5-PROJECTION-ALLOCATION.md",
    GOV / "SC-DP6-QUALITY-ALLOCATION.md",
    GOV / "SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md",
    DATA / "DP-0-DATA-PLATFORM-CONTRACT-FOUNDATION.md",
    DATA / "DP-0-P2-BASELINE-ALIGNMENT.md",
    DATA / "DP-0-HANDOFF.md",
    DATA / "DATA-PLATFORM-ARCHITECTURE-V1.md",
    DATA / "PLATFORM-EVENT-CONTRACT-V1.md",
    DATA / "BEHAVIOR-EVENT-TAXONOMY-V1.md",
    DATA / "EVENT-IDEMPOTENCY-AND-FINGERPRINT-V1.md",
    DATA / "EVENT-RETRY-QUARANTINE-REPLAY-V1.md",
    DATA / "DATA-LINEAGE-AND-SNAPSHOT-V1.md",
    DATA / "DATA-RETENTION-AND-PRIVACY-V1.md",
    DATA / "P0-RECOMMENDATION-EVENT-ADAPTER-V1.md",
    DATA / "DP-5-PROJECTION-AND-SNAPSHOT-FOUNDATION.md",
    DATA / "DP-5-PROJECTION-MATRIX.md",
    DATA / "DP-5-HANDOFF.md",
    DATA / "DP-6-DATA-QUALITY-AND-LINEAGE-VALIDATION-HARDENING.md",
    DATA / "DP-6-QUALITY-MATRIX.md",
    DATA / "DP-6-HANDOFF.md",
    DATA / "DP-7-CROSS-TRACK-INTEGRATION-VALIDATION.md",
    DATA / "DP-7-INTEGRATION-MATRIX.md",
    DATA / "DP-7-AUTHORITY-MATRIX.md",
    DATA / "DP-7-PRIVACY-RETENTION-MATRIX.md",
    DATA / "DP-7-HANDOFF.md",
    DATA / "DATA-PLATFORM-TECHNICAL-BASELINE-V1.md",
    DATA / "DATA-PLATFORM-AUTHORITY-CLOSURE-V1.md",
    DATA / "DATA-PLATFORM-PRODUCTION-READINESS-GAPS-V1.md",
    DATA / "DATA-PLATFORM-PRODUCTION-ACTIVATION-DEPENDENCIES-V1.md",
    DATA / "DATA-PLATFORM-CHANGE-POLICY-V1.md",
    DATA / "DATA-PLATFORM-CLOSURE-HANDOFF.md",
    DATA / "HANDOFF-DATA-TO-RECOMMENDATION-V1.md",
    DATA / "HANDOFF-DATA-TO-INTELLIGENCE-V1.md",
    DATA / "HANDOFF-DATA-TO-SEARCH-V1.md",
    DATA / "HANDOFF-DATA-TO-OPERATIONS-V1.md",
    DATA / "HANDOFF-DATA-TO-RELIABILITY-V1.md",
]


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in REQUIRED:
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing or empty required file: {path.relative_to(ROOT)}")

system_contract = (GOV / "JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md").read_text(encoding="utf-8")
for marker in (
    "V1.3 / SC-2 POST-DP-CLOSURE",
    "journey-connect-db-v2.7/01..52",
    "SQL `53+`",
    "RCA-0 Recommendation Data Consumer Contract & Fixture Alignment",
    "PRODUCTION_ACTIVATION: NOT_AUTHORIZED",
):
    if marker not in system_contract:
        fail(f"system contract marker missing: {marker}")

governance = (GOV / "JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md").read_text(encoding="utf-8")
for marker in (
    "Data Platform technical closure [COMPLETE]",
    "RCA-0 Recommendation Data Consumer Contract & Fixture Alignment",
    "RP remains reserved for Reliability Platform",
    "DB_CHANGE_NOT_REQUIRED",
):
    if marker not in governance:
        fail(f"governance marker missing: {marker}")

registry = (GOV / "SC-PLATFORM-REGISTRY.md").read_text(encoding="utf-8")
for marker in (
    "ACTIVE / DATA_PLATFORM_CLOSED / RCA-0 RESERVED",
    "data-cross-track-integration-policy-v1",
    "recommendation-data-consumer-alignment-v1",
    "recommendation-profile-input-consumer-v1",
    "experiment-outcome-input-consumer-v1",
    "recommendation-data-consumer-fixture-v1",
    "`48..52`",
    "`53+`",
):
    if marker not in registry:
        fail(f"registry marker missing: {marker}")

for number in range(1, 53):
    if len(list(SQL.glob(f"{number:02d}_*.sql"))) != 1:
        fail(f"canonical SQL {number:02d} missing or duplicated")
if list(SQL.glob("5[3-9]_*.sql")) or list(SQL.glob("[6-9][0-9]_*.sql")):
    fail("SQL 53+ remains unallocated")

for path in OUT.glob("*.tsv"):
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.reader(handle, delimiter="\t"))
    if not rows:
        fail(f"empty historical SC evidence: {path.name}")

next_track_verifier = ROOT / "verification/sc-next-track/run_sc_next_track_reconciliation.py"
if not next_track_verifier.is_file():
    fail("next-track verifier missing")
subprocess.run([sys.executable, str(next_track_verifier)], cwd=ROOT, check=True)

print("SC baseline reconciliation through Data closure and RCA-0 allocation: PASS")
