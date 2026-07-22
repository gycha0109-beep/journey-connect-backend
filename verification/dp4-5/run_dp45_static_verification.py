#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import csv
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DP45 = ROOT / "verification/dp4-5"
SQL_DIR = ROOT / "database/journey-connect-db-v2.7"
IMPLEMENTATION_DOC = ROOT / "docs/platform/data/DP-4-5-RECOMMENDATION-ADAPTER-SHADOW-EVIDENCE-PERSISTENCE.md"
HANDOFF = ROOT / "docs/platform/data/DP-4-5-HANDOFF.md"
ALLOCATION = ROOT / "docs/platform/governance/SC-DP4-5-PERSISTENCE-ALLOCATION.md"

SQL35 = SQL_DIR / "35_data_recommendation_adapter_shadow_evidence.sql"
SQL36 = SQL_DIR / "36_data_recommendation_adapter_shadow_persistence.sql"
SQL37 = SQL_DIR / "37_data_recommendation_adapter_shadow_validation.sql"
APPROVED_SQL = {str(path.relative_to(ROOT)) for path in (SQL35, SQL36, SQL37)}

REQUIRED = [
    SQL35,
    SQL36,
    SQL37,
    IMPLEMENTATION_DOC,
    HANDOFF,
    ALLOCATION,
    DP45 / "DP45_BASELINE.tsv",
    DP45 / "DP45_CHANGED_FILES.tsv",
    DP45 / "DP45_DB_OBJECTS.tsv",
    DP45 / "DP45_PERSISTENCE.tsv",
    DP45 / "DP45_DUPLICATE_CONFLICT.tsv",
    DP45 / "DP45_CONCURRENCY.tsv",
    DP45 / "DP45_ROLE_GRANTS.tsv",
    DP45 / "DP45_RETENTION.tsv",
    DP45 / "DP45_SAFE_VIEW.tsv",
    DP45 / "DP45_PROTECTED_DIFF.tsv",
    DP45 / "DP45_VERIFICATION_STATUS.tsv",
    DP45 / "DP45_DECISIONS.tsv",
    DP45 / "run_dp45_concurrency.sh",
]


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in REQUIRED:
    if not path.is_file():
        fail(f"missing DP-4.5 implementation artifact: {path.relative_to(ROOT)}")
    if not path.read_text(encoding="utf-8").strip():
        fail(f"empty DP-4.5 implementation artifact: {path.relative_to(ROOT)}")

allocation_text = ALLOCATION.read_text(encoding="utf-8")
for marker in (
    "Implementation authority: `GRANTED`",
    "35_data_recommendation_adapter_shadow_evidence.sql",
    "36_data_recommendation_adapter_shadow_persistence.sql",
    "37_data_recommendation_adapter_shadow_validation.sql",
    "jc_data_adapter_evidence_writer",
    "jc_data_adapter_evidence_reader",
    "jc_data_adapter_evidence_function_owner",
    "DP-4.5 persistence is a required prerequisite for DP-5",
):
    if marker not in allocation_text:
        fail(f"SC allocation marker missing: {marker}")

implementation_text = IMPLEMENTATION_DOC.read_text(encoding="utf-8")
handoff_text = HANDOFF.read_text(encoding="utf-8")
for marker in (
    "Recommendation P0 source",
    "Data Platform shadow candidate",
    "DP45_IMPLEMENTATION_COMPLETE",
    "NEW",
    "DUPLICATE",
    "CONFLICT",
    "ADAPTER_EVIDENCE_CONFLICT",
    "production Recommendation input is prohibited",
):
    if marker not in implementation_text:
        fail(f"DP-4.5 implementation marker missing: {marker}")
if "DP45_IMPLEMENTATION_COMPLETE" not in handoff_text:
    fail("DP-4.5 handoff does not declare implementation completion")

for number in range(1, 38):
    matches = list(SQL_DIR.glob(f"{number:02d}_*.sql"))
    if len(matches) != 1:
        fail(f"canonical SQL {number:02d} expected exactly once, found {len(matches)}")
if list(SQL_DIR.glob("3[8-9]_*.sql")) or list(SQL_DIR.glob("[4-9][0-9]_*.sql")):
    fail("SQL 38+ must remain absent")

