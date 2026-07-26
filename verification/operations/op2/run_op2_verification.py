#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
WORK_START = "f17fc3e515264eefcf2ca2b113a0e5875bbde6ae"
OP1_HEAD = "6c89e78e32f54a1f830d0c84db07a01de951e39c"
OP1_MERGE = WORK_START
CONTRACT_DIR = ROOT / "verification/operations/op2/contracts"
RUNTIME_DIR = ROOT / "verification/operations/op2/runtime"

REQUIRED_CONTRACTS = [
    "op2-baseline.json", "op1-continuity.json", "metric-inventory.json",
    "metric-implementation-status.json", "dashboard-inventory.json", "dashboard-readiness.json",
    "critical-alert-inventory.json", "warning-alert-inventory.json", "alert-route-status.json",
    "kill-switch-status.json", "rollback-matrix.json", "rollback-drill-results.json",
    "approval-matrix.json", "manual-enablement-runbook.json", "op3-entry-gate.json",
    "external-dependency-register.json", "blocker-register.json", "risk-register.json",
]
REQUIRED_DOCS = [
    "00-OP-2-ENTRY-VERIFICATION.md", "01-OP-1-CONTINUITY-VERIFICATION.md",
    "02-OBSERVABILITY-SCOPE.md", "03-AUTHORITATIVE-METRIC-CONTINUITY.md",
    "04-METRIC-IMPLEMENTATION-RESULT.md", "05-DASHBOARD-ARCHITECTURE.md",
    "06-DASHBOARD-READINESS.md", "07-CRITICAL-ALERT-INVENTORY.md",
    "08-WARNING-ALERT-INVENTORY.md", "09-ALERT-ROUTING-READINESS.md",
    "10-KILL-SWITCH-VERIFICATION.md", "11-CIRCUIT-BREAKER-CONTINUITY.md",
    "12-ROLLBACK-LEVEL-1-7-PLAN.md", "13-ROLLBACK-DRILL-RESULT.md",
    "14-CREDENTIAL-REVOKE-DRILL.md", "15-NETWORK-REVOKE-DRILL.md",
    "16-BLOCKING-APPROVAL-PACKAGE.md", "17-MANUAL-ENABLEMENT-RUNBOOK.md",
    "18-OP-3-ENTRY-GATE.md", "19-EVIDENCE-RETENTION.md", "20-BLOCKER-REGISTER.md",
    "21-RISK-REGISTER.md", "22-INCIDENT-ESCALATION.md", "23-OP-3-EXECUTION-HANDOFF.md",
    "24-OP-3-EXECUTION-PROMPT.md",
]
BACKLOG = {
    "traffic_selected_count", "traffic_skipped_count", "executor_active_count",
    "executor_queue_depth", "shadow_task_age_ms", "shadow_cancelled_count", "checkpoint_lag_ms",
}
CRITICAL = {
    "response_mutation_detected", "database_write_detected", "cache_write_detected",
    "event_emission_detected", "notification_emission_detected", "redaction_failure_detected",
    "production_route_detected", "production_identity_detected", "authority_mismatch_detected",
    "traffic_ceiling_violation", "credential_scope_violation",
}
WARNING = {
    "timeout_rate", "exception_rate", "queue_rejection_rate", "late_discard_rate", "task_age",
    "checkpoint_lag", "executor_saturation", "credential_refresh_failure", "allowlist_lookup_failure",
    "unexpected_p1_mismatch", "unexpected_p2_mismatch",
}


def sh(*args: str, check: bool = True) -> str:
    result = subprocess.run(args, cwd=ROOT, text=True, capture_output=True)
    if check and result.returncode != 0:
        raise AssertionError(f"command failed: {' '.join(args)}\n{result.stdout}\n{result.stderr}")
    return result.stdout.strip()


def load(name: str) -> dict:
    return json.loads((CONTRACT_DIR / name).read_text(encoding="utf-8"))


