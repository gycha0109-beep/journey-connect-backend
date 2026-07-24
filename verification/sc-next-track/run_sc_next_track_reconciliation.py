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
CLOSURE_HEAD = "478a15929db43b1b3d3fde4648a5027a36ee75da"
SC2_FINAL_HEAD = "1e259bb92d8d69492a0d8407cb3421f71076d361"
RCA0_FINAL_HEAD = "d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d"
CURRENT_MAIN = "f802a105e46a62718616acaa7a3db6c172e7ed10"

DECISION_DOCS = [
    GOV / "sc-next-track/01-SC-POST-DP-CLOSURE-AUTHORITATIVE-BASELINE.md",
    GOV / "sc-next-track/02-SC-NEXT-TRACK-OWNERSHIP-DECISION.md",
    GOV / "sc-next-track/03-SC-NEXT-TRACK-NAMING-AND-PHASE-ALLOCATION.md",
    GOV / "sc-next-track/04-SC-RECOMMENDATION-CONSUMER-ADOPTION-SCOPE-DECISION.md",
    GOV / "sc-next-track/05-SC-EXISTING-P1-P2-AUTHORITY-PROTECTION-DECISION.md",
    GOV / "sc-next-track/06-SC-DATA-TO-RECOMMENDATION-CONTRACT-DEPENDENCY-MAP.md",
    GOV / "sc-next-track/07-SC-IDENTITY-PRIVACY-DEPENDENCY-DECISION.md",
    GOV / "sc-next-track/08-SC-OPERATIONS-RELIABILITY-PREREQUISITE-MATRIX.md",
    GOV / "sc-next-track/09-SC-SQL-ALLOCATION-DECISION.md",
    GOV / "sc-next-track/10-SC-PRODUCTION-ACTIVATION-IMPACT-ASSESSMENT.md",
    GOV / "sc-next-track/11-SC-CROSS-TRACK-VERIFICATION-PLAN.md",
]
HANDOFF = GOV / "sc-next-track/12-RCA-0-IMPLEMENTATION-HANDOFF-PROMPT.md"
MASTER = GOV / "SC-2-POST-DP-CLOSURE-NEXT-TRACK-BASELINE-RECONCILIATION.md"
REQUIRED_SECTIONS = (
    "## Scope", "## Current Baseline", "## Contract Impact", "## Authority",
    "## Dependencies", "## Allowed Changes", "## Forbidden Changes",
    "## Verification", "## Compatibility", "## Risks", "## Handoff",
)

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

for document in DECISION_DOCS:
    text = read(document)
    for section in REQUIRED_SECTIONS:
        if section not in text:
            fail(f"historical section missing {section}: {document.relative_to(ROOT)}")

master = read(MASTER)
handoff = read(HANDOFF)
for marker in (
    SC2_AUTHORITATIVE_MAIN, CLOSURE_HEAD,
    "DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE",
    "JOINT_INTELLIGENCE_RELIABILITY_ADOPTION",
    "RCA-0 Recommendation Data Consumer Contract & Fixture Alignment",
    "CONTRACT_AND_FIXTURE", "DB_CHANGE_NOT_REQUIRED",
    "PRODUCTION_IMPACT: NONE", "PRODUCTION_ACTIVATION: NOT_AUTHORIZED",
    "NEXT_TRACK_ENTRY_CONDITIONALLY_AUTHORIZED",
):
    if marker not in master:
        fail(f"historical SC-2 marker missing: {marker}")

for marker in (
    "RecommendationP1ProfileSource", "RecommendationP2ObservationSource",
    "recommendation-profile-input-v1", "experiment-outcome-input-v1",
    "SQL `01..52`", "SQL `53+`", "Do not merge without explicit user approval",
):
    if marker not in handoff:
        fail(f"historical RCA-0 handoff marker missing: {marker}")