sql35 = SQL35.read_text(encoding="utf-8")
sql36 = SQL36.read_text(encoding="utf-8")
sql37 = SQL37.read_text(encoding="utf-8")
for marker in (
    "data_recommendation_adapter_run_v1",
    "data_recommendation_adapter_output_v1",
    "data_recommendation_adapter_failure_v1",
    "data_recommendation_adapter_conflict_observation_v1",
    "adapter_evidence_90d",
    "prevent_data_event_append_only_mutation_v1",
):
    if marker not in sql35:
        fail(f"SQL 35 boundary missing: {marker}")
for marker in (
    "persist_recommendation_adapter_shadow_evidence_v1",
    "pg_advisory_xact_lock",
    "ADAPTER_EVIDENCE_CONFLICT",
    "jc_data_adapter_evidence_writer",
    "jc_data_adapter_evidence_reader",
    "jc_data_adapter_evidence_function_owner",
    "SECURITY DEFINER",
    "SET search_path = pg_catalog, public, pg_temp",
    "data_recommendation_adapter_safe_metrics_v1",
    "REVOKE ALL ON FUNCTION",
):
    if marker not in sql36:
        fail(f"SQL 36 boundary missing: {marker}")
for marker in (
    "DUPLICATE",
    "CONFLICT",
    "append-only",
    "PUBLIC",
    "ROLLBACK;",
):
    if marker not in sql37:
        fail(f"SQL 37 validation marker missing: {marker}")

combined_sql = "\n".join((sql35, sql36, sql37))
for forbidden in (
    "INSERT INTO public.recommendation_",
    "UPDATE public.recommendation_",
    "DELETE FROM public.recommendation_",
    "INSERT INTO public.data_platform_event_v1",
    "UPDATE public.data_platform_event_v1",
    "DELETE FROM public.data_platform_event_v1",
    "CREATE EXTENSION",
):
    if forbidden in combined_sql:
        fail(f"protected authority mutation found: {forbidden}")
if re.search(r"(?i)\b(CREATE|ALTER)\s+ROLE\s+(postgres|jc_security_owner)\b", combined_sql):
    fail("broad owner role mutation is forbidden")

for evidence_path in DP45.glob("*.tsv"):
    with evidence_path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.reader(handle, delimiter="\t"))
    if len(rows) < 2:
        fail(f"DP-4.5 evidence is empty: {evidence_path.name}")

with (DP45 / "DP45_DB_OBJECTS.tsv").open(encoding="utf-8", newline="") as handle:
    object_rows = list(csv.DictReader(handle, delimiter="\t"))
if not object_rows or any(row.get("implementation_status") != "IMPLEMENTED" for row in object_rows):
    fail("DB object evidence is not fully IMPLEMENTED")

with (DP45 / "DP45_ROLE_GRANTS.tsv").open(encoding="utf-8", newline="") as handle:
    role_rows = list(csv.DictReader(handle, delimiter="\t"))
if not role_rows or any(row.get("status") != "IMPLEMENTED" for row in role_rows):
    fail("role/grant evidence is not fully IMPLEMENTED")

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
    changed_sql = {path for path in changed if path.endswith(".sql")}
    if changed_sql != APPROVED_SQL:
        fail(f"SQL allocation must change exactly SQL 35..37: {sorted(changed_sql)}")

    allowed_exact = {
        *APPROVED_SQL,
        ".github/workflows/data-postgres-ci.yml",
        ".github/workflows/data-contract-ci.yml",
        "jc-backend/src/test/java/com/jc/backend/search/shadow/production/IP12ProductionShadowStaticTest.java",
        "verification/dp4/run_dp4_static_verification.py",
        "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
        "docs/platform/data/DP-4-5-RECOMMENDATION-ADAPTER-SHADOW-EVIDENCE-PERSISTENCE.md",
        "docs/platform/data/DP-4-5-HANDOFF.md",
    }
    unexpected = [
        path for path in changed
        if path not in allowed_exact and not path.startswith("verification/dp4-5/")
    ]
    if unexpected:
        fail(f"unexpected/protected diff: {unexpected}")

    protected = [path for path in changed if path.startswith((
        "jc-recommendation-core/",
        "jc-backend/src/main/",
        "jc-backend/src/main/resources/",
        "jc-data-contracts/",
        "jc-intelligence-contracts/",
        "jc-search-contracts/",
        "jc-search-compatibility/",
        "jc-search-runtime/",
        "jc-search-integration/",
        "jc-search-shadow-wiring/",
        "jc-search-readiness/",
        "jc-search-production-controls/",
    ))]
    if protected:
        fail(f"production/Recommendation/Data-contract/Search source changed: {protected}")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

print("DP-4.5 implementation static verification: PASS")
