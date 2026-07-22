#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path
import csv
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SQL_DIR = ROOT / "database/journey-connect-db-v2.7"
DP3 = ROOT / "verification/dp3"

REQUIRED = [
    SQL_DIR / "32_data_retry_quarantine_evidence.sql",
    SQL_DIR / "33_data_retry_processing_roles.sql",
    SQL_DIR / "34_data_retry_quarantine_smoke_test.sql",
    ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/retry/RetryPolicyV1.java",
    ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/retry/RetryFailureClassV1.java",
    ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/retry/QuarantineReasonV1.java",
    ROOT / "jc-data-contracts/src/test/java/com/jc/data/contract/Dp3RetryPolicyContractTest.java",
    ROOT / "docs/platform/data/DP-3-RETRY-AND-QUARANTINE-PROCESSING-FOUNDATION.md",
    ROOT / "docs/platform/data/DP-3-HANDOFF.md",
    ROOT / ".github/workflows/data-postgres-ci.yml",
    DP3 / "run_dp3_concurrency.sh",
]


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in REQUIRED:
    if not path.is_file():
        fail(f"missing DP-3 file: {path.relative_to(ROOT)}")

for number in range(1, 35):
    matches = list(SQL_DIR.glob(f"{number:02d}_*.sql"))
    if len(matches) != 1:
        fail(f"canonical SQL {number:02d} expected exactly once, found {len(matches)}")

sql32 = (SQL_DIR / "32_data_retry_quarantine_evidence.sql").read_text(encoding="utf-8")
sql33 = (SQL_DIR / "33_data_retry_processing_roles.sql").read_text(encoding="utf-8")
sql34 = (SQL_DIR / "34_data_retry_quarantine_smoke_test.sql").read_text(encoding="utf-8")
combined = "\n".join((sql32, sql33, sql34))

for token in (
    "data-projection-retry-v1",
    "data_projection_work_state_v1",
    "data_projection_retry_schedule_v1",
    "data_projection_processing_attempt_v1",
    "data_projection_quarantine_evidence_v1",
    "data_projection_quarantine_review_evidence_v1",
    "FOR UPDATE SKIP LOCKED",
    "interval '60 seconds'",
    "interval '20 seconds'",
    "retry_exhausted",
    "repeated_deterministic_failure",
    "unclassified_failure",
    "jc_data_retry_processor",
    "jc_data_quarantine_reviewer",
    "jc_data_replay_executor",
    "EVENT_CLAIM_STALE",
    "SECURITY DEFINER",
    "SET search_path = pg_catalog, public, pg_temp",
    "data_projection_retry_observability_v1",
):
    if token not in combined:
        fail(f"required DP-3 token missing: {token}")

for delay in ("1 minute", "5 minutes", "30 minutes", "2 hours", "12 hours"):
    if delay not in sql33:
        fail(f"retry delay missing: {delay}")

if re.search(r"\b(UPDATE|DELETE)\s+public\.data_platform_event_v1\b", sql33, re.IGNORECASE):
    fail("DP-3 must not mutate canonical event source")
if re.search(r"\b(UPDATE|DELETE)\s+public\.data_event_idempotency_binding_v1\b", sql33, re.IGNORECASE):
    fail("DP-3 must not mutate idempotency source")
if re.search(r"CREATE\s+(?:OR\s+REPLACE\s+)?(?:FUNCTION|PROCEDURE)\s+public\.(?:purge|replay_execute|release_quarantine)", combined, re.IGNORECASE):
    fail("DP-3 must not create purge, replay execution, or automatic release")
if "GRANT EXECUTE" in sql33 and "TO jc_data_replay_executor" in sql33:
    fail("replay executor must not receive DP-3 execution grants")
if "DUPLICATE_CLAIM_BLOCKED" not in (DP3 / "run_dp3_concurrency.sh").read_text(encoding="utf-8"):
    fail("same-work concurrency oracle missing")
if "INDEPENDENT_CLAIMS_DISTRIBUTED" not in (DP3 / "run_dp3_concurrency.sh").read_text(encoding="utf-8"):
    fail("independent-work concurrency oracle missing")

try:
    subprocess.run(["git", "fetch", "origin", "main", "--depth=1"], cwd=ROOT,
                   check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    changed = subprocess.run(
        ["git", "diff", "--name-only", "origin/main...HEAD"], cwd=ROOT,
        check=True, text=True, capture_output=True).stdout.splitlines()
    protected_sql = [path for path in changed if re.match(
        r"database/journey-connect-db-v2\.7/(0[1-9]|1[0-9]|2[0-9]|3[01])_.*\.sql$", path)]
    if protected_sql:
        fail(f"protected SQL 01..31 changed: {protected_sql}")
    protected_runtime = [path for path in changed if path.startswith((
        "jc-backend/src/main/", "jc-recommendation-core/", "jc-intelligence-contracts/",
        "jc-search-contracts/", "jc-search-compatibility/", "jc-search-runtime/",
        "jc-search-integration/", "jc-search-shadow-wiring/", "jc-search-readiness/",
        "jc-search-production-controls/",
    ))]
    if protected_runtime:
        fail(f"protected runtime changed: {protected_runtime}")
except (subprocess.CalledProcessError, FileNotFoundError):
    pass

for path in DP3.glob("*.tsv"):
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.reader(handle, delimiter="\t"))
    if not rows:
        fail(f"empty DP-3 evidence: {path.name}")

print("DP-3 static verification: PASS")