def common_metadata() -> None:
    for name in REQUIRED_CONTRACTS:
        path = CONTRACT_DIR / name
        assert path.is_file(), f"missing contract {name}"
        data = load(name)
        assert data["work_start_sha"] == WORK_START
        assert data["source_op1_exact_head"] == OP1_HEAD
        assert data["source_op1_merge_commit"] == OP1_MERGE
        assert data["artifact_version"] == "op2-rca2-stage1-observability-safety-v1"
        assert data["status"]
        assert data["owner"]
        assert data["updated_at"]


def authoritative_baseline(expected_head: str) -> None:
    assert sh("git", "rev-parse", "HEAD") == expected_head
    sh("git", "merge-base", "--is-ancestor", WORK_START, expected_head)
    sh("git", "cat-file", "-e", f"{OP1_HEAD}^{{commit}}")
    assert "Merge pull request #32" in sh("git", "show", "-s", "--format=%s", OP1_MERGE)


def op1_continuity() -> None:
    sh("git", "diff", "--quiet", OP1_HEAD, OP1_MERGE)
    data = load("op1-continuity.json")
    assert data["pr_32_merged"] is True
    assert data["merge_tree_equality_expected"] is True
    assert data["effective_traffic_percent"] == 0


def document_completeness() -> None:
    base = ROOT / "docs/platform/operations/op2"
    missing = [name for name in REQUIRED_DOCS if not (base / name).is_file()]
    assert not missing, f"missing documents: {missing}"
    assert (base / "25-OP-2-WORKLOG.md").is_file()
    assert (base / "23-OP-3-EXECUTION-HANDOFF.md").is_file()
    assert (base / "24-OP-3-EXECUTION-PROMPT.md").is_file()


def metric_continuity() -> None:
    inventory = load("metric-inventory.json")
    names = [item["name"] for item in inventory["metrics"]]
    assert len(names) == 27 and len(set(names)) == 27
    tsv = (ROOT / "verification/sc-next-track/rca2-nonzero-nonprod-entry/SC6_METRIC_INVENTORY.tsv").read_text(encoding="utf-8").splitlines()[1:]
    authoritative = [line.split("\t", 1)[0] for line in tsv if line.strip()]
    assert names == authoritative, "SC-6 metric order or meaning drift"
    assert inventory["status"] == "AUTHORITATIVE_27_CONTINUITY_PRESERVED"


def metric_instrumentation() -> None:
    status = load("metric-implementation-status.json")
    implemented = {item["name"] for item in status["metrics"] if item["status"] == "IMPLEMENTED"}
    assert implemented == BACKLOG
    source = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/Rca2Op2Telemetry.java").read_text(encoding="utf-8")
    for metric in BACKLOG:
        assert f'"{metric}"' in source


def metric_redaction() -> None:
    inventory = load("metric-inventory.json")
    forbidden = ("identity", "token", "endpoint", "error_message", "url")
    for item in inventory["metrics"]:
        labels = item["labels"]
        assert not any(any(word in label.lower() for word in forbidden) for label in labels)
    source = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/Rca2Op2Telemetry.java").read_text(encoding="utf-8")
    assert "NO_RAW_IDENTITY_TOKEN_FULL_ENDPOINT_OR_UNBOUNDED_ERROR" in source
    assert "[a-z0-9_]{1,48}" in source


def dashboard_contract() -> None:
    inventory = load("dashboard-inventory.json")
    assert len(inventory["sections"]) == 22
    dashboard = json.loads((ROOT / "ops/observability/rca2/op2/grafana-dashboard.json").read_text(encoding="utf-8"))
    assert len(dashboard["panels"]) == 22
    assert load("dashboard-readiness.json")["application_dashboard_contract_ready"] is True
    assert load("dashboard-readiness.json")["external_dashboard_ready"] is False


def critical_alerts() -> None:
    data = load("critical-alert-inventory.json")
    assert data["tolerance"] == 0
    assert {item["id"] for item in data["alerts"]} == CRITICAL
    assert data["route_ready"] is False


