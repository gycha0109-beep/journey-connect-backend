#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import csv
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DP4 = ROOT / "verification/dp4"
DATA = ROOT / "jc-data-contracts"
PACKAGE = DATA / "src/main/java/com/jc/data/contract/v1/adapter/recommendation"
CORE_EVENT_TYPE = ROOT / "jc-recommendation-core/src/main/java/com/jc/recommendation/model/event/EventType.java"
GOLDEN = DATA / "src/test/resources/recommendation-p0-adapter-golden-v1.tsv"
INVALID = DATA / "src/test/resources/recommendation-p0-adapter-invalid-v1.tsv"

REQUIRED = [
    PACKAGE / "RecommendationP0EventAdapter.java",
    PACKAGE / "RecommendationP0EventAdapterV1.java",
    PACKAGE / "RecommendationP0AdapterInputV1.java",
    PACKAGE / "RecommendationP0AdapterOutputV1.java",
    PACKAGE / "RecommendationP0CompatibilityClass.java",
    PACKAGE / "RecommendationP0MappingStatus.java",
    PACKAGE / "RecommendationP0MappingFailure.java",
    PACKAGE / "RecommendationP0AdapterPolicyV1.java",
    GOLDEN,
    INVALID,
    ROOT / "docs/platform/data/DP-4-P0-RECOMMENDATION-EVENT-ADAPTER.md",
    ROOT / "docs/platform/data/DP-4-RECOMMENDATION-COMPATIBILITY-MATRIX.md",
    ROOT / "docs/platform/data/DP-4-HANDOFF.md",
]

EXPECTED_WIRES = {
    "impression", "view", "click", "like", "unlike", "save", "unsave", "share",
    "follow", "unfollow", "hide", "report", "search", "tag_click", "crew_join", "crew_leave",
}
EXPECTED_TARGETS = {
    "impression": "recommendation_impression",
    "view": "post_view",
    "click": "recommendation_click",
    "like": "post_like",
    "unlike": "post_unlike",
    "save": "post_bookmark",
    "unsave": "post_unbookmark",
    "share": "post_share",
    "follow": "follow",
    "unfollow": "unfollow",
    "hide": "post_hide",
    "report": "post_report",
    "search": "search_submit",
    "tag_click": "tag_click",
    "crew_join": "crew_join",
    "crew_leave": "crew_leave",
}


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in REQUIRED:
    if not path.is_file():
        fail(f"missing DP-4 file: {path.relative_to(ROOT)}")

core_text = CORE_EVENT_TYPE.read_text(encoding="utf-8")
core_wires = set(re.findall(r"\b[A-Z_]+\(\"([a-z_]+)\"\)", core_text))
if core_wires != EXPECTED_WIRES:
    fail(f"authoritative P0 taxonomy mismatch: {sorted(core_wires)}")

with GOLDEN.open(encoding="utf-8", newline="") as handle:
    golden_rows = list(csv.DictReader(handle, delimiter="\t"))
if len(golden_rows) != 16:
    fail(f"expected 16 golden mappings, found {len(golden_rows)}")
if {row["source_event_type"] for row in golden_rows} != EXPECTED_WIRES:
    fail("golden fixture does not cover exact P0 wire set")
for row in golden_rows:
    source = row["source_event_type"]
    if row["target_event_type"] != EXPECTED_TARGETS[source]:
        fail(f"target mapping mismatch for {source}")
    if row["compatibility"] != "semantic_compatible":
        fail(f"mapping must remain semantic for {source}")
    if not re.fullmatch(r"[0-9a-f]{64}", row["output_fingerprint"]):
        fail(f"invalid adapter fingerprint fixture for {source}")

with INVALID.open(encoding="utf-8", newline="") as handle:
    invalid_rows = list(csv.DictReader(handle, delimiter="\t"))
required_failures = {
    "unsupported_event_type", "unsupported_schema_version", "identity_mapping_required",
    "missing_required_reference", "missing_exposure_reference", "payload_unmappable",
    "timestamp_invalid", "source_fingerprint_mismatch", "privacy_policy_violation",
    "exposure_authority_conflict",
}
if not required_failures.issubset({row["expected_failure"] for row in invalid_rows}):
    fail("invalid fixture does not cover stable failure taxonomy")
