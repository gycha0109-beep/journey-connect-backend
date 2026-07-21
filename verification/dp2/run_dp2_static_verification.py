#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path
import csv
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DP2 = ROOT / "verification/dp2"
SQL_DIR = ROOT / "database/journey-connect-db-v2.7"

REQUIRED = [
    SQL_DIR / "29_data_platform_event_store.sql",
    SQL_DIR / "30_data_event_idempotency_roles.sql",
    SQL_DIR / "31_data_event_store_smoke_test.sql",
    ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/support/Sha256DigestV1.java",
    ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/fingerprint/PlatformEventFingerprintCanonicalizerV1.java",
    ROOT / "jc-data-contracts/src/main/java/com/jc/data/contract/v1/fingerprint/Sha256EventFingerprintBoundaryV1.java",
    ROOT / "jc-data-contracts/src/test/java/com/jc/data/contract/Dp2FingerprintContractTest.java",
    ROOT / "jc-data-contracts/src/test/resources/platform-event-fingerprint-sha256-v1.tsv",
    ROOT / ".github/workflows/data-postgres-ci.yml",
    ROOT / "docs/platform/data/DP-2-POSTGRESQL-EVENT-STORE-AND-IDEMPOTENCY.md",
    ROOT / "docs/platform/data/DP-2-HANDOFF.md",
]


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


for path in REQUIRED:
    if not path.is_file():
        fail(f"missing DP-2 file: {path.relative_to(ROOT)}")

for number in range(1, 32):
    matches = list(SQL_DIR.glob(f"{number:02d}_*.sql"))
    if len(matches) != 1:
        fail(f"canonical SQL {number:02d} expected exactly once, found {len(matches)}")

sql29 = (SQL_DIR / "29_data_platform_event_store.sql").read_text(encoding="utf-8")
sql30 = (SQL_DIR / "30_data_event_idempotency_roles.sql").read_text(encoding="utf-8")
sql31 = (SQL_DIR / "31_data_event_store_smoke_test.sql").read_text(encoding="utf-8")
combined = sql29 + "\n" + sql30 + "\n" + sql31

for token in (
    "data_platform_event_v1",
    "data_event_ingest_attempt_v1",
    "data_event_duplicate_observation_v1",
    "data_event_conflict_observation_v1",
    "data_event_idempotency_binding_v1",
    "platform-event-fingerprint-sha256-v1",
    "pg_advisory_xact_lock",
    "IDEMPOTENCY_CONFLICT",
    "SECURITY DEFINER",
    "SET search_path = pg_catalog, public, pg_temp",
    "jc_data_event_writer",
    "jc_data_event_reader",
    "jc_data_replay_executor",
):
    if token not in combined:
        fail(f"required DP-2 SQL token missing: {token}")

if re.search(r"\b(UPDATE|DELETE)\s+public\.data_platform_event_v1\b", sql30, re.IGNORECASE):
    fail("atomic ingest function must not update or delete canonical events")
if re.search(r"CREATE\s+(?:OR\s+REPLACE\s+)?(?:FUNCTION|PROCEDURE)\s+public\.purge_", combined, re.IGNORECASE):
    fail("DP-2 must not create a purge executor")
if "REVOKE EXECUTE ON FUNCTION public.ingest_data_platform_event_v1" not in sql30:
    fail("PUBLIC and unrelated-role execute revoke missing")
if "TO jc_data_event_writer" not in sql30:
    fail("writer function grant missing")
if "GRANT SELECT ON public.data_platform_event_reader_v1" not in sql30:
    fail("reader view grant missing")
if "DUPLICATE NEW" not in (DP2 / "run_dp2_concurrency.sh").read_text(encoding="utf-8"):
    fail("same/same concurrency oracle missing")
if "CONFLICT NEW" not in (DP2 / "run_dp2_concurrency.sh").read_text(encoding="utf-8"):
    fail("same/different concurrency oracle missing")

fixture_rows = list(csv.DictReader(
    (ROOT / "jc-data-contracts/src/test/resources/platform-event-fingerprint-sha256-v1.tsv")
    .read_text(encoding="utf-8").splitlines(), delimiter="\t"))
included = {row["field"] for row in fixture_rows if row["classification"] == "included"}
excluded = {row["field"] for row in fixture_rows if row["classification"] == "excluded"}
expected_included = {
    "contractVersion", "schemaVersion", "canonicalizationVersion", "eventFamily",
    "eventType", "occurredAt", "actorRef", "sessionRef", "entityRef",
    "causationId", "payload",
}
expected_excluded = {
    "eventId", "receivedAt", "producerVersion", "producerBuildId", "requestId",
    "correlationId", "idempotencyKey",
}
if included != expected_included or excluded != expected_excluded:
    fail(f"fingerprint inclusion/exclusion fixture mismatch: included={included}, excluded={excluded}")

try:
    subprocess.run(["git", "fetch", "origin", "main", "--depth=1"], cwd=ROOT,
                   check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    changed = subprocess.run(
        ["git", "diff", "--name-only", "origin/main...HEAD"], cwd=ROOT,
        check=True, text=True, capture_output=True).stdout.splitlines()
    protected_sql = [path for path in changed if re.match(
        r"database/journey-connect-db-v2\.7/(0[1-9]|1[0-9]|2[0-8])_.*\.sql$", path)]
    if protected_sql:
        fail(f"protected SQL 01..28 changed: {protected_sql}")
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

for path in DP2.glob("*.tsv"):
    if not path.read_text(encoding="utf-8").strip():
        fail(f"empty DP-2 evidence: {path.name}")

print("DP-2 static verification: PASS")