def warning_alerts() -> None:
    data = load("warning-alert-inventory.json")
    assert {item["id"] for item in data["alerts"]} == WARNING
    conditions = {item["id"]: item["condition"] for item in data["alerts"]}
    assert "20" in conditions["timeout_rate"]
    assert "25" in conditions["exception_rate"]
    assert "5" in conditions["queue_rejection_rate"]
    assert "5" in conditions["late_discard_rate"]


def kill_switch() -> None:
    data = load("kill-switch-status.json")
    assert data["feature_flag_default_off"] is True
    for key in ("lane_kill_switch", "global_shadow_disable", "candidate_invocation_blocked",
                "queued_task_cancellation", "late_result_discard", "in_flight_timeout",
                "fallback_keeps_primary", "primary_response_unchanged", "restart_safe_default"):
        assert data[key] == "PASS"
    assert data["automatic_restart_or_ramp"] == "FORBIDDEN"


def rollback_levels() -> None:
    matrix = load("rollback-matrix.json")["levels"]
    assert [item["level"] for item in matrix] == list(range(1, 8))
    allowed = {"PASS", "FAIL", "NOT_EXECUTED", "BLOCKED_EXTERNAL_DEPENDENCY", "NOT_APPLICABLE"}
    assert all(item["drill_status"] in allowed for item in matrix)
    assert matrix[4]["drill_status"] == "NOT_EXECUTED"
    assert matrix[5]["drill_status"] == "BLOCKED_EXTERNAL_DEPENDENCY"
    assert matrix[6]["drill_status"] == "BLOCKED_EXTERNAL_DEPENDENCY"


def approvals_and_runbook() -> None:
    approvals = load("approval-matrix.json")
    assert len(approvals["approvals"]) == 6
    assert all(item["signature_status"] == "PENDING_USER_REVIEW" for item in approvals["approvals"])
    runbook = load("manual-enablement-runbook.json")
    assert len(runbook["steps"]) == 16
    assert runbook["execution_allowed_through_step"] == 11
    assert runbook["manual_enablement_approved"] is False


def effective_zero_traffic() -> None:
    baseline = load("op2-baseline.json")
    assert baseline["current_nonproduction_traffic_percent"] == 0
    assert baseline["effective_nonproduction_traffic_percent"] == 0
    assert baseline["production_traffic_percent"] == 0
    assert baseline["feature_flag_default"] == "OFF"
    gate = load("op3-entry-gate.json")
    assert gate["all_conditions_operator"] == "AND"
    assert gate["op3_entry"] == "BLOCKED"
    assert not all(gate["conditions"].values())


def runtime_side_effect_protection() -> None:
    contracts = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/Rca2RuntimeContracts.java").read_text(encoding="utf-8")
    assert 'PRIMARY_RESULT_AUTHORITY = "CURRENT_P1_P2_ONLY"' in contracts
    assert 'SHADOW_RESULT_AUTHORITY = "NONE"' in contracts
    assert 'SHADOW_RESULT_SERVING = "FORBIDDEN"' in contracts
    assert 'PRODUCTION_ACTIVATION = "NOT_AUTHORIZED"' in contracts
    assert 'AUTHORITY_TRANSFER = "FORBIDDEN"' in contracts
    config = (ROOT / "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml").read_text(encoding="utf-8")
    assert "traffic-percent: 0" in config
    assert "effective-traffic-percent: 0" in config


def historical_evidence_protection() -> None:
    changed = sh("git", "diff", "--name-only", WORK_START, "HEAD").splitlines()
    protected_prefixes = (
        "docs/platform/recommendation/rca2/", "docs/platform/operations/op0/",
        "docs/platform/operations/op1/", "verification/rca2/", "verification/operations/op0/",
        "verification/operations/op1/contracts/", "verification/operations/op1/runtime/",
        "verification/sc-next-track/",
    )
    violations = [path for path in changed if path.startswith(protected_prefixes)]
    assert not violations, f"historical evidence changed: {violations}"
    wrapper = ROOT / "verification/operations/op1/run_op1_verification.py"
    source = wrapper.read_text(encoding="utf-8")
    assert "EXPECTED_SOURCE_BLOB" in source
    assert "successor-owned OP-2" in source


