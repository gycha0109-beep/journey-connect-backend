#!/usr/bin/env python3
from __future__ import annotations

import csv
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DP7 = ROOT / "verification/dp7"
SQL_DIR = ROOT / "database/journey-connect-db-v2.7"
ALLOCATION = ROOT / "docs/platform/governance/SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md"
FOUNDATION = ROOT / "docs/platform/data/DP-7-CROSS-TRACK-INTEGRATION-VALIDATION.md"
MATRIX_DOC = ROOT / "docs/platform/data/DP-7-INTEGRATION-MATRIX.md"
AUTHORITY_DOC = ROOT / "docs/platform/data/DP-7-AUTHORITY-MATRIX.md"
PRIVACY_DOC = ROOT / "docs/platform/data/DP-7-PRIVACY-RETENTION-MATRIX.md"
HANDOFF = ROOT / "docs/platform/data/DP-7-HANDOFF.md"
DECISIONS = ROOT / "docs/platform/governance/SC-DECISION-REGISTER.md"
REGISTRY = ROOT / "docs/platform/governance/SC-PLATFORM-REGISTRY.md"
SC_HANDOFF = ROOT / "docs/platform/governance/SC-HANDOFF.md"
INTEGRATION_JAVA = ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/integration"

EVIDENCE = [
    "DP7_BASELINE.tsv",
    "DP7_CHANGED_FILES.tsv",
    "DP7_CONTRACT_INVENTORY.tsv",
    "DP7_INTEGRATION_MATRIX.tsv",
    "DP7_RECOMMENDATION_COMPATIBILITY.tsv",
    "DP7_INTELLIGENCE_COMPATIBILITY.tsv",
    "DP7_SEARCH_COMPATIBILITY.tsv",
    "DP7_IDENTITY_BOUNDARY.tsv",
    "DP7_AUTHORITY_BOUNDARY.tsv",
    "DP7_PRIVACY_BOUNDARY.tsv",
    "DP7_RETENTION_BOUNDARY.tsv",
    "DP7_QUALITY_VERDICT_BOUNDARY.tsv",
    "DP7_FINGERPRINT_BOUNDARY.tsv",
    "DP7_DUPLICATE_CONFLICT.tsv",
    "DP7_CONCURRENCY.tsv",
    "DP7_ROLE_GRANTS.tsv",
    "DP7_SAFE_VIEW.tsv",
    "DP7_PROTECTED_DIFF.tsv",
    "DP7_VERIFICATION_STATUS.tsv",
    "DP7_DECISIONS.tsv",
]

MATRIX_HEADER = [
    "source_track", "source_contract", "source_schema_version", "source_field",
    "source_semantic", "source_unit", "source_authority", "target_track",
    "target_contract", "target_schema_version", "target_field", "target_semantic",
    "target_unit", "target_authority", "mapping_rule", "required", "nullable",
    "identity_namespace", "lineage_required", "quality_requirement", "privacy_class",
    "retention_class", "compatibility_status", "failure_code",
]

ALLOWED_COMPATIBILITY = {
    "COMPATIBLE", "INCOMPATIBLE", "CONDITIONALLY_COMPATIBLE", "NOT_APPLICABLE", "UNVERIFIED"
}

ALLOWED_PREFIXES = (
    ".github/workflows/data-postgres-ci.yml",
    ".github/workflows/dp7-allocation-ci.yml",
    "docs/platform/data/DP-7-",
    "docs/platform/governance/SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md",
    "docs/platform/governance/SC-DECISION-REGISTER.md",
    "docs/platform/governance/SC-PLATFORM-REGISTRY.md",
    "docs/platform/governance/SC-HANDOFF.md",
    "verification/dp6/run_dp6_allocation_verification.py",
    "verification/dp6/run_dp6_static_verification.py",
    "verification/dp7/",
    "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
)


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in [ALLOCATION, FOUNDATION, MATRIX_DOC, AUTHORITY_DOC, PRIVACY_DOC, HANDOFF,
             DECISIONS, REGISTRY, SC_HANDOFF, *(DP7 / name for name in EVIDENCE)]:
    if not path.is_file() or not path.read_text(encoding="utf-8").strip():
        fail(f"missing or empty DP-7 artifact: {path.relative_to(ROOT)}")

