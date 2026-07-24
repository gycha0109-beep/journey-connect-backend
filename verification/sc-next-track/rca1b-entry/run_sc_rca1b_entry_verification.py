#!/usr/bin/env python3
from __future__ import annotations

import csv
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "verification/sc-next-track/rca1b-entry/runtime"
GOV = ROOT / "docs/platform/governance"
EVIDENCE = ROOT / "verification/sc-next-track/rca1b-entry"
SQL = ROOT / "database/journey-connect-db-v2.7"
START = "b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4"
RCA1_FINAL = "38896b2a37180633870282e9d9e305d9c9fbbf8a"

DOCS = [
    "SC-4-RCA-1B-ENTRY-AUTHORIZATION-AND-EXECUTION-BOUNDARY.md",
    "23-SC-RCA1B-EXECUTION-ENVIRONMENT-DECISION.md",
    "24-SC-RCA1B-POSTGRESQL-VERSION-AND-COMPATIBILITY-DECISION.md",
    "25-SC-RCA1B-DB-READ-ONLY-EXECUTION-CONTRACT.md",
    "26-SC-RCA1B-ROLE-GRANT-AND-SQL-ALLOCATION-DECISION.md",
    "27-SC-RCA1B-ALLOWED-QUERY-BOUNDARY.md",
    "28-SC-RCA1B-REPRODUCIBLE-DATASET-DECISION.md",
    "29-SC-RCA1B-IDENTITY-PRIVACY-DECISION.md",
    "30-SC-RCA1B-P1-DB-RECONCILIATION-AUTHORITY-AND-EQUIVALENCE.md",
    "31-SC-RCA1B-P2-DB-RECONCILIATION-AUTHORITY-AND-EQUIVALENCE.md",
    "32-SC-RCA1B-CHECKPOINT-FRESHNESS-LINEAGE-DECISION.md",
    "33-SC-RCA1B-EVIDENCE-REDACTION-RETENTION-POLICY.md",
    "34-SC-RCA1B-OPERATIONS-RELIABILITY-PREREQUISITE-MATRIX.md",
    "35-SC-RCA1B-VERIFICATION-PLAN.md",
    "36-SC-RCA1B-EXIT-CRITERIA-AND-RCA2-HANDOFF.md",
    "37-RCA-1B-IMPLEMENTATION-HANDOFF-PROMPT.md",
]
SECTIONS = {
    "Scope", "Current Baseline", "Decision", "Rationale", "Authority", "Dependencies",
    "Execution Environment", "DB Access Boundary", "Query Boundary", "Identity/Privacy",
    "Evidence", "DB/SQL Impact", "Production Impact", "Verification", "Risks", "Exit Criteria", "Handoff",
}
P1_DB_DIMENSIONS = {
    "QUERY_RESULT_PARITY", "CHECKPOINT_PARITY", "SNAPSHOT_ISOLATION_PARITY", "ROW_ORDER_PARITY",
    "NULL_SEMANTICS_PARITY", "NUMERIC_NORMALIZATION_PARITY", "TIMEZONE_NORMALIZATION_PARITY",
    "DUPLICATE_ROW_DETECTION", "SOURCE_ROW_COUNT_PARITY",
}
P2_DB_DIMENSIONS = {
    "QUERY_RESULT_PARITY", "CHECKPOINT_PARITY", "EXPOSURE_ROW_UNIQUENESS", "OUTCOME_ROW_UNIQUENESS",
    "DUPLICATE_OBSERVATION_DETECTION", "WINDOW_BOUNDARY_SQL_PARITY", "EVENT_TYPE_FILTER_PARITY",
    "FALLBACK_JOIN_PARITY", "ASSIGNMENT_VERSION_JOIN_PARITY", "SOURCE_ROW_COUNT_PARITY",
}
CONTRACT_IDS = {
    "recommendation-shadow-reconciliation-v1",
    "recommendation-shadow-reconciliation-evidence-v1",
    "recommendation-shadow-reconciliation-fixture-v1",
}
QUERY_IDS = {
    "P1_AUTHORITATIVE_REFERENCE_V1", "P1_DATA_CANDIDATE_V1",
    "P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1", "P2_DATA_CANDIDATE_V1",
    "SOURCE_CHECKPOINT_V1", "SOURCE_LINEAGE_V1", "BOUNDED_ROW_COUNT_V1",
}


