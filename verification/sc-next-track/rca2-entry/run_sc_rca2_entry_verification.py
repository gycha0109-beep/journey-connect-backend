#!/usr/bin/env python3
from __future__ import annotations

import csv
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "verification/sc-next-track/rca2-entry/runtime"
GOV = ROOT / "docs/platform/governance"
EV = ROOT / "verification/sc-next-track/rca2-entry"
SQL = ROOT / "database/journey-connect-db-v2.7"
START = "3efbf96ebf25ae1645a62f35269c4b569425a9ca"
RCA1B_FINAL = "dbb6b5397ad0fe675856b195e280faf9a0f3030c"
DOCS = [
    "SC-5-RCA-2-ENTRY-AUTHORIZATION-AND-EXECUTION-BOUNDARY.md",
    *[f"{number}-SC-RCA2-{name}.md" for number, name in [
        (38,"RUNTIME-ENVIRONMENT-DECISION"),(39,"RUNTIME-MODEL-DECISION"),
        (40,"FEATURE-FLAG-AND-TRAFFIC-POLICY"),(41,"PRIMARY-SHADOW-AUTHORITY-CONTRACT"),
        (42,"TIMEOUT-FALLBACK-CIRCUIT-BREAKER-POLICY"),(43,"CREDENTIAL-AND-NETWORK-BOUNDARY"),
        (44,"RUNTIME-QUERY-BOUNDARY"),(45,"IDENTITY-AND-PRIVACY-GOVERNANCE"),
        (46,"P1-RUNTIME-DARK-READ-DECISION"),(47,"P2-RUNTIME-DARK-READ-DECISION"),
        (48,"CHECKPOINT-LINEAGE-FRESHNESS-DECISION"),(49,"OBSERVABILITY-METRIC-LOGGING-POLICY"),
        (50,"ALERT-AND-KILL-SWITCH-POLICY"),(51,"DEPLOYMENT-AND-ROLLBACK-POLICY"),
        (52,"DB-SQL-IMPACT-DECISION"),(53,"OPERATIONS-RELIABILITY-APPROVAL-MATRIX"),
        (54,"VERIFICATION-PLAN"),(55,"EXIT-CRITERIA-AND-AUTHORITY-TRANSFER-BOUNDARY")]],
    "56-RCA-2-IMPLEMENTATION-HANDOFF-PROMPT.md",
]
SECTIONS = {"Scope","Current Baseline","Decision","Rationale","Authority","Dependencies",
"Runtime Environment","Runtime Model","Feature Flag","Traffic Boundary","Primary/Shadow Authority",
"Timeout/Fallback","Credential/Network","Identity/Privacy","P1 Result Boundary","P2 Result Boundary",
"Checkpoint/Lineage","Observability","Rollback","DB/SQL Impact","Production Impact","Verification",
"Risks","Exit Criteria","Handoff"}


def sh(command: list[str], check: bool = True) -> str:
    return subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, check=check).stdout.strip()

def git(*args: str) -> str:
    return sh(["git", *args])

def need(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)

def rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        data = list(reader)
    need(bool(reader.fieldnames) and bool(data), f"invalid TSV: {path.name}")
    signatures = [tuple(row.get(field, "") for field in reader.fieldnames) for row in data]
    need(len(signatures) == len(set(signatures)), f"duplicate TSV row: {path.name}")
    return data