rows = read_tsv(EVIDENCE / "SC_NEXT_TRACK_DECISIONS.tsv")
actual = {row["decision"]: row for row in rows}
expected = {
    "authoritative_main": SC2_AUTHORITATIVE_MAIN,
    "closure_head": CLOSURE_HEAD,
    "next_track": "JOINT_INTELLIGENCE_RELIABILITY_ADOPTION",
    "scope": "CONTRACT_AND_FIXTURE",
    "db_change": "DB_CHANGE_NOT_REQUIRED",
    "sql_53_plus": "UNALLOCATED",
    "production_activation": "NOT_AUTHORIZED",
    "entry": "NEXT_TRACK_ENTRY_CONDITIONALLY_AUTHORIZED",
}
for key, value in expected.items():
    if actual.get(key, {}).get("value") != value:
        fail(f"historical SC-2 evidence mismatch: {key}")

document_rows = read_tsv(EVIDENCE / "SC_NEXT_TRACK_DOCUMENTS.tsv")
if len(document_rows) != 12 or len({row["path"] for row in document_rows}) != 12:
    fail("historical SC-2 document inventory mismatch")
for row in document_rows:
    if not (ROOT / row["path"]).is_file():
        fail(f"historical inventoried document missing: {row['path']}")

system_contract = read(GOV / "JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md")
governance = read(GOV / "JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md")
registry = read(GOV / "SC-PLATFORM-REGISTRY.md")
decisions = read(GOV / "SC-DECISION-REGISTER.md")
raci = read(GOV / "SC-RACI.md")
sc_handoff = read(GOV / "SC-HANDOFF.md")
for name, text, markers in (
    ("system", system_contract, ("V1.4 / SC-3 RCA-1 ENTRY", CURRENT_MAIN, RCA0_FINAL_HEAD, "RCA1_ENTRY_AUTHORIZED")),
    ("governance", governance, ("RCA-0 Recommendation Data Consumer Contract & Fixture Alignment [COMPLETE / MERGED]", "RCA-1 Recommendation Data Shadow Reconciliation", "RP remains reserved for Reliability Platform")),
    ("registry", registry, ("ACTIVE / RCA0_COMPLETE / RCA1_ENTRY_AUTHORIZED", "recommendation-data-consumer-alignment-v1", "recommendation-shadow-reconciliation-v1", "`53+`")),
    ("decisions", decisions, ("SC-RCA-001", "SC-RCA1-001", "RCA1_ENTRY_AUTHORIZED")),
    ("raci", raci, ("P1 comparison implementation", "P2 exposure/window/event/fallback acceptance")),
    ("handoff", sc_handoff, ("RCA0_CONTRACT_AND_FIXTURE_COMPLETE / RCA1_ENTRY_AUTHORIZED", "MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION")),
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
    "allowlist-hashes: ${JC_SEARCH_SHADOW_PRODUCTION_ALLOWLIST_HASHES:}",
):
    if marker not in prod:
        fail(f"production default missing: {marker}")

subprocess.run(["git", "fetch", "origin", "main", "--depth=2"], cwd=ROOT, check=False,
               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
origin_main = subprocess.run(["git", "rev-parse", "origin/main"], cwd=ROOT, check=True,
                             text=True, capture_output=True).stdout.strip()
if origin_main != CURRENT_MAIN:
    fail(f"origin/main moved: {origin_main}")
for ancestor in (SC2_AUTHORITATIVE_MAIN, SC2_FINAL_HEAD, RCA0_FINAL_HEAD, CURRENT_MAIN):
    subprocess.run(["git", "merge-base", "--is-ancestor", ancestor, "HEAD"], cwd=ROOT, check=True)

changed = subprocess.run(["git", "diff", "--name-only", "origin/main...HEAD"], cwd=ROOT,
                         check=True, text=True, capture_output=True).stdout.splitlines()
allowed = (
    "docs/platform/governance/",
    "verification/sc-next-track/",
    "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
    ".github/workflows/sc-baseline-reconciliation.yml",
)
for rel in filter(None, changed):
    if not any(rel == prefix or rel.startswith(prefix) for prefix in allowed):
        fail(f"unexpected changed file: {rel}")
    if rel.startswith(("database/", "jc-backend/src/main/", "jc-recommendation-core/",
                       "jc-data-contracts/src/main/", "jc-intelligence-contracts/")):
        fail(f"protected source changed: {rel}")

print("SC-2 historical allocation preserved and current SC-3 continuity verified: PASS")