allocation = ALLOCATION.read_text(encoding="utf-8")
for marker in (
    "PROPOSED / MERGE REQUIRED",
    "Implementation authority: `NOT GRANTED`",
    "69b2f9619733e8e6068a23bb149c2aaf41f23fc9",
    "48_cross_track_integration_validation_foundation.sql",
    "49_cross_track_contract_mapping_and_boundary_evidence.sql",
    "50_cross_track_integration_verdict_and_conflict.sql",
    "51_cross_track_integration_persistence_roles_and_safe_view.sql",
    "52_cross_track_integration_validation.sql",
    "jc_data_integration_writer",
    "jc_data_integration_reader",
    "jc_data_integration_function_owner",
    "data-cross-track-integration-policy-v1",
    "CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT",
    "No SQL `48+` is present",
    "DP7_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION",
):
    if marker not in allocation:
        fail(f"allocation marker missing: {marker}")

for number in range(1, 48):
    matches = list(SQL_DIR.glob(f"{number:02d}_*.sql"))
    if len(matches) != 1:
        fail(f"canonical SQL {number:02d} expected exactly once, found {len(matches)}")
if list(SQL_DIR.glob("4[8-9]_*.sql")) or list(SQL_DIR.glob("[5-9][0-9]_*.sql")):
    fail("SQL 48+ must remain absent in the allocation-only phase")

if INTEGRATION_JAVA.exists() and any(INTEGRATION_JAVA.rglob("*.java")):
    fail("DP-7 Java implementation is not authorized before allocation merge")

with (DP7 / "DP7_INTEGRATION_MATRIX.tsv").open(encoding="utf-8", newline="") as handle:
    rows = list(csv.reader(handle, delimiter="\t"))
if not rows or rows[0] != MATRIX_HEADER:
    fail("integration matrix header does not match the machine contract")
if len(rows) < 4:
    fail("integration matrix must cover Recommendation, Intelligence and Search")
for index, row in enumerate(rows[1:], start=2):
    if len(row) != len(MATRIX_HEADER):
        fail(f"integration matrix row {index} has {len(row)} columns")
    if row[22] not in ALLOWED_COMPATIBILITY:
        fail(f"integration matrix row {index} has invalid compatibility status: {row[22]}")

matrix_targets = {row[7] for row in rows[1:]}
if not {"Recommendation", "Intelligence", "Search"}.issubset(matrix_targets):
    fail(f"integration matrix target coverage incomplete: {sorted(matrix_targets)}")

for name in EVIDENCE:
    with (DP7 / name).open(encoding="utf-8", newline="") as handle:
        evidence_rows = list(csv.reader(handle, delimiter="\t"))
    if len(evidence_rows) < 2:
        fail(f"empty evidence: {name}")

foundation = FOUNDATION.read_text(encoding="utf-8")
for marker in (
    "CONDITIONALLY_COMPATIBLE",
    "INCONCLUSIVE",
    "quality_verdict_missing",
    "cross_track_identity_binding_missing",
    "cross_track_write_authority_violation",
    "cross_track_pii_exposure",
    "integration-input-sha256-v1",
    "FullCrossTrackIntegrationValidator",
    "DP7_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION",
):
    if marker not in foundation:
        fail(f"foundation marker missing: {marker}")

registry = REGISTRY.read_text(encoding="utf-8")
for marker in (
    "DP-7 ALLOCATION PROPOSED",
    "data-cross-track-integration-policy-v1",
    "integration-verdict-sha256-v1",
    "jc_data_integration_writer",
    "PROPOSED / NOT CREATED",
    "53+",
):
    if marker not in registry:
        fail(f"registry marker missing: {marker}")

try:
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

    for path in filter(None, changed):
        if not any(path == prefix or path.startswith(prefix) for prefix in ALLOWED_PREFIXES):
            fail(f"unexpected/protected changed file: {path}")

    changed_sql = [path for path in changed if path.endswith(".sql")]
    if changed_sql:
        fail(f"allocation-only phase cannot change SQL: {changed_sql}")

    protected_prefixes = (
        "jc-data-contracts/src/main/",
        "jc-backend/src/main/",
        "jc-recommendation-core/",
        "jc-intelligence-contracts/",
        "jc-search-contracts/",
        "jc-search-compatibility/",
        "jc-search-runtime/",
        "jc-search-integration/",
        "jc-search-shadow-wiring/",
        "jc-search-readiness/",
        "jc-search-production-controls/",
    )
    protected = [path for path in changed if path.startswith(protected_prefixes)]
    if protected:
        fail(f"protected implementation source changed: {protected}")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

print("DP-7 allocation, contract inventory and protected diff verification: PASS")
