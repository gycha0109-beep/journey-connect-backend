#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
EV = ROOT / "verification/sc-next-track/rca2-nonzero-nonprod-entry"
GOV = ROOT / "docs/platform/governance"
DOC = GOV / "sc-next-track"
SQL = ROOT / "database/journey-connect-db-v2.7"
OUT = EV / "runtime"
WORK_START = "b57c344c9b4e332966fe9f6d36a5da66a5faae71"
RCA2_HEAD = "511b19f80cdd42bb2fafde0563c7388b4f5b5f48"
ARTIFACT_ID = "8621492010"
ARTIFACT_DIGEST = "9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760"
ALLOWED_STATUS = {"PASS", "FAIL", "NOT_EXECUTED", "NOT_APPLICABLE"}

DOCS = [
    "docs/platform/governance/sc-next-track/SC-6-RCA-2-NONZERO-NONPRODUCTION-TRAFFIC-STAGE1-AUTHORIZATION.md",
    "docs/platform/governance/sc-next-track/57-SC-RCA2-AUTHORITATIVE-BASELINE.md",
    "docs/platform/governance/sc-next-track/58-SC-RCA2-STAGE1-TRAFFIC-CONTRACT.md",
    "docs/platform/governance/sc-next-track/59-SC-RCA2-STAGE1-COHORT-SELECTION-CONTRACT.md",
    "docs/platform/governance/sc-next-track/60-SC-RCA2-STAGE1-ENDPOINT-BOUNDARY.md",
    "docs/platform/governance/sc-next-track/61-SC-RCA2-STAGE1-CREDENTIAL-BOUNDARY.md",
    "docs/platform/governance/sc-next-track/62-SC-RCA2-STAGE1-IDENTITY-ALLOWLIST-BOUNDARY.md",
    "docs/platform/governance/sc-next-track/63-SC-RCA2-STAGE1-OBSERVATION-WINDOW-POLICY.md",
    "docs/platform/governance/sc-next-track/64-SC-RCA2-STAGE1-SAFETY-THRESHOLD-POLICY.md",
    "docs/platform/governance/sc-next-track/65-SC-RCA2-STAGE1-IMMEDIATE-ABORT-POLICY.md",
    "docs/platform/governance/sc-next-track/66-SC-RCA2-STAGE1-METRIC-INVENTORY.md",
    "docs/platform/governance/sc-next-track/67-SC-RCA2-STAGE1-ALERT-POLICY.md",
    "docs/platform/governance/sc-next-track/68-SC-RCA2-STAGE1-CIRCUIT-BREAKER-CONTINUITY.md",
    "docs/platform/governance/sc-next-track/69-SC-RCA2-STAGE1-ROLLBACK-OWNERSHIP.md",
    "docs/platform/governance/sc-next-track/70-SC-RCA2-P1-STAGE1-BOUNDARY.md",
    "docs/platform/governance/sc-next-track/71-SC-RCA2-P2-STAGE1-BOUNDARY.md",
    "docs/platform/governance/sc-next-track/72-SC-RCA2-STAGE1-CHECKPOINT-LINEAGE-FRESHNESS-POLICY.md",
    "docs/platform/governance/sc-next-track/73-SC-RCA2-STAGE1-DB-SQL-IMPACT.md",
    "docs/platform/governance/sc-next-track/74-SC-RCA2-STAGE1-BLOCKING-APPROVAL-MATRIX.md",
    "docs/platform/governance/sc-next-track/75-RCA-2-STAGE1-EXECUTION-HANDOFF-PROMPT.md"
]
REQUIRED_METRICS = [
    "traffic_selection_evaluated_total",
    "traffic_selection_selected_total",
    "stable_hash_cohort_bucket",
    "shadow_submission_total",
    "shadow_execution_started_total",
    "shadow_execution_completed_total",
    "shadow_timeout_total",
    "shadow_exception_total",
    "shadow_queue_rejection_total",
    "shadow_late_discard_total",
    "shadow_cancellation_total",
    "shadow_executor_active",
    "shadow_executor_queue_depth",
    "shadow_task_age_milliseconds",
    "shadow_total_duration_milliseconds",
    "shadow_checkpoint_lag_seconds",
    "shadow_lineage_mismatch_total",
    "shadow_p1_expected_protected_gap_total",
    "shadow_p1_unexpected_mismatch_total",
    "shadow_p2_migration_gap_total",
    "shadow_p2_unexpected_mismatch_total",
    "shadow_redaction_failure_total",
    "shadow_response_mutation_total",
    "shadow_database_write_total",
    "shadow_event_emission_total",
    "shadow_production_route_detection_total",
    "shadow_authority_mismatch_total"
]


