#!/usr/bin/env python3
from __future__ import annotations

import csv
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GOV = ROOT / "docs/platform/governance"
SQL = ROOT / "database/journey-connect-db-v2.7"
EVIDENCE = ROOT / "verification/sc-next-track"
AUTHORITATIVE_MAIN = "95dad33fd56a54d69e2497c11dc4e2e77d8d3a77"
CLOSURE_HEAD = "478a15929db43b1b3d3fde4648a5027a36ee75da"

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
    "## Scope",
    "## Current Baseline",
    "## Contract Impact",
    "## Authority",
    "## Dependencies",
    "## Allowed Changes",
    "## Forbidden Changes",
    "## Verification",
    "## Compatibility",
    "## Risks",
    "## Handoff",
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
        if not reader.fieldnames or any(not field.strip() for field in reader.fieldnames):
            fail(f"invalid TSV header: {path.name}")
        rows = list(reader)
    if not rows:
        fail(f"empty TSV: {path.name}")
    signatures = [tuple(row.get(field, "") for field in reader.fieldnames) for row in rows]
    if len(signatures) != len(set(signatures)):
        fail(f"duplicate TSV row: {path.name}")
    return rows


for document in DECISION_DOCS:
    text = read(document)
    for section in REQUIRED_SECTIONS:
        if section not in text:
            fail(f"required section {section} missing: {document.relative_to(ROOT)}")

master = read(MASTER)
handoff = read(HANDOFF)
for marker in (
    AUTHORITATIVE_MAIN,
    CLOSURE_HEAD,
    "DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE",
    "JOINT_INTELLIGENCE_RELIABILITY_ADOPTION",
    "RCA-0 Recommendation Data Consumer Contract & Fixture Alignment",
    "CONTRACT_AND_FIXTURE",
    "DB_CHANGE_NOT_REQUIRED",
    "PRODUCTION_IMPACT: NONE",
    "PRODUCTION_ACTIVATION: NOT_AUTHORIZED",
    "NEXT_TRACK_ENTRY_CONDITIONALLY_AUTHORIZED",
):
    if marker not in master:
        fail(f"master decision marker missing: {marker}")

for marker in (
    "RecommendationP1ProfileSource",
    "RecommendationP2ObservationSource",
    "recommendation-profile-input-v1",
    "experiment-outcome-input-v1",
    "SQL `01..52`",
    "SQL `53+`",
    "Do not merge without explicit user approval",
):
    if marker not in handoff:
        fail(f"handoff marker missing: {marker}")

system_contract = read(GOV / "JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md")
governance = read(GOV / "JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md")
registry = read(GOV / "SC-PLATFORM-REGISTRY.md")
decisions = read(GOV / "SC-DECISION-REGISTER.md")
raci = read(GOV / "SC-RACI.md")
sc_handoff = read(GOV / "SC-HANDOFF.md")

for text_name, text, markers in (
    ("system contract", system_contract, (
        "V1.3 / SC-2 POST-DP-CLOSURE", "journey-connect-db-v2.7/01..52",
        "RCA-0", "GATE-1", "GATE-9")),
    ("track governance", governance, (
        "Data Platform technical closure [COMPLETE]", "RCA-0", "RP remains reserved")),
    ("registry", registry, (
        "ACTIVE / DATA_PLATFORM_CLOSED / RCA-0 RESERVED",
        "recommendation-data-consumer-alignment-v1",
        "recommendation-profile-input-consumer-v1",
        "experiment-outcome-input-consumer-v1",
        "recommendation-data-consumer-fixture-v1",
        "`48..52`", "`53+`")),
    ("decision register", decisions, (
        "SC-RCA-001", "SC-RCA-013", "NEXT_TRACK=JOINT_INTELLIGENCE_RELIABILITY_ADOPTION")),
    ("RACI", raci, ("RCA-0 shared fixture implementation", "P2 semantic fixture approval")),
    ("SC handoff", sc_handoff, ("RCA-0 ENTRY CONDITIONALLY AUTHORIZED", "NEXT_TRACK_ENTRY_CONDITIONALLY_AUTHORIZED")),
):
    for marker in markers:
        if marker not in text:
            fail(f"{text_name} marker missing: {marker}")

for contract_id in (
    "recommendation-data-consumer-alignment-v1",
    "recommendation-profile-input-consumer-v1",
    "experiment-outcome-input-consumer-v1",
    "recommendation-data-consumer-fixture-v1",
):
    if registry.count(contract_id) != 1:
        fail(f"registry contract ID must appear exactly once: {contract_id}")

