#!/usr/bin/env python3
from __future__ import annotations

import csv
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = "c528f6fb0942389b70a348cb9aa672eb7819a392"
DOCS = [
    "docs/platform/data/DATA-PLATFORM-TECHNICAL-BASELINE-V1.md",
    "docs/platform/data/DATA-PLATFORM-AUTHORITY-CLOSURE-V1.md",
    "docs/platform/data/DATA-PLATFORM-PRODUCTION-READINESS-GAPS-V1.md",
    "docs/platform/data/DATA-PLATFORM-PRODUCTION-ACTIVATION-DEPENDENCIES-V1.md",
    "docs/platform/data/DATA-PLATFORM-CHANGE-POLICY-V1.md",
    "docs/platform/data/HANDOFF-DATA-TO-RECOMMENDATION-V1.md",
    "docs/platform/data/HANDOFF-DATA-TO-INTELLIGENCE-V1.md",
    "docs/platform/data/HANDOFF-DATA-TO-SEARCH-V1.md",
    "docs/platform/data/HANDOFF-DATA-TO-OPERATIONS-V1.md",
    "docs/platform/data/HANDOFF-DATA-TO-RELIABILITY-V1.md",
    "docs/platform/data/DATA-PLATFORM-CLOSURE-HANDOFF.md",
]
GOV = [
    "docs/platform/governance/JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md",
    "docs/platform/governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md",
    "docs/platform/governance/SC-DECISION-REGISTER.md",
    "docs/platform/governance/SC-PLATFORM-REGISTRY.md",
    "docs/platform/governance/SC-HANDOFF.md",
    "docs/platform/governance/SC-DATA-PLATFORM-TECHNICAL-CLOSURE.md",
    "docs/platform/data/DP-7-HANDOFF.md",
]
EVIDENCE = [
    "DATA_PLATFORM_CLOSURE_BASELINE.tsv",
    "DATA_PLATFORM_PHASE_STATUS.tsv",
    "DATA_PLATFORM_OBJECT_INVENTORY.tsv",
    "DATA_PLATFORM_CONTRACT_INVENTORY.tsv",
    "DATA_PLATFORM_AUTHORITY_MATRIX.tsv",
    "DATA_PLATFORM_ROLE_GRANT_MATRIX.tsv",
    "DATA_PLATFORM_POLICY_INVENTORY.tsv",
    "DATA_PLATFORM_FINGERPRINT_INVENTORY.tsv",
    "DATA_PLATFORM_RETENTION_INVENTORY.tsv",
    "DATA_PLATFORM_PRODUCTION_GAPS.tsv",
    "DATA_PLATFORM_RECOMMENDATION_HANDOFF.tsv",
    "DATA_PLATFORM_INTELLIGENCE_HANDOFF.tsv",
    "DATA_PLATFORM_SEARCH_HANDOFF.tsv",
    "DATA_PLATFORM_OPERATIONS_HANDOFF.tsv",
    "DATA_PLATFORM_RELIABILITY_HANDOFF.tsv",
    "DATA_PLATFORM_ACTIVATION_GATES.tsv",
    "DATA_PLATFORM_CHANGE_POLICY.tsv",
    "DATA_PLATFORM_PROTECTED_STATE.tsv",
    "DATA_PLATFORM_POST_MERGE_VALIDATION.tsv",
    "DATA_PLATFORM_CLOSURE_DECISIONS.tsv",
    "DATA_PLATFORM_CLOSURE_STATUS.tsv",
]
EV = ROOT / "verification/data-platform-closure"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def read_tsv(name: str) -> list[dict[str, str]]:
    path = EV / name
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        if not reader.fieldnames or any(not field.strip() for field in reader.fieldnames):
            fail(f"invalid evidence header: {name}")
        rows = list(reader)
    if not rows:
        fail(f"empty evidence: {name}")
    width = len(reader.fieldnames)
    if any(len(row) != width for row in rows):
        fail(f"evidence column mismatch: {name}")
    signatures = [tuple(row.get(field, "") for field in reader.fieldnames) for row in rows]
    if len(signatures) != len(set(signatures)):
        fail(f"duplicate evidence row: {name}")
    return rows


for rel in DOCS + GOV:
    path = ROOT / rel
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing document: {rel}")

actual_evidence = {path.name for path in EV.glob("*.tsv")}
expected_evidence = set(EVIDENCE)
if actual_evidence != expected_evidence:
    fail(
        "closure evidence set mismatch: "
        f"missing={sorted(expected_evidence - actual_evidence)}, "
        f"unexpected={sorted(actual_evidence - expected_evidence)}"
    )
for name in EVIDENCE:
    read_tsv(name)

combined = "\n".join((ROOT / rel).read_text(encoding="utf-8") for rel in DOCS + GOV)
for marker in (
    MAIN,
    "DP-0~DP-7",
    "SQL `01..52`",
    "SQL `53+`",
    "CONDITIONALLY_COMPATIBLE",
    "INCONCLUSIVE",
    "Operations",
    "Reliability",
    "Production shadow: DISABLED",
    "Sampling: 0 BPS",
    "Cohort: EMPTY",
    "historical migration rewrite",
    "NOT_AUTHORIZED",
):
    if marker not in combined:
        fail(f"marker missing: {marker}")

handoff = (ROOT / "docs/platform/data/DATA-PLATFORM-CLOSURE-HANDOFF.md").read_text(encoding="utf-8")
sc_closure = (ROOT / "docs/platform/governance/SC-DATA-PLATFORM-TECHNICAL-CLOSURE.md").read_text(encoding="utf-8")
if "`PR_READY_FOR_USER_APPROVAL / MERGE PENDING`" not in handoff:
    fail("closure handoff is not bound to pre-merge Ready status")