def sh(*args: str, check: bool = True) -> str:
    result = subprocess.run(args, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, check=False)
    if check and result.returncode != 0:
        raise AssertionError(f"command failed {args}: {result.stdout.strip()}")
    return result.stdout.strip()


def rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        data = list(reader)
    if not reader.fieldnames or not data:
        raise AssertionError(f"invalid TSV: {path}")
    signatures = [tuple(row.get(field, "") for field in reader.fieldnames) for row in data]
    if len(signatures) != len(set(signatures)):
        raise AssertionError(f"duplicate TSV rows: {path}")
    return data


def values(path: Path, key: str = "key", value: str = "value") -> dict[str, str]:
    return {row[key]: row[value] for row in rows(path)}


def check_authoritative_baseline(head: str) -> str:
    base = values(EV / "SC6_AUTHORITATIVE_BASELINE.tsv")
    expected = {
        "work_start_sha": WORK_START,
        "pr29_merged": "YES",
        "rca2_exact_final_head": RCA2_HEAD,
        "rca2_merge_tree_equivalent": "YES",
        "rca2_evidence_artifact_id": ARTIFACT_ID,
        "rca2_evidence_digest": ARTIFACT_DIGEST,
        "rca2_completion_marker": "RCA2_CONTROLLED_NONPRODUCTION_RUNTIME_DARK_READ_COMPLETE",
        "current_traffic_percent": "0",
        "production_traffic_percent": "0",
        "feature_flag_default": "OFF",
        "db_change": "NONE",
        "sql_allocation": "NOT_REQUIRED",
    }
    for key, value in expected.items():
        if base.get(key) != value:
            raise AssertionError(f"baseline mismatch {key}={base.get(key)}")
    if "Merge pull request #29" not in sh("git", "show", "-s", "--format=%B", WORK_START):
        raise AssertionError("PR #29 merge marker missing")
    sh("git", "merge-base", "--is-ancestor", WORK_START, head)
    sh("git", "merge-base", "--is-ancestor", RCA2_HEAD, WORK_START)
    sh("git", "diff", "--quiet", RCA2_HEAD, WORK_START)
    return "work-start, PR #29, RCA-2 head/tree and artifact identity verified"


def check_document_completeness(head: str) -> str:
    inventory = rows(EV / "SC6_DOCUMENTS.tsv")
    if len(inventory) != 20:
        raise AssertionError(f"document count {len(inventory)} != 20")
    actual = [row["path"] for row in inventory]
    if actual != DOCS:
        raise AssertionError("document inventory order/path mismatch")
    combined = []
    for relative in DOCS:
        path = ROOT / relative
        if not path.is_file():
            raise AssertionError(f"missing document {relative}")
        text = path.read_text(encoding="utf-8")
        for marker in (WORK_START, RCA2_HEAD, "NOT_EXECUTED"):
            if marker not in text:
                raise AssertionError(f"{relative} missing {marker}")
        combined.append(text)
    all_text = "\n".join(combined)
    for marker in (
        "RCA2_NONZERO_NONPRODUCTION_TRAFFIC_STAGE1_CONDITIONALLY_AUTHORIZED",
        "TARGET_TRAFFIC_PERCENT=1", "TRAFFIC_ENABLEMENT=BLOCKED_PENDING_ALL_CONDITIONS",
        "FEATURE_FLAG_DEFAULT=OFF", "MANUAL_ENABLEMENT_REQUIRED=YES",
        "AUTOMATIC_ROLLOUT=FORBIDDEN", "STABLE_HASH_PERCENTAGE",
        "HASHED_NONPRODUCTION_TEST_SUBJECT_REF", "RAW_IDENTITY_COHORT_KEY=FORBIDDEN",
        "MIN_OBSERVATION_DURATION_MINUTES=30", "MIN_SHADOW_EXECUTION_COUNT=100",
        "BOTH_CONDITIONS_REQUIRED=YES", "APPROVAL_STATUS=PENDING_USER_REVIEW",
        "STAGE1_EXECUTION_REQUIRES_SEPARATE_PR_OR_OPERATIONS_CHANGE",
    ):
        if marker not in all_text:
            raise AssertionError(f"document marker missing {marker}")
    return "20 SC-6 documents and execution handoff verified"