def values(path: Path, key: str = "key", value: str = "value") -> dict[str, str]:
    return {row[key]: row[value] for row in rows(path)}


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    checks: list[dict[str, str]] = []
    failures: list[str] = []
    head = "UNKNOWN"

    def record(name: str, function, command: str = "NOT_APPLICABLE") -> None:
        try:
            checks.append({"check": name, "status": "PASS", "command": command,
                           "detail": str(function() or "verified")})
        except Exception as exc:
            failures.append(f"{name}: {exc}")
            checks.append({"check": name, "status": "FAIL", "command": command, "detail": str(exc)})

    try:
        head = git("rev-parse", "HEAD")

        def baseline() -> str:
            base = values(EV / "SC_RCA2_BASELINE.tsv")
            for key, expected in {
                "work_start_sha": START, "rca1b_exact_final_head": RCA1B_FINAL,
                "pr27_merged": "YES", "merge_tree_equivalent": "YES",
                "cross_version_result": "PASS", "p1_result": "RECONCILED_WITH_EXPECTED_GAPS",
                "p2_result": "RECONCILED_WITH_MIGRATION_GAPS", "production_db": "FORBIDDEN_NOT_USED",
                "production_traffic": "NONE", "actual_identity_mapping": "NOT_IMPLEMENTED",
                "sql_53_plus": "ABSENT_UNALLOCATED", "current_authority": "UNCHANGED",
            }.items():
                need(base.get(key) == expected, f"baseline mismatch: {key}")
            sh(["git", "merge-base", "--is-ancestor", START, head])
            return "actual work-start and RCA-1B baseline verified"
        record("actual_authoritative_work_start", baseline, f"git merge-base --is-ancestor {START} {head}")

        def merge_tree() -> str:
            need("Merge pull request #27" in git("show", "-s", "--format=%B", START), "PR #27 merge absent")
            sh(["git", "merge-base", "--is-ancestor", RCA1B_FINAL, START])
            sh(["git", "diff", "--quiet", RCA1B_FINAL, START])
            return "PR #27 merge and exact-head tree equivalence verified"
        record("pr27_merge_and_rca1b_tree_equivalence", merge_tree,
               f"git diff --quiet {RCA1B_FINAL} {START}")

        def documents() -> str:
            inventory = rows(EV / "SC_RCA2_DOCUMENTS.tsv")
            need(len(inventory) == 20, "document inventory mismatch")
            need({Path(row["path"]).name for row in inventory} == set(DOCS), "document names mismatch")
            for name in DOCS:
                text = (GOV / "sc-next-track" / name).read_text(encoding="utf-8")
                headings = set(re.findall(r"^##\s+(.+)$", text, re.MULTILINE))
                need(SECTIONS <= headings, f"sections missing in {name}: {sorted(SECTIONS-headings)}")
                need(START in text and RCA1B_FINAL in text, f"baseline SHA missing in {name}")
            handoff = (GOV / "sc-next-track/56-RCA-2-IMPLEMENTATION-HANDOFF-PROMPT.md").read_text(encoding="utf-8")
            for marker in ("dedicated bounded executor", "ASYNC_POST_RESPONSE_SHADOW", "Production Impact"):
                need(marker in handoff, f"handoff marker missing: {marker}")
            return "20 documents and implementation handoff verified"
        record("required_documents_and_handoff", documents)

        def decisions() -> str:
            entries = rows(EV / "SC_RCA2_ENTRY_DECISIONS.tsv")
            d = {row["decision"]: row["value"] for row in entries}
            expected = {"entry_result":"RCA2_ENTRY_AUTHORIZED","execution_environment":"ISOLATED_NON_PRODUCTION_RUNTIME",
                "runtime_model":"ASYNC_POST_RESPONSE_SHADOW","feature_flag_required":"YES",
                "feature_flag_default":"OFF","initial_traffic_percent":"0",
                "max_production_dark_read_percent":"0","primary_result_authority":"CURRENT_P1_P2_ONLY",
                "shadow_result_authority":"NONE","shadow_result_serving":"FORBIDDEN",
                "shadow_failure_fallback":"KEEP_PRIMARY_RESULT","identity_mode":"SYNTHETIC_OR_TEST_ACCOUNT_ONLY",
                "runtime_freshness_policy":"BLOCKED_PENDING_MEASUREMENT","retry_policy":"NONE",
                "db_change":"NONE","sql_allocation":"NOT_REQUIRED","production_activation":"NOT_AUTHORIZED",
                "authority_transfer":"FORBIDDEN","implementation":"REQUIRES_SEPARATE_PR"}
            for key, expected_value in expected.items():
                need(d.get(key) == expected_value, f"decision mismatch: {key}")
            need(sum(row["decision"] == "execution_environment" for row in entries) == 1, "environment not singular")
            need(sum(row["decision"] == "runtime_model" for row in entries) == 1, "runtime model not singular")
            return "singular environment/model and entry decisions verified"
        record("singular_entry_decisions", decisions)

        def runtime() -> str:
            r = values(EV / "SC_RCA2_RUNTIME_CONTRACT.tsv")
            finite = ("shadow_connection_timeout_ms","shadow_read_timeout_ms","shadow_total_timeout_ms",
                      "task_queue_timeout_ms","max_task_age_ms","max_shadow_concurrency","max_shadow_queue_depth",
                      "breaker_minimum_sample_count","breaker_open_duration_seconds","breaker_half_open_probe_count",
                      "flag_refresh_interval_seconds","flag_stale_after_seconds","flag_max_ttl_days")
            for key in finite:
                need(int(r.get(key, "0")) > 0, f"finite boundary missing: {key}")
            need(r.get("retry_policy") == "NONE" and r.get("late_result_policy") == "DISCARD", "retry/late mismatch")
            need(r.get("primary_budget_extension") == "FORBIDDEN", "primary budget extension allowed")
            stages = rows(EV / "SC_RCA2_TRAFFIC_STAGES.tsv")
            need(stages[0]["traffic_percent"] == "0", "initial traffic not zero")
            prod = [row for row in stages if row["stage"] == "PROD"]
            need(len(prod) == 1 and prod[0]["traffic_percent"] == "0", "production ceiling not zero")
            rollback = rows(EV / "SC_RCA2_ROLLBACK.tsv")
            need([row["level"] for row in rollback] == [f"LEVEL_{i}" for i in range(1,8)], "rollback mismatch")
            return "finite runtime, zero traffic and seven rollback levels verified"
        record("runtime_traffic_breaker_and_rollback", runtime)

        def boundaries() -> str:
            identity = values(EV / "SC_RCA2_IDENTITY_GOVERNANCE.tsv", "decision", "value")
            need(identity.get("identity_mode") == "SYNTHETIC_OR_TEST_ACCOUNT_ONLY", "identity mismatch")
            need(identity.get("actual_production_identity") == "BLOCKED", "actual identity not blocked")
            queries = rows(EV / "SC_RCA2_QUERY_BOUNDARY.tsv")
            need(len([r for r in queries if r["query_or_contract"].startswith("RCA1B_") and r["status"] == "TEST_ONLY"]) == 7,
                 "RCA-1B test query inventory mismatch")
            lane = rows(EV / "SC_RCA2_LANE_BOUNDARIES.tsv")
            markers = {row["marker"] for row in lane}
            for marker in ("P1_RUNTIME_DARK_READ_ONLY","CURRENT_P1_AUTHORITY_UNCHANGED","P1_SHADOW_RESULT_NOT_SERVED",
                           "P2_RUNTIME_DARK_READ_ONLY","CURRENT_P2_AUTHORITY_UNCHANGED","P2_SHADOW_RESULT_NOT_SERVED",
                           "NO_AUTHORITY_TRANSFER","STALE_UNEXPOSED_ASSIGNMENT_GAP","OBSERVATION_DEDUPE_GAP",
                           "CANONICAL_DATASET_HASH_PROTECTED","RELEASE_EVIDENCE_PROTECTED"):
                need(marker in markers, f"lane marker missing: {marker}")
            metrics = rows(EV / "SC_RCA2_METRICS.tsv")
            need(len(metrics) == 17, "metric inventory count mismatch")
            forbidden = re.compile(r"(user|subject|session|run|exposure|request)_id", re.I)
            need(all(not forbidden.search(row["allowed_labels"]) for row in metrics), "identity label found")
            approvals = {row["role"]: row["approval"] for row in rows(EV / "SC_RCA2_APPROVAL_MATRIX.tsv")}
            for role in ("Intelligence","Reliability","Operations","Privacy/Security","System Coordination"):
                need(approvals.get(role) == "BLOCKING_APPROVAL", f"approval missing: {role}")
            need(approvals.get("Data") == "REQUIRED", "Data classification mismatch")
            return "identity, query, lanes, metrics and approvals verified"
        record("identity_query_lane_observability_approvals", boundaries)

        def governance() -> str:
            combined = "\n".join((GOV / name).read_text(encoding="utf-8") for name in (
                "JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md","JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md",
                "SC-PLATFORM-REGISTRY.md","SC-DECISION-REGISTER.md","SC-RACI.md","SC-HANDOFF.md"))
            for marker in ("RCA2_ENTRY_AUTHORIZED","ISOLATED_NON_PRODUCTION_RUNTIME","ASYNC_POST_RESPONSE_SHADOW",
                "FEATURE_FLAG_DEFAULT=OFF","INITIAL_TRAFFIC_PERCENT=0","PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY",
                "SHADOW_RESULT_AUTHORITY=NONE","SHADOW_RESULT_SERVING=FORBIDDEN",
                "SHADOW_FAILURE_FALLBACK=KEEP_PRIMARY_RESULT","IDENTITY_MODE=SYNTHETIC_OR_TEST_ACCOUNT_ONLY",
                "RUNTIME_FRESHNESS_POLICY=BLOCKED_PENDING_MEASUREMENT","SQL_ALLOCATION=NOT_REQUIRED",
                "PRODUCTION_ACTIVATION=NOT_AUTHORIZED","AUTHORITY_TRANSFER=FORBIDDEN",
                "reserved for Reliability Platform","RecommendationP1ProfileSource","RecommendationP2ObservationSource",
                "RCA1B_NONPRODUCTION_READONLY_RECONCILIATION_COMPLETE","CROSS_VERSION_RESULT_EQUIVALENCE=PASS",
                "READ_ONLY_BOUNDARY=ENFORCED","QUERY_ALLOWLIST=ENFORCED","CHECKPOINT_BOUNDARY=ENFORCED",
                "LINEAGE_BOUNDARY=ENFORCED"):
                need(marker in combined, f"governance marker missing: {marker}")
            need(not re.search(r"\bRP\s*(?:=|:|means)\s*Recommendation(?:\s+Platform)?\b", combined, re.I), "RP conflict")
            return "governance and RCA/RP naming aligned"
        record("governance_registry_alignment", governance)

        def protected() -> str:
            for number in range(1,53):
                need(len(list(SQL.glob(f"{number:02d}_*.sql"))) == 1, f"SQL {number:02d} missing/duplicate")
            need(not list(SQL.glob("5[3-9]_*.sql")) and not list(SQL.glob("[6-9][0-9]_*.sql")), "SQL 53+ exists")
            p1 = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java").read_text(encoding="utf-8")
            p2 = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java").read_text(encoding="utf-8")
            need("recommendation_user_preference" in p1 and "recommendation_behavior_event" in p1, "P1 authority changed")
            for marker in ("recommendation_p2_experiment_exposure","interval '7 days'",
                           "b.event_type in ('click','like','save','share')","r.run_status = 'fallback'"):
                need(marker in p2, f"P2 authority marker missing: {marker}")
            changed = git("diff", "--name-only", f"{START}..{head}").splitlines()
            allowed = (
                ".github/actions/rca2-job/",
                ".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",
                ".github/workflows/sc-rca2-entry-ci.yml",
                "docs/platform/governance/",
                "docs/platform/recommendation/rca2/",
                "jc-backend/build.gradle.kts",
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java",
                "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/",
                "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml",
                "jc-backend/src/test/java/com/jc/backend/recommendation/rca2/",
                "jc-backend/src/test/java/com/jc/backend/verification/IP9ControlledBackendHookStaticTest.java",
                "jc-search-readiness/src/test/java/com/jc/intelligence/readiness/search/SearchShadowReadinessContractTest.java",
                "verification/rca0/run_rca0_verification.py",
                "verification/rca1/run_rca1_verification.py",
                "verification/rca1b/run_rca1b_verification.py",
                "verification/rca2/",
                "verification/data-platform-closure/run_data_platform_closure_verification.py",
                "verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py",
                "verification/sc-next-track/rca2-entry/",
            )
            unexpected = [item for item in changed if not any(item == prefix or item.startswith(prefix) for prefix in allowed)]
            need(not unexpected, f"unexpected changed files: {unexpected}")
            production_configs = [item for item in changed
                if re.search(r"jc-backend/src/main/resources/application.*\.(?:yml|yaml|properties)$", item)
                and item != "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml"]
            forbidden = [item for item in changed if item.startswith(("database/", "jc-recommendation-core/"))
                or item in (
                    "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java",
                    "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java",
                )]
            need(not forbidden and not production_configs,
                 f"protected source/core/SQL/production config changed: {forbidden + production_configs}")
            config = (ROOT / "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml").read_text(encoding="utf-8")
            for marker in ("flag: off", "traffic-percent: 0", "max-production-dark-read-percent: 0",
                           "production-route-allowed: false", "db-change: NONE", "sql-allocation: NOT_REQUIRED"):
                need(marker in config, f"RCA2 isolated config marker missing: {marker}")
            need(not re.search(r"https?://|jdbc:", config), "concrete route or DB connection in RCA2 config")
            feed = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java").read_text(encoding="utf-8")
            need("RCA-2 request registration failed open" in feed and "return response;" in feed,
                 "RCA2 fail-open primary response boundary missing")
            need("return registrar.registerFeed" not in feed and "return rca2Registrar" not in feed,
                 "RCA2 hook became response authority")
            runtime_source = "\n".join(path.read_text(encoding="utf-8")
                for path in (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/rca2").glob("*.java"))
            for token in ("JpaRepository", "JdbcTemplate", "EntityManager", "ApplicationEventPublisher",
                          "KafkaTemplate", "@Transactional", "ForkJoinPool.commonPool", "newCachedThreadPool",
                          "CallerRunsPolicy"):
                need(token not in runtime_source, f"forbidden RCA2 runtime dependency: {token}")
            return f"SC5 entry and authorized RCA2 implementation boundaries verified: {len(changed)} files"
        record("sql_authority_and_historical_protection", protected,
               f"git diff --name-only {START}..{head}")

        for row in rows(EV / "SC_RCA2_VERIFICATION_PLAN.tsv"):
            if row["sc5_status"] in {"NOT_EXECUTED","NOT_APPLICABLE"}:
                checks.append({"check":row["check"],"status":row["sc5_status"],
                               "command":row["sc5_status"],"detail":row["evidence"]})
    except Exception as exc:
        failures.append(f"verifier_internal: {exc}")

    summary = {"contractId":"sc-rca2-entry-authorization-v1","workStartSha":START,
               "rca1bExactFinalHead":RCA1B_FINAL,"testedSha":head,
               "result":"PASS" if not failures else "FAIL","checks":checks,"failures":failures}
    (OUT / "SC_RCA2_ENTRY_VERIFICATION.json").write_text(json.dumps(summary, indent=2, sort_keys=True)+"\n", encoding="utf-8")
    with (OUT / "SC_RCA2_ENTRY_VERIFICATION.tsv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(["check","status","command","detail","tested_sha"])
        for check in checks:
            writer.writerow([check["check"],check["status"],check["command"],check["detail"],head])
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 1 if failures else 0

if __name__ == "__main__":
    sys.exit(main())
