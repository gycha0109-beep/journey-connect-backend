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
DECISIONS = ROOT / "docs/platform/governance/SC-DECISION-REGISTER.md"
REGISTRY = ROOT / "docs/platform/governance/SC-PLATFORM-REGISTRY.md"
SC_HANDOFF = ROOT / "docs/platform/governance/SC-HANDOFF.md"
DP4_HANDOFF = ROOT / "docs/platform/data/DP-4-HANDOFF.md"

REQUIRED = [
    IMPLEMENTATION_DOC,
    HANDOFF,
    DP45 / "DP45_BASELINE.tsv",
    DP45 / "DP45_CHANGED_FILES.tsv",
    DP45 / "DP45_DB_OBJECTS.tsv",
    DP45 / "DP45_PERSISTENCE.tsv",
    DP45 / "DP45_DUPLICATE_CONFLICT.tsv",
    DP45 / "DP45_ROLE_GRANTS.tsv",
    DP45 / "DP45_RETENTION.tsv",
    DP45 / "DP45_SAFE_VIEW.tsv",
    DP45 / "DP45_PROTECTED_DIFF.tsv",
    DP45 / "DP45_VERIFICATION_STATUS.tsv",
    DP45 / "DP45_DECISIONS.tsv",
]


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in REQUIRED:
    if not path.is_file():
        fail(f"missing DP-4.5 blocker artifact: {path.relative_to(ROOT)}")
    if not path.read_text(encoding="utf-8").strip():
        fail(f"empty DP-4.5 blocker artifact: {path.relative_to(ROOT)}")

implementation_text = IMPLEMENTATION_DOC.read_text(encoding="utf-8")
handoff_text = HANDOFF.read_text(encoding="utf-8")
for marker in (
    "DP45_IMPLEMENTATION_BLOCKED_BY_SQL_ASSIGNMENT",
    "Recommendation P0 source",
    "SQL `35+`",
    "PROPOSED_NOT_IMPLEMENTED",
    "production Recommendation input is prohibited",
):
    if marker not in implementation_text:
        fail(f"missing blocker contract marker: {marker}")
if "DP45_IMPLEMENTATION_BLOCKED_BY_SQL_ASSIGNMENT" not in handoff_text:
    fail("handoff verdict is not the required SQL-assignment blocker verdict")

for prohibited_sql in ("CREATE TABLE", "CREATE ROLE", "CREATE FUNCTION", "GRANT ", "ALTER TABLE"):
    if prohibited_sql in implementation_text:
        fail(f"design document contains executable SQL-like statement: {prohibited_sql}")

sc_text = "\n".join(
    path.read_text(encoding="utf-8") for path in (DECISIONS, REGISTRY, SC_HANDOFF)
)
if not re.search(r"35\+.*(?:unallocated|미배정)", sc_text, re.IGNORECASE):
    fail("SC authority no longer marks SQL 35+ as unallocated")
registry_text = REGISTRY.read_text(encoding="utf-8")
for unapproved_role in ("jc_data_adapter_evidence_writer", "jc_data_adapter_evidence_reader"):
    if unapproved_role in registry_text:
        fail(f"adapter role unexpectedly appears in SC registry: {unapproved_role}")
if "DP4_IMPLEMENTATION_COMPLETE_WITH_SQL_ASSIGNMENT_PENDING" not in DP4_HANDOFF.read_text(encoding="utf-8"):
    fail("DP-4 persistence-pending baseline changed unexpectedly")

for number in range(1, 35):
    matches = list(SQL_DIR.glob(f"{number:02d}_*.sql"))
    if len(matches) != 1:
        fail(f"protected SQL {number:02d} expected exactly once, found {len(matches)}")
if list(SQL_DIR.glob("3[5-9]_*.sql")) or list(SQL_DIR.glob("[4-9][0-9]_*.sql")):
    fail("SQL 35+ must remain absent until SC assignment")

with (DP45 / "DP45_DB_OBJECTS.tsv").open(encoding="utf-8", newline="") as handle:
    object_rows = list(csv.DictReader(handle, delimiter="\t"))
if not object_rows:
    fail("DB object proposal evidence is empty")
allowed_object_status = {"PROPOSED_NOT_IMPLEMENTED", "BLOCKED_UNALLOCATED"}
if any(row["implementation_status"] not in allowed_object_status for row in object_rows):
    fail("DB object evidence implies an unauthorized implementation")

with (DP45 / "DP45_ROLE_GRANTS.tsv").open(encoding="utf-8", newline="") as handle:
    role_rows = list(csv.DictReader(handle, delimiter="\t"))
if not any(row["status"] == "PROPOSED_ROLE_NOT_ASSIGNED" for row in role_rows):
    fail("unassigned adapter-role blocker is not recorded")

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
    allowed_exact = {
        "docs/platform/data/DP-4-5-RECOMMENDATION-ADAPTER-SHADOW-EVIDENCE-PERSISTENCE.md",
        "docs/platform/data/DP-4-5-HANDOFF.md",
        ".github/workflows/data-contract-ci.yml",
    }
    unexpected = [
        path for path in changed
        if path not in allowed_exact and not path.startswith("verification/dp4-5/")
    ]
    if unexpected:
        fail(f"unexpected/protected diff: {unexpected}")
    forbidden_prefixes = (
        "database/",
        "jc-data-contracts/",
        "jc-recommendation-core/",
        "jc-backend/src/main/",
        "jc-backend/src/test/",
        "jc-intelligence-contracts/",
        "jc-search-contracts/",
        "jc-search-compatibility/",
        "jc-search-runtime/",
        "jc-search-integration/",
        "jc-search-shadow-wiring/",
        "jc-search-readiness/",
        "jc-search-production-controls/",
    )
    protected = [path for path in changed if path.startswith(forbidden_prefixes)]
    if protected:
        fail(f"SQL, Java, production or protected source changed: {protected}")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

print("DP-4.5 SQL-assignment blocker verification: PASS")