def check_machine_readable_evidence(head: str) -> str:
    required = [
        "SC6_AUTHORITATIVE_BASELINE.tsv", "SC6_TRAFFIC_STAGE_DECISION.tsv",
        "SC6_BLOCKER_INVENTORY.tsv", "SC6_COHORT_CONTRACT.tsv",
        "SC6_OWNER_BOUNDARIES.tsv", "SC6_OBSERVATION_CONTRACT.tsv",
        "SC6_METRIC_INVENTORY.tsv", "SC6_SAFETY_THRESHOLDS.tsv",
        "SC6_ABORT_CONDITIONS.tsv", "SC6_APPROVAL_MATRIX.tsv",
        "SC6_ROLLBACK_MATRIX.tsv", "SC6_DB_SQL_IMPACT.tsv",
        "SC6_DOCUMENTS.tsv", "SC6_VERIFICATION_PLAN.tsv",
    ]
    for name in required:
        rows(EV / name)
    decision = {r["decision"]: r["value"] for r in rows(EV / "SC6_TRAFFIC_STAGE_DECISION.tsv")}
    expected = {
        "target_traffic_percent": "1", "current_traffic_percent": "0",
        "traffic_enablement": "BLOCKED_PENDING_ALL_CONDITIONS",
        "feature_flag_default": "OFF", "manual_enablement_required": "YES",
        "automatic_rollout": "FORBIDDEN", "production_traffic_percent": "0",
        "production_activation": "NOT_AUTHORIZED", "authority_transfer": "FORBIDDEN",
        "approval_status": "PENDING_USER_REVIEW",
    }
    for key, value in expected.items():
        if decision.get(key) != value:
            raise AssertionError(f"decision mismatch {key}")
    cohort = values(EV / "SC6_COHORT_CONTRACT.tsv")
    if cohort.get("cohort_selection") != "STABLE_HASH_PERCENTAGE":
        raise AssertionError("cohort selection mismatch")
    if cohort.get("cohort_key") != "HASHED_NONPRODUCTION_TEST_SUBJECT_REF":
        raise AssertionError("cohort key mismatch")
    if cohort.get("raw_identity_cohort_key") != "FORBIDDEN":
        raise AssertionError("raw identity cohort key allowed")
    observation = values(EV / "SC6_OBSERVATION_CONTRACT.tsv")
    if observation.get("minimum_observation_duration_minutes") != "30":
        raise AssertionError("observation duration mismatch")
    if observation.get("minimum_shadow_execution_count") != "100":
        raise AssertionError("observation count mismatch")
    if observation.get("both_conditions_required") != "YES":
        raise AssertionError("observation AND mismatch")
    metric_rows = rows(EV / "SC6_METRIC_INVENTORY.tsv")
    names = [r["metric"] for r in metric_rows]
    if len(names) != 27 or names != REQUIRED_METRICS:
        raise AssertionError("27 metric inventory mismatch")
    forbidden_label = re.compile(r"(raw|user_id|subject_id|session_id|email|credential|endpoint)", re.I)
    if any(forbidden_label.search(r["allowed_labels"]) for r in metric_rows):
        raise AssertionError("forbidden metric label")
    thresholds = values(EV / "SC6_SAFETY_THRESHOLDS.tsv", "threshold", "maximum")
    expected_thresholds = {
        "timeout_rate_percent":"20", "exception_rate_percent":"25",
        "queue_rejection_rate_percent":"5", "late_discard_rate_percent":"5",
        "redaction_failure_count":"0", "response_mutation_count":"0",
        "database_write_count":"0", "event_emission_count":"0",
        "production_route_detection_count":"0", "authority_mismatch_count":"0",
    }
    if thresholds != expected_thresholds:
        raise AssertionError("safety thresholds mismatch")
    owners = values(EV / "SC6_OWNER_BOUNDARIES.tsv", "item", "wire_owner")
    for key, value in {
        "endpoint":"OPERATIONS", "credential":"OPERATIONS",
        "identity_allowlist":"PRIVACY_SECURITY", "alert":"OPERATIONS",
        "rollback_execution":"OPERATIONS",
    }.items():
        if owners.get(key) != value:
            raise AssertionError(f"owner mismatch {key}")
    approvals = rows(EV / "SC6_APPROVAL_MATRIX.tsv")
    if [r["role"] for r in approvals] != [
        "Intelligence","Reliability","Data","Operations","Privacy/Security","SystemCoordination"
    ]:
        raise AssertionError("approval roles mismatch")
    if any(r["approval"] != "BLOCKING_APPROVAL" or r["status"] != "PENDING_USER_REVIEW" for r in approvals):
        raise AssertionError("approval state fabricated")
    rb = rows(EV / "SC6_ROLLBACK_MATRIX.tsv")
    if [r["level"] for r in rb] != [f"LEVEL_{i}" for i in range(1,8)]:
        raise AssertionError("rollback level mismatch")
    if any(r["owner"] != "Operations" or int(r["maximum_execution_seconds"]) <= 0 for r in rb):
        raise AssertionError("rollback owner/time mismatch")
    blockers = rows(EV / "SC6_BLOCKER_INVENTORY.tsv")
    if len(blockers) < 16 or any(r["status"] != "BLOCKED" for r in blockers):
        raise AssertionError("blocker inventory incomplete")
    plan = rows(EV / "SC6_VERIFICATION_PLAN.tsv")
    if any(r["status"] not in ALLOWED_STATUS for r in plan):
        raise AssertionError("invalid verification status")
    prohibited = {
        "actual_traffic_1_percent","actual_endpoint","actual_credential","actual_allowlist",
        "runtime_observation","canary","load","production_route","production_identity",
        "production_traffic","candidate_serving","authority_transfer",
    }
    if any(r["check"] in prohibited and r["status"] == "PASS" for r in plan):
        raise AssertionError("unexecuted item marked PASS")
    return "machine-readable evidence, 27 metrics, thresholds, owners, approvals and rollback verified"


