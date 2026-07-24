#!/usr/bin/env python3
from __future__ import annotations

import csv
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GOV = ROOT / "docs/platform/governance"
SQL = ROOT / "database/journey-connect-db-v2.7"
EVIDENCE = ROOT / "verification/sc-next-track"

SC2_AUTHORITATIVE_MAIN = "95dad33fd56a54d69e2497c11dc4e2e77d8d3a77"
SC2_FINAL_HEAD = "1e259bb92d8d69492a0d8407cb3421f71076d361"
RCA0_FINAL_HEAD = "d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d"
SC3_MERGE_MAIN = "5a0ca52c8226a0f4a6e21f9af96c7da0732c8d5b"
RCA1_FINAL_HEAD = "38896b2a37180633870282e9d9e305d9c9fbbf8a"
CURRENT_MAIN = "b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def read(path: Path) -> str:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(ROOT)}")
    text = path.read_text(encoding="utf-8")
    if not text.strip():
        fail(f"empty file: {path.relative_to(ROOT)}")
    return text


def read_tsv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        rows = list(reader)
    if not reader.fieldnames or not rows:
        fail(f"invalid TSV: {path.name}")
    signatures = [tuple(row.get(field, "") for field in reader.fieldnames) for row in rows]
    if len(signatures) != len(set(signatures)):
        fail(f"duplicate TSV row: {path.name}")
    return rows


# Historical SC-2 allocation documents/evidence remain present and unchanged by SC-4.
for number in range(1, 13):
    matches = list((GOV / "sc-next-track").glob(f"{number:02d}-*.md"))
    if len(matches) != 1:
        fail(f"historical SC-2 document {number:02d} missing or duplicated")
for name in ("SC_NEXT_TRACK_DECISIONS.tsv", "SC_NEXT_TRACK_DOCUMENTS.tsv"):
    read_tsv(EVIDENCE / name)

# SC-3 entry package and RCA-1 implementation handoff remain present.
for name in (
    "SC-3-RCA-1-ENTRY-AUTHORIZATION-AND-BASELINE-ALLOCATION.md",
    "22-RCA-1-IMPLEMENTATION-HANDOFF-PROMPT.md",
):
    read(GOV / "sc-next-track" / name)

system_contract = read(GOV / "JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md")
governance = read(GOV / "JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md")
registry = read(GOV / "SC-PLATFORM-REGISTRY.md")
decisions = read(GOV / "SC-DECISION-REGISTER.md")
raci = read(GOV / "SC-RACI.md")
handoff = read(GOV / "SC-HANDOFF.md")

for name, text, markers in (
    ("system", system_contract, ("V1.5 / SC-4 RCA-1B ENTRY", CURRENT_MAIN, RCA1_FINAL_HEAD, "RCA1B_ENTRY_AUTHORIZED", "PRODUCTION_ACTIVATION=NOT_AUTHORIZED")),
    ("governance", governance, ("RCA-0 Recommendation Data Consumer Contract & Fixture Alignment [COMPLETE / MERGED]", "RCA-1 Recommendation Data Shadow Reconciliation [COMPLETE / MODEL A]", "reserved for Reliability Platform", "CI_EPHEMERAL_POSTGRESQL")),
    ("registry", registry, ("ACTIVE / RCA1_COMPLETE / RCA1B_ENTRY_AUTHORIZED", "recommendation-shadow-reconciliation-v1", "RCA-1B", "`53+`")),
    ("decisions", decisions, ("SC-RCA-001", "SC-RCA1-001", "SC-RCA1B-001", "RCA1B_ENTRY_AUTHORIZED")),
    ("raci", raci, ("P1 authoritative/candidate query and dimensions", "P2 exposure/window/event/fallback query", "BLOCKING_APPROVAL")),
    ("handoff", handoff, ("RCA1_OFFLINE_SHADOW_RECONCILIATION_COMPLETE / RCA1B_ENTRY_AUTHORIZED", "POSTGRESQL_VERSION_MATRIX=15,18", "TRANSACTION_READ_ONLY=REQUIRED")),
):
    for marker in markers:
        if marker not in text:
            fail(f"current {name} marker missing: {marker}")

p1_source = read(ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java")
for marker in ("recommendation_user_preference", "recommendation_behavior_event", "public.posts"):
    if marker not in p1_source:
        fail(f"P1 authority marker missing: {marker}")
p2_source = read(ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java")
for marker in (
    "recommendation_p2_experiment_assignment", "recommendation_p2_experiment_exposure",
    "recommendation_p1_profile_snapshot", "b.event_type in ('click','like','save','share')",
    "interval '7 days'", "r.run_status = 'fallback'",
):
    if marker not in p2_source:
        fail(f"P2 authority marker missing: {marker}")

for number in range(1, 53):
    if len(list(SQL.glob(f"{number:02d}_*.sql"))) != 1:
        fail(f"canonical SQL {number:02d} missing or duplicated")
if list(SQL.glob("5[3-9]_*.sql")) or list(SQL.glob("[6-9][0-9]_*.sql")):
    fail("SQL 53+ must remain absent")

prod = read(ROOT / "jc-backend/src/main/resources/application-prod.yml")
for marker in (
    "enabled: ${JC_SEARCH_SHADOW_PRODUCTION_ENABLED:false}",
    "kill-switch: ${JC_SEARCH_SHADOW_PRODUCTION_KILL_SWITCH:true}",
    "sampling-bps: ${JC_SEARCH_SHADOW_PRODUCTION_SAMPLING_BPS:0}",
):
    if marker not in prod:
        fail(f"production default missing: {marker}")

shallow = ROOT / ".git/shallow"
fetch_command = ["git", "fetch", "--unshallow", "origin"] if shallow.exists() else ["git", "fetch", "origin", "main"]
subprocess.run(fetch_command, cwd=ROOT, check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
origin_main = subprocess.run(["git", "rev-parse", "origin/main"], cwd=ROOT, check=True,
                             text=True, capture_output=True).stdout.strip()
if origin_main != CURRENT_MAIN:
    fail(f"origin/main moved: {origin_main}")
for ancestor in (SC2_AUTHORITATIVE_MAIN, SC2_FINAL_HEAD, RCA0_FINAL_HEAD, SC3_MERGE_MAIN, RCA1_FINAL_HEAD, CURRENT_MAIN):
    subprocess.run(["git", "merge-base", "--is-ancestor", ancestor, "HEAD"], cwd=ROOT, check=True)
subprocess.run(["git", "diff", "--quiet", RCA1_FINAL_HEAD, CURRENT_MAIN], cwd=ROOT, check=True)

changed = subprocess.run(["git", "diff", "--name-only", "origin/main...HEAD"], cwd=ROOT,
                         check=True, text=True, capture_output=True).stdout.splitlines()
allowed = (
    "docs/platform/governance/", "verification/sc-next-track/",
    "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
    ".github/workflows/sc-baseline-reconciliation.yml", ".github/workflows/sc-rca1b-entry-ci.yml",
)
for rel in filter(None, changed):
    if not any(rel == prefix or rel.startswith(prefix) for prefix in allowed):
        fail(f"unexpected changed file: {rel}")
    if rel.startswith(("database/", "jc-backend/src/main/", "jc-backend/src/test/", "jc-recommendation-core/", "verification/rca0/", "verification/rca1/")):
        fail(f"protected/historical source changed: {rel}")

print("SC historical allocation preserved through RCA-1 merge and current SC-4 entry: PASS")