def sh(command: list[str], check: bool = True) -> str:
    result = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, check=check)
    return result.stdout.strip()


def git(*args: str) -> str:
    return sh(["git", *args])


def need(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        result = list(reader)
    need(bool(reader.fieldnames) and bool(result), f"invalid TSV: {path.name}")
    signatures = [tuple(row.get(field, "") for field in reader.fieldnames) for row in result]
    need(len(signatures) == len(set(signatures)), f"duplicate TSV row: {path.name}")
    return result


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    checks: list[dict[str, str]] = []
    failures: list[str] = []
    head = "UNKNOWN"

    def record(name: str, function, command: str = "NOT_APPLICABLE") -> None:
        try:
            detail = function() or "verified"
            checks.append({"check": name, "status": "PASS", "command": command, "detail": str(detail)})
        except Exception as exc:  # verifier must preserve every assertion failure
            failures.append(f"{name}: {exc}")
            checks.append({"check": name, "status": "FAIL", "command": command, "detail": str(exc)})

    try:
        head = git("rev-parse", "HEAD")

        def baseline() -> str:
            data = {row["key"]: row["value"] for row in rows(EVIDENCE / "SC_RCA1B_BASELINE.tsv")}
            need(data.get("work_start_sha") == START, "work-start mismatch")
            need(data.get("rca1_exact_final_head") == RCA1_FINAL, "RCA-1 final-head mismatch")
            return "exact authoritative baseline recorded"
        record("exact_authoritative_work_start", baseline)
        record("work_start_is_ancestor", lambda: sh(["git", "merge-base", "--is-ancestor", START, head]) or "ancestor verified",
               f"git merge-base --is-ancestor {START} {head}")

        def merge() -> str:
            message = git("show", "-s", "--format=%B", START)
            need("Merge pull request #25" in message, "PR #25 merge marker absent")
            sh(["git", "merge-base", "--is-ancestor", RCA1_FINAL, START])
            sh(["git", "diff", "--quiet", RCA1_FINAL, START])
            return "PR #25 merged and exact-final-head tree equivalent"
        record("rca1_merge_and_tree_equivalence", merge, f"git diff --quiet {RCA1_FINAL} {START}")

        def rca1_inventory() -> str:
            contracts = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption/reconciliation/Rca1Contracts.java").read_text(encoding="utf-8")
            need(CONTRACT_IDS <= set(re.findall(r'"([a-z0-9-]+-v1)"', contracts)), "RCA-1 contract ID missing")
            p1 = rows(ROOT / "jc-backend/src/test/resources/recommendation-data-adoption/reconciliation/p1-reconciliation-fixtures-v1.tsv")
            p2 = rows(ROOT / "jc-backend/src/test/resources/recommendation-data-adoption/reconciliation/p2-reconciliation-fixtures-v1.tsv")
            need(len(p1) == 23 and len(p2) == 39, "RCA-1 fixture count mismatch")
            need({row["scenario"] for row in p1}.__len__() == 23, "duplicate P1 scenario")
            need({row["scenario"] for row in p2}.__len__() == 39, "duplicate P2 scenario")
            need("RECONCILED_WITH_EXPECTED_GAPS" in contracts and "RECONCILED_WITH_MIGRATION_GAPS" in contracts, "lane verdict markers absent")
            need('IDENTITY_MODE = "SYNTHETIC_ONLY"' in contracts, "synthetic identity marker absent")
            return "3 RCA-1 contracts; 23 P1 and 39 P2 fixtures"
        record("rca1_contract_fixture_and_lane_baseline", rca1_inventory)

        def documents() -> str:
            base = GOV / "sc-next-track"
            inventory = rows(EVIDENCE / "SC_RCA1B_DOCUMENTS.tsv")
            need(len(inventory) == 16, "document inventory must contain 16 files")
            need({Path(row["path"]).name for row in inventory} == set(DOCS), "document inventory mismatch")
            for name in DOCS:
                text = (base / name).read_text(encoding="utf-8")
                headings = set(re.findall(r"^##\s+(.+)$", text, re.MULTILINE))
                need(SECTIONS <= headings, f"required sections missing in {name}: {sorted(SECTIONS-headings)}")
                need(START in text, f"work-start missing in {name}")
            return "16 complete SC-4 decision/handoff documents"
        record("required_documents_and_handoff", documents)

        def decisions() -> str:
            data = {row["decision"]: (row["value"], row["status"]) for row in rows(EVIDENCE / "SC_RCA1B_ENTRY_DECISIONS.tsv")}
            expected = {
                "environment": "CI_EPHEMERAL_POSTGRESQL", "postgresql_version_matrix": "15,18",
                "dataset_mode": "DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE", "identity_mode": "SYNTHETIC_ONLY",
                "transaction_read_only": "REQUIRED", "db_write": "FORBIDDEN", "production_db": "FORBIDDEN",
                "db_change": "NONE", "sql_allocation": "NOT_REQUIRED", "runtime_wiring": "NOT_AUTHORIZED",
            }
            for key, value in expected.items():
                need(data.get(key, (None,))[0] == value, f"decision mismatch: {key}")
            need(len([row for row in rows(EVIDENCE / "SC_RCA1B_ENTRY_DECISIONS.tsv") if row["decision"] == "environment"]) == 1, "execution environment not singular")
            return "single environment/version/dataset/identity decisions"
        record("decision_uniqueness", decisions)

        def execution_contract() -> str:
            data = {row["key"]: row["value"] for row in rows(EVIDENCE / "SC_RCA1B_EXECUTION_CONTRACT.tsv")}
            need(data.get("transaction_isolation") == "REPEATABLE_READ", "isolation mismatch")
            need(data.get("transaction_read_only") == "REQUIRED", "read-only not required")
            for key in ("statement_timeout_ms", "lock_timeout_ms", "idle_in_transaction_timeout_ms",
                        "max_result_rows_per_query", "max_reconciliation_cases", "max_execution_duration_seconds",
                        "max_reconciliation_connections", "cursor_fetch_size"):
                need(int(data.get(key, "0")) > 0, f"finite positive limit missing: {key}")
            need(data.get("parallel_query") == "DISABLED" and data.get("retry_policy") == "NONE", "parallel/retry boundary mismatch")
            need(data.get("max_checkpoint_lag") == "0" and data.get("lineage_fingerprint") == "REQUIRED", "checkpoint/lineage boundary mismatch")
            return "finite read-only and checkpoint/lineage boundaries"
        record("read_only_resource_checkpoint_contract", execution_contract)

        def query_and_role() -> str:
            query_rows = rows(EVIDENCE / "SC_RCA1B_QUERY_ALLOWLIST.tsv")
            need({row["query_id"] for row in query_rows} == QUERY_IDS, "query allowlist mismatch")
            role = {row["property"]: row["value"] for row in rows(EVIDENCE / "SC_RCA1B_ROLE_GRANT.tsv")}
            for key in ("inherit_allowed", "bypassrls_allowed", "createdb_allowed", "createrole_allowed", "replication_allowed", "sequence_select"):
                need(role.get(key) == "NO", f"forbidden role capability: {key}")
            need(role.get("write_grant") == "FORBIDDEN" and role.get("owner_role_use") == "FORBIDDEN", "write/owner boundary mismatch")
            return "7 allowlisted query IDs and least-privilege role policy"
        record("query_allowlist_and_role_policy", query_and_role)

        def dimensions() -> str:
            inventory = rows(EVIDENCE / "SC_RCA1B_DB_DIMENSIONS.tsv")
            p1 = {row["dimension"] for row in inventory if row["lane"] == "P1"}
            p2 = {row["dimension"] for row in inventory if row["lane"] == "P2"}
            need(p1 == P1_DB_DIMENSIONS, f"P1 DB dimension mismatch: {sorted(p1 ^ P1_DB_DIMENSIONS)}")
            need(p2 == P2_DB_DIMENSIONS, f"P2 DB dimension mismatch: {sorted(p2 ^ P2_DB_DIMENSIONS)}")
            return "P1 and P2 DB dimensions remain separate"
        record("lane_specific_db_dimensions", dimensions)

        def governance_markers() -> str:
            combined = "\n".join((GOV / name).read_text(encoding="utf-8") for name in (
                "JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md", "JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md",
                "SC-PLATFORM-REGISTRY.md", "SC-DECISION-REGISTER.md", "SC-RACI.md", "SC-HANDOFF.md"))
            for marker in (
                "RCA1B_ENTRY_AUTHORIZED", "CI_EPHEMERAL_POSTGRESQL", "POSTGRESQL_VERSION_MATRIX=15,18",
                "DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE", "TRANSACTION_READ_ONLY=REQUIRED",
                "PRODUCTION_DB=FORBIDDEN", "SQL_ALLOCATION=NOT_REQUIRED", "reserved for Reliability Platform",
                "RecommendationP1ProfileSource", "RecommendationP2ObservationSource",
                "PRODUCTION_ACTIVATION=NOT_AUTHORIZED", "CURRENT_P1_P2_AUTHORITY_UNCHANGED"):
                need(marker in combined, f"governance marker missing: {marker}")
            need("RP=Recommendation" not in combined and "RP means Recommendation" not in combined, "RP naming conflict")
            return "registry, authority and RCA/RP governance aligned"
        record("governance_registry_authority_alignment", governance_markers)

        def protected_state() -> str:
            for number in range(1, 53):
                need(len(list(SQL.glob(f"{number:02d}_*.sql"))) == 1, f"SQL {number:02d} missing or duplicated")
            need(not list(SQL.glob("5[3-9]_*.sql")) and not list(SQL.glob("[6-9][0-9]_*.sql")), "SQL 53+ exists")
            p1 = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java").read_text(encoding="utf-8")
            p2 = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java").read_text(encoding="utf-8")
            need("recommendation_user_preference" in p1 and "recommendation_behavior_event" in p1, "P1 authority marker missing")
            for marker in ("recommendation_p2_experiment_exposure", "interval '7 days'", "b.event_type in ('click','like','save','share')", "r.run_status = 'fallback'"):
                need(marker in p2, f"P2 authority marker missing: {marker}")
            prod = (ROOT / "jc-backend/src/main/resources/application-prod.yml").read_text(encoding="utf-8")
            for marker in ("enabled: ${JC_SEARCH_SHADOW_PRODUCTION_ENABLED:false}", "kill-switch: ${JC_SEARCH_SHADOW_PRODUCTION_KILL_SWITCH:true}", "sampling-bps: ${JC_SEARCH_SHADOW_PRODUCTION_SAMPLING_BPS:0}"):
                need(marker in prod, f"production default missing: {marker}")
            return "SQL 01..52, SQL 53+ absence, sources and production defaults protected"
        record("protected_sql_source_and_production_state", protected_state)

        def diff_boundary() -> str:
            changed = git("diff", "--name-only", f"{START}..{head}").splitlines()
            allowed = (
                "docs/platform/governance/", "verification/sc-next-track/rca1b-entry/",
                "verification/sc-next-track/run_sc_next_track_reconciliation.py",
                "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
                ".github/workflows/sc-baseline-reconciliation.yml", ".github/workflows/sc-rca1b-entry-ci.yml",
            )
            unexpected = [path for path in changed if not any(path == prefix or path.startswith(prefix) for prefix in allowed)]
            need(not unexpected, f"unexpected changed files: {unexpected}")
            historical = [path for path in changed if path.startswith((
                "verification/rca0/", "verification/rca1/", "docs/platform/recommendation/RCA-0-",
                "docs/platform/recommendation/RCA-1-", "jc-backend/src/main/", "jc-backend/src/test/",
                "jc-recommendation-core/", "database/", "jc-backend/src/main/resources/"))]
            need(not historical, f"historical/protected diff: {historical}")
            return f"governance-only diff: {len(changed)} files"
        record("governance_only_diff_and_historical_evidence", diff_boundary, f"git diff --name-only {START}..{head}")

        verification_rows = rows(EVIDENCE / "SC_RCA1B_VERIFICATION_PLAN.tsv")
        for row in verification_rows:
            checks.append({"check": row["check"], "status": row["sc4_status"], "command": "NOT_EXECUTED" if row["sc4_status"] == "NOT_EXECUTED" else "NOT_APPLICABLE", "detail": "SC-4 governance status inventory"})

    except Exception as exc:
        failures.append(f"verifier_internal: {exc}")

    summary = {
        "contractId": "sc-rca1b-entry-authorization-v1",
        "workStartSha": START,
        "rca1ExactFinalHead": RCA1_FINAL,
        "testedSha": head,
        "result": "PASS" if not failures else "FAIL",
        "checks": checks,
        "failures": failures,
    }
    (OUT / "SC_RCA1B_ENTRY_VERIFICATION.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    with (OUT / "SC_RCA1B_ENTRY_VERIFICATION.tsv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(["check", "status", "command", "detail", "tested_sha"])
        for check in checks:
            writer.writerow([check["check"], check["status"], check["command"], check["detail"], head])
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