def sql_protection() -> None:
    changed = sh("git", "diff", "--name-only", WORK_START, "HEAD", "--", "*.sql").splitlines()
    assert not changed, f"SQL changed: {changed}"


def op3_gate() -> None:
    gate = load("op3-entry-gate.json")
    assert gate["op3_entry"] == "BLOCKED"
    assert gate["current_nonproduction_traffic_percent"] == 0
    blockers = load("blocker-register.json")["blockers"]
    assert blockers and "CRITICAL_ALERT_ROUTE_READY=NO" in blockers


def write_evidence(expected_head: str, checks: dict[str, str], failures: list[str]) -> None:
    RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256()
    evidence_files = sorted(CONTRACT_DIR.glob("*.json")) + sorted((ROOT / "ops/observability/rca2/op2").glob("*"))
    for path in evidence_files:
        digest.update(path.relative_to(ROOT).as_posix().encode())
        digest.update(path.read_bytes())
    payload = {
        "artifact_version": "op2-rca2-stage1-observability-safety-evidence-v1",
        "tested_sha": expected_head,
        "work_start_sha": WORK_START,
        "source_op1_exact_head": OP1_HEAD,
        "source_op1_merge_commit": OP1_MERGE,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "result": "PASS" if not failures else "FAIL",
        "checks": checks,
        "failures": failures,
        "contract_digest": f"sha256:{digest.hexdigest()}",
        "external_observability_ready": False,
        "critical_alert_route_ready": False,
        "rollback_external_drills_ready": False,
        "human_approvals": "PENDING_USER_REVIEW",
        "op3_entry": "BLOCKED",
        "current_nonproduction_traffic_percent": 0,
        "production_traffic_percent": 0,
    }
    (RUNTIME_DIR / "op2-verification-result.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


CHECKS = {
    "authoritative_baseline": lambda head: authoritative_baseline(head),
    "op1_continuity": lambda head: op1_continuity(),
    "document_completeness": lambda head: document_completeness(),
    "machine_readable_evidence": lambda head: common_metadata(),
    "metric_continuity": lambda head: metric_continuity(),
    "metric_instrumentation": lambda head: metric_instrumentation(),
    "metric_redaction": lambda head: metric_redaction(),
    "dashboard_contract": lambda head: dashboard_contract(),
    "critical_alerts": lambda head: critical_alerts(),
    "warning_alerts": lambda head: warning_alerts(),
    "kill_switch": lambda head: kill_switch(),
    "rollback_levels": lambda head: rollback_levels(),
    "approvals_runbook": lambda head: approvals_and_runbook(),
    "effective_zero_traffic": lambda head: effective_zero_traffic(),
    "runtime_side_effect_protection": lambda head: runtime_side_effect_protection(),
    "historical_evidence_protection": lambda head: historical_evidence_protection(),
    "sql_protection": lambda head: sql_protection(),
    "op3_gate": lambda head: op3_gate(),
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--expected-head", required=True)
    parser.add_argument("--only", choices=sorted(CHECKS))
    args = parser.parse_args()
    selected = {args.only: CHECKS[args.only]} if args.only else CHECKS
    results: dict[str, str] = {}
    failures: list[str] = []
    for name, check in selected.items():
        try:
            check(args.expected_head)
            results[name] = "PASS"
        except Exception as exc:
            results[name] = "FAIL"
            failures.append(f"{name}: {exc}")
    write_evidence(args.expected_head, results, failures)
    print(json.dumps({"RESULT": "PASS" if not failures else "FAIL", "TESTED_SHA": args.expected_head,
                      "CHECKS": results, "FAILURES": failures}, indent=2))
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