if "`PR_READY_FOR_USER_APPROVAL / MERGE REQUIRED`" not in sc_closure:
    fail("SC closure is not bound to pre-merge Ready status")
for forbidden in (
    "`DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE / PR MERGE PENDING`",
    "`TECHNICAL CLOSURE COMPLETE / PR MERGE REQUIRED`",
):
    if forbidden in combined:
        fail(f"premature closure completion marker present: {forbidden}")

status_rows = read_tsv("DATA_PLATFORM_CLOSURE_STATUS.tsv")
if len(status_rows) != 1:
    fail("closure status must contain exactly one row")
status = status_rows[0]
expected_status = {
    "authoritative_main": MAIN,
    "closure_pr_head": "SELF_HEAD",
    "status": "PR_READY_FOR_USER_APPROVAL",
    "verification_executed": "true",
    "verification_passed": "true",
    "production_ready": "false",
    "production_approved": "false",
}
for field, expected in expected_status.items():
    if status.get(field) != expected:
        fail(f"closure status mismatch: {field}={status.get(field)!r}, expected={expected!r}")

validation_rows = {row["validation"]: row for row in read_tsv("DATA_PLATFORM_POST_MERGE_VALIDATION.tsv")}
if validation_rows.get("main_push_ci", {}).get("status") != "NOT_AVAILABLE":
    fail("main push CI must remain NOT_AVAILABLE")
if validation_rows.get("merge_local", {}).get("status") != "NOT_EXECUTED":
    fail("merge-commit local checkout must remain NOT_EXECUTED")
final_validation = validation_rows.get("closure_pr_final")
if not final_validation:
    fail("final closure PR validation evidence missing")
if final_validation.get("target_sha") != "SELF_HEAD" or final_validation.get("status") != "PASS_AT_READY_TRANSITION":
    fail("final closure PR validation is not SELF_HEAD-bound")

protected_rows = read_tsv("DATA_PLATFORM_PROTECTED_STATE.tsv")
if any(row.get("status", "").startswith("CANDIDATE") for row in protected_rows):
    fail("protected-state evidence still contains candidate status")
activation_rows = {row["gate"]: row for row in read_tsv("DATA_PLATFORM_ACTIVATION_GATES.tsv")}
if activation_rows.get("GATE-1", {}).get("status") != "PR_READY_FOR_USER_APPROVAL":
    fail("GATE-1 must remain pre-merge PR_READY_FOR_USER_APPROVAL")

sql = ROOT / "database/journey-connect-db-v2.7"
for number in range(1, 53):
    if len(list(sql.glob(f"{number:02d}_*.sql"))) != 1:
        fail(f"SQL {number:02d} missing or duplicated")
if list(sql.glob("5[3-9]_*.sql")) or list(sql.glob("[6-9][0-9]_*.sql")):
    fail("SQL 53+ present")

prod = (ROOT / "jc-backend/src/main/resources/application-prod.yml").read_text(encoding="utf-8")
for marker in (
    "enabled: ${JC_SEARCH_SHADOW_PRODUCTION_ENABLED:false}",
    "kill-switch: ${JC_SEARCH_SHADOW_PRODUCTION_KILL_SWITCH:true}",
    "sampling-bps: ${JC_SEARCH_SHADOW_PRODUCTION_SAMPLING_BPS:0}",
    "allowlist-hashes: ${JC_SEARCH_SHADOW_PRODUCTION_ALLOWLIST_HASHES:}",
):
    if marker not in prod:
        fail(f"production default missing: {marker}")

allowed = set(
    DOCS
    + GOV
    + [
        "verification/data-platform-closure/run_data_platform_closure_verification.py",
        ".github/workflows/data-platform-closure-ci.yml",
        ".github/workflows/data-contract-ci.yml",
        ".github/workflows/data-postgres-ci.yml",
        ".github/workflows/dp6-allocation-ci.yml",
        ".github/workflows/dp7-allocation-ci.yml",
        ".github/workflows/backend-pr-ci.yml",
        ".github/workflows/recommendation-p0-db-ci.yml",
        ".github/workflows/sc-baseline-reconciliation.yml",
        "verification/dp7/run_dp7_allocation_verification.py",
        "verification/dp7/run_dp7_static_verification.py",
        "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
        "docs/platform/governance/SC-RACI.md",
        "docs/platform/governance/SC-2-POST-DP-CLOSURE-NEXT-TRACK-BASELINE-RECONCILIATION.md",
    ]
)
allowed_prefixes = (
    "docs/platform/governance/sc-next-track/",
    "verification/sc-next-track/",
)
subprocess.run(
    ["git", "fetch", "origin", "main", "--depth=1"],
    cwd=ROOT,
    check=False,
    stdout=subprocess.DEVNULL,
    stderr=subprocess.DEVNULL,
)
changed = subprocess.run(
    ["git", "diff", "--name-only", "origin/main...HEAD"],
    cwd=ROOT,
    check=True,
    text=True,
    capture_output=True,
).stdout.splitlines()
for rel in filter(None, changed):
    if rel.startswith("verification/data-platform-closure/"):
        continue
    if any(rel.startswith(prefix) for prefix in allowed_prefixes):
        continue
    if rel not in allowed:
        fail(f"unexpected diff: {rel}")
for rel in changed:
    if rel.startswith(
        (
            "database/",
            "jc-backend/src/main/",
            "jc-backend/src/main/resources/",
            "jc-recommendation-core/",
            "jc-intelligence-contracts/",
            "jc-search-",
        )
    ):
        fail(f"protected change: {rel}")

print("Data Platform technical closure documents, exact evidence set, pre-merge status and protected state: PASS")
