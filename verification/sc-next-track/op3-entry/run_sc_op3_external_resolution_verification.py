#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
WORK_START = "fcd930550eb0f8b4c529ac53fb8f2aa9bce767a9"
CONTRACT = ROOT / "verification/sc-next-track/op3-entry"
DOC_ROOT = ROOT / "docs/platform/governance/sc-next-track"
TEMPLATE_ROOT = CONTRACT / "templates"
ISSUES = list(range(36, 44))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def read_json(name: str) -> dict:
    return json.loads((CONTRACT / name).read_text(encoding="utf-8"))


def live_issue_states() -> dict[int, str]:
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    token = os.environ.get("GITHUB_TOKEN", "")
    require(bool(repository and token), "live issue verification requires repository and token")
    states: dict[int, str] = {}
    for issue in ISSUES:
        request = urllib.request.Request(
            f"https://api.github.com/repos/{repository}/issues/{issue}",
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        with urllib.request.urlopen(request, timeout=20) as response:
            payload = json.load(response)
        require("pull_request" not in payload, f"#{issue} is not an issue work order")
        states[issue] = payload["state"]
    return states


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-live-issues", action="store_true")
    args = parser.parse_args()

    head = git("rev-parse", "HEAD")
    require(git("merge-base", WORK_START, head) == WORK_START, "work-start is not an ancestor")

    required_docs = [
        "61-SC-OP3-EXTERNAL-DEPENDENCY-WORK-ORDERS.md",
        "61-SC-OP3-EVIDENCE-ACCEPTANCE-CONTRACT.md",
        "61-SC-OP3-READINESS-REASSESSMENT.md",
        "61-SC-OP3-OPERATIONS-CONTROL-PLANE-PREPARATION.md",
        "61-SC-OP3-PARALLEL-EXTERNAL-PREPARATION.md",
        "61-SC-OP3-ARCHITECTURE-DECISION-AND-PROVISIONING-GATE.md",
        "61-SC-OP3-GCP-CLOUD-RUN-DRY-RUN-PROVISIONING-PACKAGE.md",
        "61-SC-OP3-COST-AND-TEARDOWN-BOUNDARY.md",
    ]
    for name in required_docs:
        path = DOC_ROOT / name
        require(path.is_file() and path.read_text(encoding="utf-8").strip(), f"missing document {name}")

    work_orders = read_json("sc-op3-work-orders.json")
    acceptance = read_json("sc-op3-evidence-acceptance.json")
    entry_gate = read_json("sc-op3-entry-gate.json")
    inputs = read_json("sc-op3-required-input-decision-matrix.json")
    dry_run = read_json("sc-op3-gcp-dry-run-contract.json")
    cost = read_json("sc-op3-cost-teardown-contract.json")

    require(work_orders["authoritative_main"] == WORK_START, "work-order baseline mismatch")
    require(work_orders["programme_issue"] == 36, "programme issue mismatch")
    require([item["issue"] for item in work_orders["work_orders"]] == list(range(37, 44)), "work-order issue set mismatch")
    require(all(item["state"] != "COMPLETE" for item in work_orders["work_orders"]), "work order falsely complete")
    require(all(not item["acceptance_status"].startswith("ACCEPTED") for item in work_orders["work_orders"]), "work order falsely accepted")

    fixed = {
        "current_resolution_result": "OP3_EXTERNAL_RESOLUTION_PARTIAL",
        "op3_entry": "BLOCKED",
        "stage1_enablement": "BLOCKED",
        "feature_flag": "OFF",
        "primary_result_authority": "CURRENT_P1_P2_ONLY",
        "candidate_serving": "FORBIDDEN",
        "automatic_rollout": "FORBIDDEN",
        "authority_transfer": "FORBIDDEN",
    }
    for field, value in fixed.items():
        require(work_orders[field] == value, f"work-order field changed: {field}")
    require(work_orders["sc_op3_execution_approved"] is False, "execution approval must remain false")
    require(work_orders["effective_nonproduction_traffic_percent"] == 0, "effective traffic changed")
    require(work_orders["production_traffic_percent"] == 0, "production traffic changed")

    decision = work_orders["sc_architecture_decision"]
    expected_decision = {
        "platform_architecture": "GCP_CLOUD_RUN",
        "environment_class": "DEDICATED_GCP_CLOUD_RUN_NONPRODUCTION",
        "platform_architecture_status": "SC_APPROVED_NOT_PROVISIONED",
        "region_candidate": "asia-northeast3",
        "region_status": "PENDING_COST_AND_RESOURCE_OWNER",
        "region_final_approval": "PENDING_COST_OWNER",
        "candidate_contract_decision": "APPROVED_NOT_CONNECTED",
        "evidence_transport": "GITHUB_ACTIONS_ARTIFACTS_V4",
        "evidence_transport_status": "SC_APPROVED_INTERMEDIATE_ONLY",
        "authoritative_evidence_store": "GCP_CLOUD_STORAGE_RETENTION_POLICY_BUCKET",
        "evidence_store_status": "DESIGN_APPROVED_NOT_PROVISIONED",
        "retention_lock_status": "NOT_AUTHORIZED",
        "cloud_provisioning_status": "BLOCKED_REQUIRED_INPUTS",
    }
    for field, value in expected_decision.items():
        require(decision[field] == value, f"SC architecture decision mismatch: {field}")
    require(decision["architecture_target_approved"] is True, "architecture approval missing")
    for field in (
        "cloud_resource_creation_authorized",
        "billing_spend_authorized",
        "iam_mutation_authorized",
        "bucket_creation_authorized",
        "endpoint_call_authorized",
    ):
        require(decision[field] is False, f"{field} must remain false")

    by_issue = {item["issue"]: item for item in work_orders["work_orders"]}
    require(by_issue[40]["preparation_status"] == "SC_APPROVED_NOT_PROVISIONED", "#40 status mismatch")
    require(by_issue[40]["gcp_project_id"] == "UNASSIGNED", "project ID fabricated")
    require(by_issue[40]["service_id"] == "UNASSIGNED" and by_issue[40]["revision_id"] == "UNASSIGNED", "Cloud Run resource fabricated")
    require(by_issue[40]["region_candidate"] == "asia-northeast3", "#40 region mismatch")
    require(by_issue[40]["region_final_approval"] == "PENDING_COST_OWNER", "#40 final region prematurely approved")
    require(by_issue[41]["preparation_status"] == "APPROVED_NOT_CONNECTED", "#41 status mismatch")
    require(by_issue[41]["endpoint"] == "UNASSIGNED" and by_issue[41]["serving"] == "FORBIDDEN", "#41 endpoint or serving fabricated")
    require(by_issue[43]["evidence_transport_status"] == "SC_APPROVED_INTERMEDIATE_ONLY", "#43 transport made authoritative")
    require(by_issue[43]["authoritative_store_id"] == "UNASSIGNED", "evidence bucket fabricated")
    require(by_issue[43]["retention_lock_status"] == "NOT_AUTHORIZED", "retention lock authorised")
    for field in ("executing_actor", "reviewing_actor", "incident_commander_or_on_call"):
        require(by_issue[43][field] == "UNASSIGNED", f"#43 {field} fabricated")

    required_input_names = {
        "gcp_project_id", "billing_resource_owner", "region_final_approval",
        "cloud_platform_administrator", "workload_identity_administrator",
        "allowlist_store_owner", "observability_owner", "alert_receiver_owner",
        "manual_operator", "independent_approver", "incident_commander_or_on_call",
        "evidence_retention_period_days", "evidence_bucket_owner", "cost_ceiling",
        "teardown_deadline",
    }
    require(required_input_names.issubset(inputs["required_inputs"]), "required input field missing")
    require(inputs["decision_status"] == "BLOCKED_REQUIRED_INPUTS", "input matrix not blocked")
    require(inputs["execution_mode"] == "TEMPLATE_ONLY" and inputs["actual_execution"] == "FORBIDDEN", "input execution boundary changed")
    require(inputs["platform_architecture_status"] == "SC_APPROVED_NOT_PROVISIONED", "input architecture status mismatch")
    require(inputs["region_candidate"] == "asia-northeast3", "input region mismatch")
    for name, value in inputs["required_inputs"].items():
        expected = "PENDING_COST_OWNER" if name == "region_final_approval" else "UNASSIGNED"
        require(value == expected, f"required input {name} was fabricated or prematurely assigned")
    for field in ("cloud_resource_creation_authorized", "billing_spend_authorized", "iam_mutation_authorized", "retention_lock_authorized"):
        require(inputs[field] is False, f"input matrix {field} must remain false")

    require(dry_run["execution_mode"] == "TEMPLATE_ONLY" and dry_run["actual_execution"] == "FORBIDDEN", "dry-run execution allowed")
    require(dry_run["project_id"] == "REQUIRED_INPUT" and dry_run["region"] == "REQUIRED_INPUT", "dry-run required input weakened")
    for field in ("billing_change", "iam_mutation", "resource_creation", "endpoint_call"):
        require(dry_run[field] == "FORBIDDEN", f"dry-run mutation allowed: {field}")
    require(dry_run["cloud_run"]["traffic_percent"] == 0, "dry-run traffic changed")
    require(dry_run["cloud_run"]["candidate_serving"] == "FORBIDDEN", "dry-run candidate serving changed")
    require(dry_run["evidence_store"]["bucket_id"] == "UNASSIGNED", "dry-run bucket fabricated")
    require(dry_run["evidence_store"]["retention_lock_status"] == "NOT_AUTHORIZED", "dry-run retention lock authorised")

    require(cost["status"] == "BLOCKED_REQUIRED_INPUTS", "cost contract status mismatch")
    require(cost["billing_spend_authorized"] is False and cost["cloud_resource_creation_authorized"] is False, "cost contract authorises spend or creation")
    for field in ("billing_resource_owner", "cost_ceiling", "teardown_deadline"):
        require(cost[field] == "UNASSIGNED", f"cost field fabricated: {field}")
    require(cost["evidence_bucket_lifecycle"]["retention_lock"] == "NOT_AUTHORIZED", "cost retention lock authorised")

    require(acceptance["required_work_order_issues"] == list(range(37, 44)), "acceptance issue set mismatch")
    require(acceptance["all_work_orders_required"] is True, "AND gate weakened")
    require(acceptance["separate_sc_execution_approval_required"] is True, "separate SC approval missing")
    require(acceptance["current_acceptance_result"] == "NOT_READY", "acceptance result fabricated")
    require(acceptance["evidence_transport"]["authority"] == "INTERMEDIATE_ONLY", "artifact transport made authoritative")
    require(acceptance["authoritative_evidence_store"]["status"] == "DESIGN_APPROVED_NOT_PROVISIONED", "store falsely provisioned")
    require(acceptance["authoritative_evidence_store"]["bucket_id"] == "UNASSIGNED", "store ID fabricated")
    require(acceptance["authoritative_evidence_store"]["retention_lock_status"] == "NOT_AUTHORIZED", "store lock authorised")
    for field in ("cloud_resource_creation_authorized", "billing_spend_authorized", "iam_mutation_authorized", "retention_lock_authorized"):
        require(acceptance[field] is False, f"acceptance {field} must remain false")

    require(entry_gate["status"] == "BLOCKED" and entry_gate["gate_pass"] is False, "entry gate changed")
    require(entry_gate["sc_op3_execution_approved"] is False, "entry execution approval granted")
    require(entry_gate["effective_nonproduction_traffic_percent"] == 0, "entry traffic changed")
    require(entry_gate["production_traffic_percent"] == 0, "entry production traffic changed")
    require(entry_gate["primary_result_authority"] == "CURRENT_P1_P2_ONLY", "entry authority changed")
    require(entry_gate["shadow_result_serving"] == "FORBIDDEN", "entry serving changed")

    shell_path = TEMPLATE_ROOT / "op3-gcp-cloud-run-template-only.sh"
    workflow_path = TEMPLATE_ROOT / "op3-github-actions-oidc-template-only.yml.example"
    require(shell_path.is_file() and workflow_path.is_file(), "template file missing")
    shell = shell_path.read_text(encoding="utf-8")
    workflow = workflow_path.read_text(encoding="utf-8")
    for marker in (
        "EXECUTION_MODE=\"${EXECUTION_MODE:-TEMPLATE_ONLY}\"",
        "ACTUAL_EXECUTION=\"${ACTUAL_EXECUTION:-FORBIDDEN}\"",
        "OUTPUT_MODE=WOULD_RUN_ONLY",
        "NO_GCLOUD_COMMAND_WAS_INVOKED=YES",
        "RETENTION_LOCK_COMMAND_INTENTIONALLY_ABSENT=YES",
    ):
        require(marker in shell, f"shell safety marker missing: {marker}")
    require(not re.search(r"(?m)^\s*gcloud\s", shell), "shell directly executes gcloud")
    require(not re.search(r"(?m)^\s*(curl|gsutil|terraform)\s", shell), "shell directly executes mutation tool")
    require("eval " not in shell and "bash -c" not in shell, "shell contains dynamic execution")
    require("lock-retention" not in shell and "lockRetentionPolicy" not in shell, "retention lock command present")
    require("if: ${{ false }}" in workflow, "workflow example is not hard-disabled")
    require("PROJECT_ID: REQUIRED_INPUT" in workflow and "REGION: REQUIRED_INPUT" in workflow, "workflow required input missing")
    require("ACTUAL_EXECUTION: FORBIDDEN" in workflow, "workflow execution boundary missing")
    require("google-github-actions/auth@v3" in workflow and "access_token_lifetime: 900s" in workflow, "OIDC template incomplete")

    changed = git("diff", "--name-only", f"{WORK_START}...HEAD").splitlines()
    allowed = (
        "docs/platform/governance/sc-next-track/61-SC-OP3-",
        "verification/sc-next-track/op3-entry/",
        ".github/workflows/sc-op3-entry-governance-ci.yml",
    )
    require(all(any(path.startswith(prefix) for prefix in allowed) for path in changed), f"scope violation: {changed}")
    require(not any(path.startswith(("database/", "jc-backend/src/", "jc-recommendation-core/")) for path in changed), "protected path changed")
    require(not any(path.endswith(".sql") for path in changed), "SQL change forbidden")
    require(not any(path.startswith(".github/workflows/") and path != ".github/workflows/sc-op3-entry-governance-ci.yml" for path in changed), "new live workflow forbidden")

    tracked_text = "\n".join((CONTRACT / name).read_text(encoding="utf-8") for name in (
        "sc-op3-work-orders.json",
        "sc-op3-required-input-decision-matrix.json",
        "sc-op3-gcp-dry-run-contract.json",
        "sc-op3-cost-teardown-contract.json",
    ))
    require("bygrczggxfuisupcevaz" not in tracked_text and "yjdubukqkcvkymabskzd" not in tracked_text, "existing Supabase resource reused")
    require(not re.search(r"\bprj_[A-Za-z0-9]{10,}\b", tracked_text), "actual-looking Vercel project ID recorded")
    require(not re.search(r"\b[a-z][a-z0-9-]{5,28}\.run\.app\b", tracked_text), "actual-looking Cloud Run endpoint recorded")
    require(not re.search(r"\b\d{6,}-compute@developer\.gserviceaccount\.com\b", tracked_text), "actual-looking service account recorded")

    states = live_issue_states() if args.require_live_issues else {issue: "NOT_CHECKED" for issue in ISSUES}
    if args.require_live_issues:
        require(all(state == "open" for state in states.values()), f"unexpected issue state: {states}")

    print(json.dumps({
        "result": "SC_OP3_ARCHITECTURE_APPROVED_NOT_PROVISIONED_VERIFIED",
        "tested_sha": head,
        "programme_issue": 36,
        "work_order_issues": list(range(37, 44)),
        "live_issue_states": states,
        "platform_architecture_status": "SC_APPROVED_NOT_PROVISIONED",
        "cloud_provisioning_status": "BLOCKED_REQUIRED_INPUTS",
        "actual_execution": "FORBIDDEN",
        "cloud_resource_creation_authorized": False,
        "billing_spend_authorized": False,
        "iam_mutation_authorized": False,
        "retention_lock_authorized": False,
        "op3_entry": "BLOCKED",
        "stage1_enablement": "BLOCKED",
        "feature_flag": "OFF",
        "effective_nonproduction_traffic_percent": 0,
        "production_traffic_percent": 0,
        "primary_result_authority": "CURRENT_P1_P2_ONLY",
        "candidate_serving": "FORBIDDEN",
    }, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