def changed_paths(head: str) -> list[str]:
    return [p for p in sh("git", "diff", "--name-only", f"{WORK_START}..{head}").splitlines() if p]


def check_governance_only_diff(head: str) -> str:
    changed = changed_paths(head)
    allowed_exact = {
        ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",
        "docs/platform/governance/SC-DECISION-REGISTER.md",
        "docs/platform/governance/SC-PLATFORM-REGISTRY.md",
        "docs/platform/governance/SC-RACI.md",
        "docs/platform/governance/SC-HANDOFF.md",
    }
    allowed_prefix = (
        "docs/platform/governance/sc-next-track/SC-6-",
        "docs/platform/governance/sc-next-track/57-",
        "docs/platform/governance/sc-next-track/58-",
        "docs/platform/governance/sc-next-track/59-",
        "docs/platform/governance/sc-next-track/60-",
        "docs/platform/governance/sc-next-track/61-",
        "docs/platform/governance/sc-next-track/62-",
        "docs/platform/governance/sc-next-track/63-",
        "docs/platform/governance/sc-next-track/64-",
        "docs/platform/governance/sc-next-track/65-",
        "docs/platform/governance/sc-next-track/66-",
        "docs/platform/governance/sc-next-track/67-",
        "docs/platform/governance/sc-next-track/68-",
        "docs/platform/governance/sc-next-track/69-",
        "docs/platform/governance/sc-next-track/70-",
        "docs/platform/governance/sc-next-track/71-",
        "docs/platform/governance/sc-next-track/72-",
        "docs/platform/governance/sc-next-track/73-",
        "docs/platform/governance/sc-next-track/74-",
        "docs/platform/governance/sc-next-track/75-",
        "verification/sc-next-track/rca2-nonzero-nonprod-entry/",
    )
    unexpected = [p for p in changed if p not in allowed_exact and not p.startswith(allowed_prefix)]
    if unexpected:
        raise AssertionError(f"scope violation {unexpected}")
    if not changed:
        raise AssertionError("empty SC-6 diff")
    for p in changed:
        if p.startswith(("jc-backend/src/main/", "database/", "jc-recommendation-core/")):
            raise AssertionError(f"forbidden path {p}")
    return f"governance-only diff verified: {len(changed)} files"


def check_historical_evidence_protection(head: str) -> str:
    changed = changed_paths(head)
    protected_prefix = (
        "verification/rca0/", "verification/rca1/", "verification/rca1b/",
        "verification/rca2/", "docs/platform/recommendation/rca2/",
    )
    hit = [p for p in changed if p.startswith(protected_prefix)]
    if hit:
        raise AssertionError(f"historical evidence changed {hit}")
    return "RCA-0/RCA-1/RCA-1B/RCA-2 historical evidence unchanged"


