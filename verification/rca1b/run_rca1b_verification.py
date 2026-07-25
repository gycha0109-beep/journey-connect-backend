#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK_START = "d07091bff54a3bfdae10d8fb6f3008923d69d455"
SC4_FINAL = "b345a47c68c0e89db325183dbab6113a6291f24e"
RCA1_FINAL = "38896b2a37180633870282e9d9e305d9c9fbbf8a"
OUT = ROOT / "verification/rca1b/runtime"
QUERY_ROOT = ROOT / "jc-backend/src/test/resources/recommendation-data-adoption/rca1b"
QUERY_INVENTORY = QUERY_ROOT / "query-fingerprints.tsv"
TEST_PACKAGE = ROOT / "jc-backend/src/test/java/com/jc/backend/recommendation/dataadoption/reconciliation/database"
DOC_ROOT = ROOT / "docs/platform/recommendation"
DOCS = [
    "RCA-1B-IMPLEMENTATION-REPORT.md",
    "RCA-1B-EXECUTION-ENVIRONMENT-REPORT.md",
    "RCA-1B-POSTGRESQL-COMPATIBILITY-REPORT.md",
    "RCA-1B-READ-ONLY-ROLE-GRANT-REPORT.md",
    "RCA-1B-QUERY-REGISTRY-FINGERPRINT-REPORT.md",
    "RCA-1B-DETERMINISTIC-SEED-REPORT.md",
    "RCA-1B-P1-DATABASE-RECONCILIATION-RESULT.md",
    "RCA-1B-P1-MISMATCH-INVENTORY.md",
    "RCA-1B-P2-DATABASE-RECONCILIATION-RESULT.md",
    "RCA-1B-P2-MISMATCH-INVENTORY.md",
    "RCA-1B-CHECKPOINT-LINEAGE-REPORT.md",
    "RCA-1B-PERMISSION-NEGATIVE-VERIFICATION.md",
    "RCA-1B-EVIDENCE-REDACTION-REPORT.md",
    "RCA-1B-CROSS-VERSION-EQUIVALENCE-REPORT.md",
    "RCA-1B-PROTECTED-AUTHORITY-REPORT.md",
    "RCA-1B-VERIFICATION-SUMMARY.md",
    "RCA-1B-WORKLOG.md",
    "RCA-1B-EXIT-AND-RCA2-HANDOFF.md",
    "RCA-1B-BLOCKING-APPROVAL-REVIEW-PACKAGE.md",
]
SECTIONS = {
    "Scope", "Current Baseline", "Implementation", "Authority", "Dependencies",
    "Execution Environment", "DB Access Boundary", "Query Boundary", "Dataset",
    "Identity/Privacy", "P1 Result", "P2 Result", "Checkpoint/Lineage", "Evidence",
    "Verification", "Compatibility", "Risks", "Exit Criteria", "Handoff",
}
QUERY_IDS = {
    "P1_AUTHORITATIVE_REFERENCE_V1", "P1_DATA_CANDIDATE_V1",
    "P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1", "P2_DATA_CANDIDATE_V1",
    "SOURCE_CHECKPOINT_V1", "SOURCE_LINEAGE_V1", "BOUNDED_ROW_COUNT_V1",
}
P1_DIMS = {
    "QUERY_RESULT_PARITY", "CHECKPOINT_PARITY", "SNAPSHOT_ISOLATION_PARITY",
    "ROW_ORDER_PARITY", "NULL_SEMANTICS_PARITY", "NUMERIC_NORMALIZATION_PARITY",
    "TIMEZONE_NORMALIZATION_PARITY", "DUPLICATE_ROW_DETECTION", "SOURCE_ROW_COUNT_PARITY",
}
P2_DIMS = {
    "QUERY_RESULT_PARITY", "CHECKPOINT_PARITY", "EXPOSURE_ROW_UNIQUENESS",
    "OUTCOME_ROW_UNIQUENESS", "DUPLICATE_OBSERVATION_DETECTION",
    "WINDOW_BOUNDARY_SQL_PARITY", "EVENT_TYPE_FILTER_PARITY", "FALLBACK_JOIN_PARITY",
    "ASSIGNMENT_VERSION_JOIN_PARITY", "SOURCE_ROW_COUNT_PARITY",
}
EVIDENCE_FIELDS = [
    "hashedCaseId", "lane", "contractId", "contractVersion", "queryId", "queryFingerprint",
    "comparisonDimension", "classification", "normalizedExpected", "normalizedActual",
    "sourceCheckpoint", "candidateCheckpoint", "lineageFingerprint", "sourceRowCount",
    "candidateRowCount", "databaseVersion", "executionEnvironment", "transactionIsolation",
    "transactionReadOnly", "statementTimeoutMs", "seedDigest", "verifierVersion", "testedSha",
    "evidenceTimestamp",
]