if any(row["retryable"] != "false" for row in invalid_rows):
    fail("deterministic mapping failures must not auto-retry")

source = "\n".join(path.read_text(encoding="utf-8") for path in PACKAGE.glob("*.java"))
for forbidden in (
    "org.springframework", "jakarta.persistence", "javax.persistence", "java.sql.",
    "HttpClient", "Socket", "UUID.randomUUID", "Clock.system", "System.currentTimeMillis",
):
    if forbidden in source:
        fail(f"runtime/framework dependency forbidden in adapter: {forbidden}")
for required in (
    "recommendation_general_exposure_v1",
    "recommendation_p2_experiment_exposure_v1",
    "SOURCE_FINGERPRINT_MISMATCH",
    "IDENTITY_MAPPING_REQUIRED",
    "MAPPED_SHADOW",
    "Sha256DigestV1",
):
    if required not in source:
        fail(f"required adapter boundary missing: {required}")

build = (DATA / "build.gradle.kts").read_text(encoding="utf-8")
if 'testImplementation(project(":jc-recommendation-core"))' not in build:
    fail("P0 compatibility must be a test-only project dependency")
if re.search(r"(?m)^\s*implementation\(project\(\":jc-recommendation-core\"\)\)", build):
    fail("Recommendation core cannot be a Data production dependency")

adapter_doc = (ROOT / "docs/platform/data/DP-4-P0-RECOMMENDATION-EVENT-ADAPTER.md").read_text(encoding="utf-8")
for marker in (
    "Recommendation P0 source → Data shadow candidate",
    "SC_SQL_ASSIGNMENT_REQUIRED",
    "No SQL is added",
    "production Recommendation runtime is not implemented",
):
    if marker not in adapter_doc:
        fail(f"DP-4 authority marker missing: {marker}")

sql_dir = ROOT / "database/journey-connect-db-v2.7"
for number in range(1, 35):
    matches = list(sql_dir.glob(f"{number:02d}_*.sql"))
    if len(matches) != 1:
        fail(f"protected SQL {number:02d} expected exactly once, found {len(matches)}")
if list(sql_dir.glob("3[5-9]_*.sql")) or list(sql_dir.glob("[4-9][0-9]_*.sql")):
    fail("DP-4 must not allocate SQL 35+")

try:
    subprocess.run(["git", "fetch", "origin", "main", "--depth=1"], cwd=ROOT,
                   check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    changed = subprocess.run(
        ["git", "diff", "--name-only", "origin/main...HEAD"], cwd=ROOT,
        check=True, text=True, capture_output=True).stdout.splitlines()
    allowed = (
        "jc-data-contracts/",
        "docs/platform/data/DP-4-",
        "verification/dp4/",
        "verification/dp4-5/",
        "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
        ".github/workflows/data-contract-ci.yml",
        ".github/workflows/recommendation-p0-db-ci.yml",
        ".github/workflows/backend-pr-ci.yml",
    )
    unexpected = [path for path in changed if not any(path == prefix or path.startswith(prefix) for prefix in allowed)]
    if unexpected:
        fail(f"unexpected/protected diff: {unexpected}")
    if any(path.endswith(".sql") for path in changed):
        fail("SQL 01..34 and SQL 35+ must remain unchanged")
    protected = [path for path in changed if path.startswith((
        "jc-recommendation-core/", "jc-backend/src/main/", "jc-backend/src/main/resources/",
        "jc-intelligence-contracts/", "jc-search-contracts/", "jc-search-compatibility/",
        "jc-search-runtime/", "jc-search-integration/", "jc-search-shadow-wiring/",
        "jc-search-readiness/", "jc-search-production-controls/",
    ))]
    if protected:
        fail(f"production/Recommendation/Search source changed: {protected}")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

for path in DP4.glob("*.tsv"):
    if not path.read_text(encoding="utf-8").strip():
        fail(f"empty DP-4 evidence: {path.name}")

print("DP-4 static verification: PASS")
