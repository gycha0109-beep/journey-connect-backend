#!/usr/bin/env python3
import argparse
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
OP2 = ROOT / "verification/operations/op2"
CONTRACTS = OP2 / "contracts"
DOCS = ROOT / "docs/platform/operations/op2"
EVIDENCE = OP2 / "op2-evidence.json"
EXPECTED_START = "f17fc3e515264eefcf2ca2b113a0e5875bbde6ae"
EXPECTED_OP1 = "6c89e78e32f54a1f830d0c84db07a01de951e39c"
EXPECTED_OP1_MERGE = "f17fc3e515264eefcf2ca2b113a0e5875bbde6ae"
BACKLOG = {
    "traffic_selected_count", "traffic_skipped_count", "executor_active_count",
    "executor_queue_depth", "shadow_task_age_ms", "shadow_cancelled_count", "checkpoint_lag_ms"
}
ARTIFACTS = {
    "op2-baseline", "op1-continuity", "metric-inventory", "metric-implementation-status",
    "dashboard-inventory", "dashboard-readiness", "critical-alert-inventory",
    "warning-alert-inventory", "alert-route-status", "kill-switch-status", "rollback-matrix",
    "rollback-drill-results", "approval-matrix", "manual-enablement-runbook", "op3-entry-gate",
    "external-dependency-register", "blocker-register", "risk-register"
}
DOC_NAMES = {
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
    "24-OP-3-EXECUTION-PROMPT.md"
}
METADATA = {
    "work_start_sha", "source_op1_exact_head", "source_op1_merge_commit", "artifact_version",
    "status", "owner", "updated_at"
}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def verify_baseline(data):
    require(data["work_start_sha"] == EXPECTED_START, "work-start SHA mismatch")
    require(data["source_op1_exact_head"] == EXPECTED_OP1, "OP-1 exact head mismatch")
    require(data["source_op1_merge_commit"] == EXPECTED_OP1_MERGE, "OP-1 merge mismatch")
    ancestor = subprocess.run(
        ["git", "merge-base", "--is-ancestor", EXPECTED_START, "HEAD"], cwd=ROOT, check=False
    ).returncode
    require(ancestor == 0, "work-start is not an ancestor")


def verify_inventory(data):
    require(set(data["required_documents"]) == DOC_NAMES, "required document inventory mismatch")
    require(set(data["artifacts"]) == ARTIFACTS, "artifact inventory mismatch")
    for name in DOC_NAMES:
        require((DOCS / name).is_file(), f"missing document: {name}")
    for name in ARTIFACTS:
        path = CONTRACTS / f"{name}.json"
        require(path.is_file(), f"missing artifact: {name}")
        artifact = load(path)
        require(METADATA <= artifact.keys(), f"metadata missing: {name}")
        require(artifact["work_start_sha"] == EXPECTED_START, f"work-start mismatch: {name}")
        require(artifact["source_op1_exact_head"] == EXPECTED_OP1, f"OP-1 head mismatch: {name}")
        require(artifact["source_op1_merge_commit"] == EXPECTED_OP1_MERGE, f"OP-1 merge mismatch: {name}")