p1_source = read(ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java")
for marker in (
    "from public.recommendation_user_preference",
    "from public.recommendation_behavior_event",
    "left join public.posts",
):
    if marker not in p1_source:
        fail(f"P1 source authority marker missing: {marker}")

p2_source = read(ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java")
for marker in (
    "recommendation_p2_experiment_assignment",
    "recommendation_p2_experiment_exposure",
    "recommendation_p1_profile_snapshot",
    "b.event_type in ('click','like','save','share')",
    "interval '7 days'",
    "r.run_status = 'fallback'",
):
    if marker not in p2_source:
        fail(f"P2 source authority marker missing: {marker}")

profile_projection = read(ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/projection/RecommendationProfileInputProjection.java")
for marker in ("activityWindowDays", "interactionCounts", "recentRegions", "engagementSignals", "negativeSignals"):
    if marker not in profile_projection:
        fail(f"profile projection marker missing: {marker}")

outcome_projection = read(ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/projection/ExperimentOutcomeInputProjection.java")
for marker in ("outcomeWindowSeconds", "604_800L", "clicked", "liked", "saved", "shared", "fallbackObserved"):
    if marker not in outcome_projection:
        fail(f"outcome projection marker missing: {marker}")

for number in range(1, 53):
    matches = list(SQL.glob(f"{number:02d}_*.sql"))
    if len(matches) != 1:
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
        fail(f"protected production default missing: {marker}")

rows = read_tsv(EVIDENCE / "SC_NEXT_TRACK_DECISIONS.tsv")
actual = {row["decision"]: row for row in rows}
expected_values = {
    "authoritative_main": AUTHORITATIVE_MAIN,
    "closure_head": CLOSURE_HEAD,
    "next_track": "JOINT_INTELLIGENCE_RELIABILITY_ADOPTION",
    "scope": "CONTRACT_AND_FIXTURE",
    "db_change": "DB_CHANGE_NOT_REQUIRED",
    "sql_53_plus": "UNALLOCATED",
    "production_activation": "NOT_AUTHORIZED",
    "entry": "NEXT_TRACK_ENTRY_CONDITIONALLY_AUTHORIZED",
}
for key, expected in expected_values.items():
    if actual.get(key, {}).get("value") != expected:
        fail(f"decision evidence mismatch: {key}")

document_rows = read_tsv(EVIDENCE / "SC_NEXT_TRACK_DOCUMENTS.tsv")
if len(document_rows) != 12:
    fail("document inventory must contain exactly 12 rows")
if len({row["path"] for row in document_rows}) != 12:
    fail("document inventory paths must be unique")
for row in document_rows:
    if not (ROOT / row["path"]).is_file():
        fail(f"inventoried document missing: {row['path']}")

subprocess.run(
    ["git", "fetch", "origin", "main", "--depth=1"],
    cwd=ROOT,
    check=False,
    stdout=subprocess.DEVNULL,
    stderr=subprocess.DEVNULL,
)
try:
    origin_main = subprocess.run(
        ["git", "rev-parse", "origin/main"], cwd=ROOT, check=True, text=True, capture_output=True
    ).stdout.strip()
    if origin_main != AUTHORITATIVE_MAIN:
        fail(f"origin/main moved: {origin_main}")
    subprocess.run(
        ["git", "merge-base", "--is-ancestor", AUTHORITATIVE_MAIN, "HEAD"], cwd=ROOT, check=True
    )
    changed = subprocess.run(
        ["git", "diff", "--name-only", "origin/main...HEAD"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    ).stdout.splitlines()
except (subprocess.CalledProcessError, FileNotFoundError) as error:
    fail(f"git baseline verification failed: {error}")

allowed_prefixes = (
    "docs/platform/governance/",
    "verification/sc-next-track/",
    "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
    "verification/data-platform-closure/run_data_platform_closure_verification.py",
    "verification/dp7/run_dp7_allocation_verification.py",
)
for rel in filter(None, changed):
    if not any(rel == prefix or rel.startswith(prefix) for prefix in allowed_prefixes):
        fail(f"unexpected changed file: {rel}")
    if rel.startswith((
        "database/",
        "jc-backend/src/main/",
        "jc-backend/src/main/resources/",
        "jc-recommendation-core/",
        "jc-data-contracts/src/main/",
        "jc-intelligence-contracts/",
    )):
        fail(f"protected source changed: {rel}")

print("SC post-DP-closure next-track reconciliation: PASS")