def check_sql_protection(head: str) -> str:
    for number in range(1, 53):
        matches = list(SQL.glob(f"{number:02d}_*.sql"))
        if len(matches) != 1:
            raise AssertionError(f"SQL {number:02d} missing or duplicate")
    if list(SQL.glob("5[3-9]_*.sql")) or list(SQL.glob("[6-9][0-9]_*.sql")):
        raise AssertionError("SQL 53+ exists")
    if any(p.startswith("database/") for p in changed_paths(head)):
        raise AssertionError("database path changed")
    return "canonical SQL 01..52 protected and SQL 53+ absent"


def check_rca2_production_authority_boundary(head: str) -> str:
    config = (ROOT / "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml").read_text(encoding="utf-8")
    for marker in (
        "flag: off", "traffic-percent: 0", "max-production-dark-read-percent: 0",
        "production-route-allowed: false", "db-change: NONE", "sql-allocation: NOT_REQUIRED",
    ):
        if marker not in config:
            raise AssertionError(f"runtime baseline marker missing {marker}")
    if re.search(r"https?://|jdbc:", config):
        raise AssertionError("concrete endpoint or DB route in isolated config")
    decision = values(EV / "SC6_TRAFFIC_STAGE_DECISION.tsv", "decision", "value")
    for key, value in {
        "current_traffic_percent":"0", "production_traffic_percent":"0",
        "feature_flag_default":"OFF", "primary_result_authority":"CURRENT_P1_P2_ONLY",
        "shadow_result_authority":"NONE", "shadow_result_serving":"FORBIDDEN",
        "production_activation":"NOT_AUTHORIZED", "authority_transfer":"FORBIDDEN",
    }.items():
        if decision.get(key) != value:
            raise AssertionError(f"authority boundary mismatch {key}")
    return "RCA-2 OFF/0/primary-only/no-serving/no-production/no-transfer boundary verified"


CHECKS = {
    "authoritative_baseline": check_authoritative_baseline,
    "document_completeness": check_document_completeness,
    "machine_readable_evidence": check_machine_readable_evidence,
    "governance_only_diff": check_governance_only_diff,
    "historical_evidence_protection": check_historical_evidence_protection,
    "sql_protection": check_sql_protection,
    "rca2_production_authority_boundary": check_rca2_production_authority_boundary,
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--expected-head")
    parser.add_argument("--only", choices=sorted(CHECKS))
    args = parser.parse_args()
    head = sh("git", "rev-parse", "HEAD")
    if args.expected_head and head != args.expected_head:
        print(json.dumps({"result":"FAIL","testedSha":head,"failures":[f"expected head {args.expected_head}"]}, indent=2))
        return 1
    selected = [args.only] if args.only else list(CHECKS)
    checks = []
    failures = []
    for name in selected:
        try:
            detail = CHECKS[name](head)
            checks.append({"check":name,"status":"PASS","detail":detail})
        except Exception as exc:
            checks.append({"check":name,"status":"FAIL","detail":str(exc)})
            failures.append(f"{name}: {exc}")
    if not args.only:
        for row in rows(EV / "SC6_VERIFICATION_PLAN.tsv"):
            if row["status"] in {"NOT_EXECUTED","NOT_APPLICABLE"}:
                checks.append({"check":row["check"],"status":row["status"],"detail":row["evidence"]})
    summary = {
        "contractId":"sc6-rca2-nonzero-nonproduction-stage1-authorization-v1",
        "workStartSha":WORK_START,
        "rca2ExactFinalHead":RCA2_HEAD,
        "rca2EvidenceArtifactId":ARTIFACT_ID,
        "rca2EvidenceDigest":ARTIFACT_DIGEST,
        "testedSha":head,
        "result":"PASS" if not failures else "FAIL",
        "checks":checks,
        "failures":failures,
    }
    OUT.mkdir(parents=True, exist_ok=True)
    if not args.only:
        (OUT / "SC6_VERIFICATION.json").write_text(json.dumps(summary, indent=2, sort_keys=True)+"\n", encoding="utf-8")
        with (OUT / "SC6_VERIFICATION.tsv").open("w", encoding="utf-8", newline="") as handle:
            writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
            writer.writerow(["check","status","detail","tested_sha"])
            for item in checks:
                writer.writerow([item["check"],item["status"],item["detail"],head])
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
