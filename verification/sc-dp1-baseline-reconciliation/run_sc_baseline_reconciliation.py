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
    GOV / "sc-next-track/SC-3-RCA-1-ENTRY-AUTHORIZATION-AND-BASELINE-ALLOCATION.md",
    GOV / "sc-next-track/SC-4-RCA-1B-ENTRY-AUTHORIZATION-AND-EXECUTION-BOUNDARY.md",
    GOV / "sc-next-track/37-RCA-1B-IMPLEMENTATION-HANDOFF-PROMPT.md",
    GOV / "sc-next-track/SC-5-RCA-2-ENTRY-AUTHORIZATION-AND-EXECUTION-BOUNDARY.md",
    GOV / "sc-next-track/56-RCA-2-IMPLEMENTATION-HANDOFF-PROMPT.md",
    DATA / "DP-0-DATA-PLATFORM-CONTRACT-FOUNDATION.md",
    DATA / "DP-0-P2-BASELINE-ALIGNMENT.md",
    DATA / "DP-0-HANDOFF.md",
    DATA / "DATA-PLATFORM-ARCHITECTURE-V1.md",
    DATA / "DP-5-PROJECTION-AND-SNAPSHOT-FOUNDATION.md",
    DATA / "DP-6-DATA-QUALITY-AND-LINEAGE-VALIDATION-HARDENING.md",
    DATA / "DP-7-CROSS-TRACK-INTEGRATION-VALIDATION.md",
    DATA / "DATA-PLATFORM-TECHNICAL-BASELINE-V1.md",
    DATA / "DATA-PLATFORM-AUTHORITY-CLOSURE-V1.md",
    DATA / "DATA-PLATFORM-CLOSURE-HANDOFF.md",
]


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in REQUIRED:
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing or empty required file: {path.relative_to(ROOT)}")

system_contract = (GOV / "JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md").read_text(encoding="utf-8")
for marker in (
    "V1.6 / SC-5 RCA-2 ENTRY",
    "3efbf96ebf25ae1645a62f35269c4b569425a9ca",
    "dbb6b5397ad0fe675856b195e280faf9a0f3030c",
    "journey-connect-db-v2.7/01..52",
    "RCA1B_NONPRODUCTION_READONLY_RECONCILIATION_COMPLETE",
    "CROSS_VERSION_RESULT_EQUIVALENCE=PASS",
    "RCA2_ENTRY_AUTHORIZED",
    "FEATURE_FLAG_DEFAULT=OFF",
    "PRODUCTION_ACTIVATION=NOT_AUTHORIZED",
):
    if marker not in system_contract:
        fail(f"system contract marker missing: {marker}")

governance = (GOV / "JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md").read_text(encoding="utf-8")
for marker in (
    "RCA-0 [COMPLETE / MERGED]",
    "RCA-1 [COMPLETE]",
    "RCA-1B [COMPLETE / MERGED]",
    "SC-5 RCA-2 entry [AUTHORIZED]",
    "reserved for Reliability Platform",
    "SQL_ALLOCATION=NOT_REQUIRED",
    "ASYNC_POST_RESPONSE_SHADOW",
):
    if marker not in governance:
        fail(f"governance marker missing: {marker}")

registry = (GOV / "SC-PLATFORM-REGISTRY.md").read_text(encoding="utf-8")
for marker in (
    "RCA2_ENTRY_AUTHORIZED",
    "recommendation-shadow-reconciliation-v1",
    "recommendation-runtime-dark-read-query-registry-v1",
    "FEATURE_FLAG_DEFAULT=OFF",
    "INITIAL_TRAFFIC_PERCENT=0",
    "`01..52`",
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
        evidence_rows = list(csv.reader(handle, delimiter="\t"))
    if not evidence_rows:
        fail(f"empty historical SC evidence: {path.name}")

historical_verifiers = (
    ROOT / "verification/sc-next-track/run_sc_next_track_reconciliation.py",
    ROOT / "verification/sc-next-track/rca1-entry/run_sc_rca1_entry_verification.py",
    ROOT / "verification/sc-next-track/rca1b-entry/run_sc_rca1b_entry_verification.py",
)
for verifier in historical_verifiers:
    if not verifier.is_file():
        fail(f"historical verifier missing: {verifier.relative_to(ROOT)}")

if (ROOT / ".git/shallow").exists():
    subprocess.run(["git", "fetch", "--unshallow", "origin"], cwd=ROOT, check=True)
else:
    subprocess.run(["git", "fetch", "origin", "main"], cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

current_verifier = ROOT / "verification/sc-next-track/rca2-entry/run_sc_rca2_entry_verification.py"
if not current_verifier.is_file():
    fail(f"current verifier missing: {current_verifier.relative_to(ROOT)}")
subprocess.run([sys.executable, str(current_verifier)], cwd=ROOT, check=True)

print("SC baseline reconciliation through RCA-1B completion and RCA-2 entry authorization: PASS")