def verify_metrics(data):
    inventory = load(CONTRACTS / "metric-inventory.json")
    implementation = load(CONTRACTS / "metric-implementation-status.json")
    require(data["metrics"]["authoritative_count"] == 27, "27 metric continuity missing")
    require(inventory["authoritative_count"] == 27, "authoritative inventory count mismatch")
    require(len(inventory["authoritative_metrics"]) == 27, "authoritative metric list mismatch")
    require(set(data["metrics"]["backlog"]) == BACKLOG, "seven backlog metrics mismatch")
    require({item["name"] for item in implementation["metrics"]} == BACKLOG, "implementation list mismatch")
    require(all(item["cardinality_limit"] <= 96 for item in implementation["metrics"]), "cardinality bound missing")
    require(implementation["labels_bounded"], "labels are not bounded")
    require(not implementation["raw_identity_label"], "raw identity label present")
    require(not implementation["token_label"], "token label present")
    require(not implementation["full_endpoint_url_label"], "full endpoint URL label present")

    metrics_source = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/Rca2Metrics.java").read_text()
    gate_source = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/Rca2EnvironmentAccessGate.java").read_text()
    orchestrator_source = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/Rca2RuntimeOrchestrator.java").read_text()
    executor_source = (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/Rca2BoundedExecutor.java").read_text()
    require('ALLOWED_LABELS = Set.of("environment", "lane", "result_class", "breaker_state")' in metrics_source,
            "bounded label source mismatch")
    for name in BACKLOG:
        require(f'"{name}"' in metrics_source, f"metric not registered: {name}")
    require('"traffic_selected_count"' in gate_source and '"traffic_skipped_count"' in gate_source,
            "traffic metrics not wired")
    for name in ("executor_active_count", "executor_queue_depth", "shadow_task_age_ms",
                 "shadow_cancelled_count", "checkpoint_lag_ms"):
        require(f'"{name}"' in orchestrator_source, f"runtime metric not wired: {name}")
    require("queueAgeMillis" in executor_source, "task-age source missing")


def verify_dashboard_alerts(data):
    dashboard = load(CONTRACTS / "dashboard-inventory.json")
    dashboard_status = load(CONTRACTS / "dashboard-readiness.json")
    critical = load(CONTRACTS / "critical-alert-inventory.json")
    warning = load(CONTRACTS / "warning-alert-inventory.json")
    route = load(CONTRACTS / "alert-route-status.json")
    require(dashboard["section_count"] == 22 and len(dashboard["sections"]) == 22,
            "dashboard inventory incomplete")
    require(dashboard_status["application_dashboard_contract_ready"], "application dashboard contract not ready")
    require(not dashboard_status["external_dashboard_ready"], "external dashboard readiness overstated")
    require(critical["zero_tolerance"] and critical["threshold"] == 0, "critical zero tolerance missing")
    require(len(critical["alerts"]) == 11 and len(warning["alerts"]) == 11, "alert inventory incomplete")
    require(not route["critical_alert_route_ready"], "critical route readiness overstated")
    require(warning["threshold_source"] == "SC-6 unchanged", "SC-6 threshold drift")


def verify_rollback_gate(data):
    rollback = load(CONTRACTS / "rollback-drill-results.json")
    matrix = load(CONTRACTS / "rollback-matrix.json")
    approval = load(CONTRACTS / "approval-matrix.json")
    runbook = load(CONTRACTS / "manual-enablement-runbook.json")
    gate = load(CONTRACTS / "op3-entry-gate.json")
    statuses = [item["status"] for item in rollback["results"]]
    require(statuses == ["PASS", "PASS", "PASS", "NOT_EXECUTED", "NOT_EXECUTED",
                         "BLOCKED_EXTERNAL_DEPENDENCY", "BLOCKED_EXTERNAL_DEPENDENCY"],
            "rollback drill status mismatch")
    require(len(matrix["levels"]) == 7, "rollback matrix incomplete")
    require(not rollback["unexecuted_reported_as_pass"], "unexecuted drill overstated")
    require(data["approval_status"] == "USER_APPROVED", "explicit user approval missing")
    require(approval["all_role_scope_approvals"], "role-scope approvals missing")
    require(not approval["independent_six_person_signatures"], "independent signatures fabricated")
    require(runbook["ready"] and runbook["approved"], "manual runbook approval missing")
    require(not runbook["execution_authorized"], "Stage 1 execution incorrectly authorised")
    require(gate["all_conditions_and"] and gate["op3_entry"] == "BLOCKED", "OP-3 gate incorrectly open")
    require(gate["current_nonproduction_traffic_percent"] == 0, "current traffic nonzero")
    require(gate["effective_nonproduction_traffic_percent"] == 0, "effective traffic nonzero")
    require(gate["production_traffic_percent"] == 0, "production traffic nonzero")
    require(len(gate["false_conditions"]) > 0, "external blockers missing")


def verify_protection(data):
    require(data["traffic"]["configured_nonproduction_percent"] == 0, "configured traffic nonzero")
    require(data["traffic"]["effective_nonproduction_percent"] == 0, "effective traffic nonzero")
    require(data["traffic"]["production_percent"] == 0, "production traffic nonzero")
    require(data["traffic"]["feature_flag_default"] == "OFF", "feature flag default changed")
    require(data["traffic"]["automatic_rollout"] == "FORBIDDEN", "automatic rollout permitted")
    require(data["authority"]["candidate_serving"] == "FORBIDDEN", "candidate serving permitted")
    require(data["authority"]["primary"] == "CURRENT_P1_P2_ONLY", "primary authority changed")
    require(data["authority"]["authority_transfer"] == "FORBIDDEN", "authority transfer permitted")
    require(data["op3_entry"] == "BLOCKED", "OP-3 incorrectly open")
    for key in ("primary_response_mutation", "database_write", "cache_write", "event_emission",
                "notification_emission", "ranking_feedback", "production_route", "production_identity"):
        require(data["invariants"][key] == "FORBIDDEN", f"invariant changed: {key}")
    require(data["invariants"]["production_activation"] == "NOT_AUTHORIZED", "production activation authorised")
    changed = git("diff", "--name-only", EXPECTED_START + "...HEAD").splitlines()
    require(not any(path.endswith(".sql") for path in changed), "SQL changed")
    require(not any(path.startswith("verification/rca2/") or path.startswith("verification/operations/op1/")
                    for path in changed), "historical evidence changed")
    require((DOCS / "23-OP-3-EXECUTION-HANDOFF.md").is_file(), "OP-3 handoff missing")
    require((DOCS / "24-OP-3-EXECUTION-PROMPT.md").is_file(), "OP-3 prompt missing")


def run(section):
    data = load(EVIDENCE)
    if section in ("all", "baseline"):
        verify_baseline(data)
    if section in ("all", "inventory"):
        verify_inventory(data)
    if section in ("all", "metrics"):
        verify_metrics(data)
    if section in ("all", "dashboard-alerts"):
        verify_dashboard_alerts(data)
    if section in ("all", "rollback-gate"):
        verify_rollback_gate(data)
    if section in ("all", "protection"):
        verify_protection(data)
    return {
        "result": "PASS",
        "section": section,
        "tested_sha": git("rev-parse", "HEAD"),
        "work_start_sha": EXPECTED_START,
        "source_op1_exact_head": EXPECTED_OP1,
        "approval_status": data["approval_status"],
        "op3_entry": data["op3_entry"],
        "effective_nonproduction_traffic_percent": data["traffic"]["effective_nonproduction_percent"],
        "production_traffic_percent": data["traffic"]["production_percent"],
        "external_blockers": data["op3_false_conditions"]
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--section", default="all",
                        choices=["all", "baseline", "inventory", "metrics", "dashboard-alerts",
                                 "rollback-gate", "protection"])
    parser.add_argument("--output")
    args = parser.parse_args()
    result = run(args.section)
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output:
        pathlib.Path(args.output).write_text(rendered, encoding="utf-8")
    print(rendered, end="")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"OP-2 verification failed: {exc}", file=sys.stderr)
        sys.exit(1)