def sh(*args: str, check: bool = True) -> str:
    return subprocess.run(args, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, check=check).stdout.strip()


def need(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        result = list(reader)
    need(bool(reader.fieldnames) and bool(result), f"invalid TSV: {path}")
    signatures = [tuple(row.get(field, "") for field in reader.fieldnames) for row in result]
    need(len(signatures) == len(set(signatures)), f"duplicate TSV row: {path}")
    return result


def canonical_bytes(path: Path) -> bytes:
    text = path.read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n")
    lines = [line.rstrip() for line in text.split("\n")]
    while lines and not lines[-1]:
        lines.pop()
    return ("\n".join(lines) + "\n").encode("utf-8")


def static_checks(head: str) -> list[dict[str, str]]:
    checks: list[dict[str, str]] = []

    def record(name: str, function) -> None:
        detail = function() or "verified"
        checks.append({"check": name, "status": "PASS", "detail": str(detail)})

    def baseline() -> str:
        sh("git", "fetch", "origin", "main", "--depth=200", check=False)
        need(sh("git", "cat-file", "-t", WORK_START) == "commit", "RCA1B work-start commit absent")
        need("Merge pull request #26" in sh("git", "show", "-s", "--format=%B", WORK_START), "PR #26 merge absent")
        sh("git", "merge-base", "--is-ancestor", SC4_FINAL, WORK_START)
        sh("git", "diff", "--quiet", SC4_FINAL, WORK_START)
        sh("git", "merge-base", "--is-ancestor", RCA1_FINAL, WORK_START)
        sh("git", "merge-base", "--is-ancestor", WORK_START, head)
        sh("git", "merge-base", "--is-ancestor", WORK_START, "origin/main")
        baseline_rows = {row["key"]: row["value"] for row in rows(ROOT / "verification/rca1b/RCA1B_BASELINE.tsv")}
        need(baseline_rows.get("work_start_sha") == WORK_START, "work-start evidence mismatch")
        return "historical RCA1B work-start, PR26/SC4 tree and current ancestry verified"
    record("authoritative_work_start_sc4_merge_tree", baseline)

    def rca1() -> str:
        contracts = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption/reconciliation/Rca1Contracts.java").read_text(encoding="utf-8")
        found = set(re.findall(r'"(recommendation-shadow-reconciliation(?:-evidence|-fixture)?-v1)"', contracts))
        need(len(found) == 3, f"RCA1 contract count: {found}")
        need(len(rows(ROOT / "jc-backend/src/test/resources/recommendation-data-adoption/reconciliation/p1-reconciliation-fixtures-v1.tsv")) == 23, "RCA1 P1 count")
        need(len(rows(ROOT / "jc-backend/src/test/resources/recommendation-data-adoption/reconciliation/p2-reconciliation-fixtures-v1.tsv")) == 39, "RCA1 P2 count")
        need("RECONCILED_WITH_EXPECTED_GAPS" in contracts, "P1 result marker")
        need("RECONCILED_WITH_MIGRATION_GAPS" in contracts, "P2 result marker")
        need('IDENTITY_MODE = "SYNTHETIC_ONLY"' in contracts, "identity marker")
        need((ROOT / "docs/platform/governance/sc-next-track/37-RCA-1B-IMPLEMENTATION-HANDOFF-PROMPT.md").is_file(), "SC4 handoff missing")
        return "RCA1 three contracts and 23/39 fixtures unchanged"
    record("rca1_contract_fixture_behavior_baseline", rca1)

    def sql_protection() -> str:
        directory = ROOT / "database/journey-connect-db-v2.7"
        inventory = []
        for path in directory.glob("*.sql"):
            match = re.match(r"(\d+)_", path.name)
            if match:
                inventory.append(int(match.group(1)))
        need(len(inventory) == 52 and set(inventory) == set(range(1, 53)), "SQL 01..52 inventory")
        need(not list(directory.glob("5[3-9]_*.sql")) and not list(directory.glob("[6-9][0-9]_*.sql")), "SQL 53+ exists")
        return "canonical SQL 01..52 exact and 53+ absent"
    record("canonical_sql_protection", sql_protection)

    def query_registry() -> str:
        inventory = rows(QUERY_INVENTORY)
        need({row["query_id"] for row in inventory} == QUERY_IDS, "query IDs must be exact seven")
        need(len({row["sha256"] for row in inventory}) == 7, "duplicate query fingerprint")
        for row in inventory:
            path = QUERY_ROOT / row["resource"]
            need(path.is_file(), f"missing query {path}")
            actual = hashlib.sha256(canonical_bytes(path)).hexdigest()
            need(actual == row["sha256"], f"fingerprint mismatch {row['query_id']}")
            text = path.read_text(encoding="utf-8")
            need("ORDER BY" in text.upper() and "LIMIT ?" in text.upper(), f"unbounded query {row['query_id']}")
            need(";" not in text.rstrip().rstrip(";"), f"multiple statements {row['query_id']}")
        java = "\n".join(path.read_text(encoding="utf-8") for path in TEST_PACKAGE.glob("*.java"))
        need("unknown query id" in java and "query fingerprint mismatch" in java, "fail-closed query guards missing")
        need("Statement.executeQuery(registry.sql" not in java, "prepared statement boundary weakened")
        return "seven static bounded ordered fingerprinted queries"
    record("query_registry_fingerprint_and_bounds", query_registry)

    def implementation() -> str:
        need((TEST_PACKAGE / "Rca1bDatabaseReconciliationTest.java").is_file(), "database test missing")
        need((QUERY_ROOT / "bootstrap-role.sql").is_file(), "role bootstrap missing")
        need((QUERY_ROOT / "seed.sql").is_file(), "seed missing")
        java = "\n".join(path.read_text(encoding="utf-8") for path in TEST_PACKAGE.glob("*.java"))
        for marker in (
            "PostgreSQLContainer", "withTmpFs", "SET TRANSACTION READ ONLY", "SHOW transaction_read_only",
            "REPEATABLE_READ", "statement_timeout", "lock_timeout", "MAX_ROWS", "PENDING_USER_REVIEW",
            "P2_NON_PRODUCTION_RECONCILIATION_ONLY", "CURRENT_P2_AUTHORITY_UNCHANGED", "NO_AUTHORITY_TRANSFER",
        ):
            need(marker in java, f"implementation marker missing {marker}")
        for forbidden in ("@Component", "@Service", "@Repository", "@Controller", "@Configuration", "JdbcTemplate", "EntityManager"):
            need(forbidden not in java, f"runtime dependency {forbidden}")
        return "test-only Testcontainers/read-only/evidence implementation"
    record("test_only_database_implementation", implementation)

    def docs() -> str:
        for name in DOCS:
            path = DOC_ROOT / name
            need(path.is_file(), f"missing doc {name}")
            text = path.read_text(encoding="utf-8")
            headings = set(re.findall(r"^##\s+(.+)$", text, re.MULTILINE))
            need(SECTIONS <= headings, f"sections missing {name}: {sorted(SECTIONS-headings)}")
            need(WORK_START in text, f"work-start missing {name}")
        return "19 RCA1B documents complete"
    record("documents_and_review_package", docs)

    def dimensions() -> str:
        inventory = rows(ROOT / "verification/rca1b/RCA1B_DIMENSION_INVENTORY.tsv")
        need({row["dimension"] for row in inventory if row["lane"] == "P1"} == P1_DIMS, "P1 DB dimensions")
        need({row["dimension"] for row in inventory if row["lane"] == "P2"} == P2_DIMS, "P2 DB dimensions")
        return "P1/P2 DB dimensions independently registered"
    record("lane_dimension_inventory", dimensions)

    def diff_boundary() -> str:
        changed = sh("git", "diff", "--name-only", f"{WORK_START}..{head}").splitlines()
        allowed = (
            ".github/actions/rca2-job/",
            ".github/workflows/rca1b-nonproduction-readonly-reconciliation-ci.yml",
            ".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",
            ".github/workflows/sc-baseline-reconciliation.yml",
            "docs/platform/governance/",
            "docs/platform/recommendation/RCA-1B-",
            "docs/platform/recommendation/rca2/",
            "jc-backend/build.gradle.kts",
            "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java",
            "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/",
            "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml",
            "jc-backend/src/test/java/com/jc/backend/recommendation/dataadoption/reconciliation/database/",
            "jc-backend/src/test/java/com/jc/backend/recommendation/rca2/",
            "jc-backend/src/test/java/com/jc/backend/verification/IP9ControlledBackendHookStaticTest.java",
            "jc-backend/src/test/resources/recommendation-data-adoption/rca1b/",
            "jc-search-readiness/src/test/java/com/jc/intelligence/readiness/search/SearchShadowReadinessContractTest.java",
            "verification/rca0/run_rca0_verification.py",
            "verification/rca1/run_rca1_verification.py",
            "verification/rca1b/",
            "verification/rca2/",
            "verification/data-platform-closure/run_data_platform_closure_verification.py",
            "verification/dp5/run_dp5_static_verification.py",
            "verification/dp6/run_dp6_allocation_verification.py",
            "verification/dp6/run_dp6_static_verification.py",
            "verification/dp7/run_dp7_allocation_verification.py",
            "verification/dp7/run_dp7_static_verification.py",
            "verification/sc-dp1-baseline-reconciliation/",
            "verification/sc-next-track/",
        )
        unexpected = [item for item in changed if not any(item == prefix or item.startswith(prefix) for prefix in allowed)]
        need(not unexpected, f"unexpected cross-phase diff {unexpected}")
        production_configs=[item for item in changed if re.search(r"jc-backend/src/main/resources/application.*\.(?:yml|yaml|properties)$",item) and item != "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml"]
        protected=[item for item in changed if item.startswith(("database/","jc-recommendation-core/")) or item in (
            "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java",
            "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java",
        )]
        need(not protected and not production_configs, f"protected diff {protected+production_configs}")
        rca2_config=(ROOT/"jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml").read_text(encoding="utf-8")
        need("flag: off" in rca2_config and "traffic-percent: 0" in rca2_config and "max-production-dark-read-percent: 0" in rca2_config and "production-route-allowed: false" in rca2_config,"RCA2 isolated config boundary")
        feed=(ROOT/"jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java").read_text(encoding="utf-8")
        need("RCA-2 request registration failed open" in feed and "return response;" in feed and "return registrar.registerFeed" not in feed,"RCA2 primary authority boundary")
        return f"authorized RCA1B/SC5/RCA2 phase diff: {len(changed)} files; historical DB/query/source boundaries protected"
    record("protected_source_config_historical_evidence", diff_boundary)
    return checks


def runtime_checks(version: str, head: str) -> list[dict[str, str]]:
    directory = OUT / f"postgresql-{version}"
    checks: list[dict[str, str]] = []

    def record(name: str, function) -> None:
        detail = function() or "verified"
        checks.append({"check": name, "status": "PASS", "detail": str(detail)})

    def summary_check() -> str:
        summary = {row["key"]: row["value"] for row in rows(directory / "RCA1B_EXECUTION_SUMMARY.tsv")}
        expected = {
            "WORK_START_SHA": WORK_START,
            "SC4_EXACT_FINAL_HEAD": SC4_FINAL,
            "RCA1_EXACT_FINAL_HEAD": RCA1_FINAL,
            "TESTED_SHA": head,
            "DATABASE_MAJOR": version,
            "EXECUTION_ENVIRONMENT": "CI_EPHEMERAL_POSTGRESQL",
            "DATASET_MODE": "DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE",
            "IDENTITY_MODE": "SYNTHETIC_ONLY",
            "P1_RESULT": "RECONCILED_WITH_EXPECTED_GAPS",
            "P2_RESULT": "RECONCILED_WITH_MIGRATION_GAPS",
            "READ_ONLY_BOUNDARY": "ENFORCED",
            "QUERY_ALLOWLIST": "ENFORCED",
            "CHECKPOINT_BOUNDARY": "ENFORCED",
            "LINEAGE_BOUNDARY": "ENFORCED",
            "IDENTITY_BOUNDARY": "ENFORCED",
            "APPROVAL_STATUS": "PENDING_USER_REVIEW",
            "RUNTIME_WIRING": "NOT_AUTHORIZED",
            "PRODUCTION_DATABASE": "FORBIDDEN",
            "AUTHORITY_TRANSFER": "NONE",
        }
        for key, value in expected.items():
            need(summary.get(key) == value, f"summary {key}={summary.get(key)!r}")
        need(re.fullmatch(r"[0-9a-f]{64}", summary.get("SEED_DIGEST", "")) is not None, "seed digest")
        need(int(summary.get("SEED_CASE_COUNT", "0")) >= 60, "seed case inventory")
        return "execution summary, seed and lane verdicts"
    record("runtime_summary_and_seed", summary_check)

    def role_check() -> str:
        role = {row["attribute"]: row["value"] for row in rows(directory / "RCA1B_ROLE_ATTRIBUTES.tsv")}
        for key in ("rolsuper", "rolinherit", "rolcreaterole", "rolcreatedb", "rolreplication", "rolbypassrls",
                    "owns_table", "write_privilege", "sequence_privilege", "privileged_function_execute", "nonallowlisted_select"):
            need(role.get(key) == "false", f"role capability {key}")
        need(role.get("rolcanlogin") == "true" and role.get("allowlisted_select") == "true", "role login/select")
        return "ephemeral least-privilege role attributes"
    record("role_grant_boundary", role_check)

    def server_check() -> str:
        state = {row["setting"]: row["value"] for row in rows(directory / "RCA1B_SERVER_STATE.tsv")}
        expected = {
            "transaction_read_only": "on", "transaction_isolation": "repeatable read",
            "statement_timeout": "5s", "lock_timeout": "1s",
            "idle_in_transaction_session_timeout": "5s", "TimeZone": "UTC",
            "max_parallel_workers_per_gather": "0",
        }
        need(state == expected, f"server state {state}")
        return "server-visible transaction and resource state"
    record("server_visible_read_only_and_limits", server_check)

    def permission_check() -> str:
        negative = rows(directory / "RCA1B_PERMISSION_NEGATIVE_RESULTS.tsv")
        ids = {row["testId"] for row in negative}
        required = {"insert", "update", "delete", "merge", "create_table", "create_temp_table", "alter_table",
                    "drop_table", "truncate", "create_function", "create_trigger", "create_sequence",
                    "copy_server_file", "nonallowlisted_select", "canonical_dataset_select", "release_evidence_select",
                    "identity_sensitive_select", "write_function_execute", "sequence_read", "lock_timeout"}
        need(required <= ids, f"negative tests missing {sorted(required-ids)}")
        need(all(row["status"] == "BLOCKED" for row in negative), "permission test not blocked")
        return f"{len(negative)} blocked permission and timeout negatives"
    record("permission_negative_and_timeout", permission_check)

    def evidence_check() -> str:
        evidence = rows(directory / "RCA1B_RECONCILIATION_EVIDENCE.tsv")
        need(list(evidence[0].keys()) == EVIDENCE_FIELDS, "evidence field order")
        keys = [(row["hashedCaseId"], row["lane"], row["comparisonDimension"], row["queryId"], row["databaseVersion"]) for row in evidence]
        need(len(keys) == len(set(keys)), "duplicate evidence key")
        need(P1_DIMS <= {row["comparisonDimension"] for row in evidence if row["lane"] == "P1"}, "P1 evidence dimensions")
        need(P2_DIMS <= {row["comparisonDimension"] for row in evidence if row["lane"] == "P2"}, "P2 evidence dimensions")
        text = (directory / "RCA1B_RECONCILIATION_EVIDENCE.json").read_text(encoding="utf-8")
        forbidden = ("jdbc:", "localhost", "127.0.0.1", "password", "rca1b_owner", "user:", "subject:",
                     "session:", "rca1b-exposure", "SELECT ", "INSERT ")
        need(not [token for token in forbidden if token.lower() in text.lower()], "evidence redaction")
        return "fixed-order duplicate-free redacted evidence"
    record("evidence_schema_redaction_and_dimensions", evidence_check)

    def counter_check() -> str:
        counters = {row["counter"]: int(row["value"]) for row in rows(directory / "RCA1B_VERIFICATION_COUNTERS.tsv")}
        need(counters.get("database_query_count") == 7, "database query count")
        need(counters.get("database_write_attempt_blocked_count", 0) >= 15, "write blocks")
        need(counters.get("database_query_failure_count", 0) >= 20, "negative query failures")
        need(counters.get("result_row_limit_exceeded_count") == 1, "row limit counter")
        need(counters.get("timeout_count") == 1, "timeout counter")
        need(counters.get("p1_query_result_mismatch_count") == 0, "P1 mismatch")
        need(counters.get("p2_query_result_mismatch_count") == 0, "P2 mismatch")
        return "verification counters distinguish baseline and expected negatives"
    record("verification_counters", counter_check)

    def query_check() -> str:
        inventory = rows(directory / "RCA1B_QUERY_INVENTORY.tsv")
        need({row["queryId"] for row in inventory} == QUERY_IDS, "runtime query inventory")
        need(all(row["maxRows"] == "1000" for row in inventory), "runtime row limit")
        return "runtime seven-query inventory"
    record("runtime_query_inventory", query_check)

    def teardown() -> str:
        state = {row["key"]: row["value"] for row in rows(directory / "RCA1B_TEARDOWN.tsv")}
        need(state == {"container_stopped": "true", "persistent_state_retained": "false"}, f"teardown {state}")
        return "container destroyed and no state retained"
    record("container_teardown", teardown)
    return checks


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--database-version", choices=("15", "18"))
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    head = sh("git", "rev-parse", "HEAD")
    checks: list[dict[str, str]] = []
    failures: list[str] = []
    try:
        checks.extend(static_checks(head))
        if args.database_version:
            checks.extend(runtime_checks(args.database_version, head))
    except Exception as exception:
        failures.append(str(exception))
        checks.append({"check": "verifier", "status": "FAIL", "detail": str(exception)})
    for name in ("runtime_dark_read", "production_database_validation", "production_traffic", "canary", "load",
                 "replay", "actual_identity_mapping", "production_credential_validation", "production_activation"):
        checks.append({"check": name, "status": "NOT_APPLICABLE" if name == "runtime_dark_read" else "NOT_EXECUTED",
                       "detail": "outside RCA-1B non-production read-only scope"})
    suffix = f"_PG{args.database_version}" if args.database_version else "_STATIC"
    summary = {
        "contractId": "rca1b-independent-verification-v1",
        "workStartSha": WORK_START,
        "sc4ExactFinalHead": SC4_FINAL,
        "rca1ExactFinalHead": RCA1_FINAL,
        "testedSha": head,
        "databaseVersion": args.database_version or "NOT_APPLICABLE",
        "result": "PASS" if not failures else "FAIL",
        "checks": checks,
        "failures": failures,
    }
    (OUT / f"RCA1B_VERIFICATION_SUMMARY{suffix}.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    with (OUT / f"RCA1B_VERIFICATION_SUMMARY{suffix}.tsv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(("check", "status", "detail", "tested_sha"))
        for check in checks:
            writer.writerow((check["check"], check["status"], check["detail"], head))
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
