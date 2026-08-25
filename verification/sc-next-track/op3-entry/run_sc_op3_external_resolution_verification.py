#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
HISTORICAL_WORK_START = "fcd930550eb0f8b4c529ac53fb8f2aa9bce767a9"
GOVERNANCE_CORRECTION_BASE = "262ae7aea5bbe080b55091cabf9f6f220bac79d5"
CONTRACT = ROOT / "verification/sc-next-track/op3-entry"
DOC_ROOT = ROOT / "docs/platform/governance/sc-next-track"
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
    require(git("merge-base", HISTORICAL_WORK_START, head) == HISTORICAL_WORK_START, "historical work-start is not an ancestor")
    require(git("merge-base", GOVERNANCE_CORRECTION_BASE, head) == GOVERNANCE_CORRECTION_BASE, "PR #45 merge is not an ancestor")

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
    docs: dict[str, str] = {}
    for name in required_docs:
        path = DOC_ROOT / name
        require(path.is_file(), f"missing document {name}")
        docs[name] = path.read_text(encoding="utf-8")
        require(bool(docs[name].strip()), f"empty document {name}")

    work_orders = read_json("sc-op3-work-orders.json")
    acceptance = read_json("sc-op3-evidence-acceptance.json")
    entry_gate = read_json("sc-op3-entry-gate.json")
    inputs = read_json("sc-op3-required-input-decision-matrix.json")
    gcp_reference = read_json("sc-op3-gcp-dry-run-contract.json")
    cost = read_json("sc-op3-cost-teardown-contract.json")

    require(work_orders["programme_issue"] == 36, "programme issue mismatch")
    require([item["issue"] for item in work_orders["work_orders"]] == list(range(37, 44)), "work-order set mismatch")
    require(work_orders["op3_governance_consistent"] is True, "governance consistency missing")
    require(work_orders["current_resolution_result"] == "OP3_GOVERNANCE_CONSISTENT", "governance result mismatch")
    require(work_orders["external_resolution_state"] == "OP3_EXTERNAL_RESOLUTION_PARTIAL", "external resolution state changed")

    fixed = {
        "op3_entry": "BLOCKED",
        "stage1_enablement": "BLOCKED",
        "feature_flag": "OFF",
        "primary_result_authority": "CURRENT_P1_P2_ONLY",
        "candidate_serving": "FORBIDDEN",
        "automatic_rollout": "FORBIDDEN",
        "authority_transfer": "FORBIDDEN",
    }
    for field, value in fixed.items():
        require(work_orders[field] == value, f"safety field changed: {field}")
    require(work_orders["sc_op3_execution_approved"] is False, "execution approval changed")
    require(work_orders["effective_nonproduction_traffic_percent"] == 0, "non-production traffic changed")
    require(work_orders["production_traffic_percent"] == 0, "production traffic changed")

    governance = work_orders["deployment_platform_governance"]
    expected_governance = {
        "final_deployment_platform": "UNDECIDED",
        "deployment_implementation": "DEFERRED",
        "platform_architecture_reference": "GCP_CLOUD_RUN",
        "platform_architecture_reference_status": "DESIGN_ONLY",
        "gcp_architecture_status": "REFERENCE_ONLY",
        "expected_training_deployment_platform": "AWS",
        "aws_deployment_decision_status": "PENDING_CURRICULUM_CONFIRMATION",
        "cloud_provisioning_status": "DEFERRED_PLATFORM_UNDECIDED",
        "op3_cloud_provisioning": "DEFERRED_PLATFORM_UNDECIDED",
        "paid_cloud_usage": "FORBIDDEN",
        "independent_approver_status": "DEFERRED_UNTIL_EXECUTION",
    }
    for field, value in expected_governance.items():
        require(governance[field] == value, f"deployment governance mismatch: {field}")
    require(governance["deployment_platform_selection_required"] is True, "platform selection gate missing")
    require(governance["cloud_provisioning_required_now"] is False, "cloud provisioning incorrectly required")
    require(governance["personal_cloud_spend_allowed"] is False, "personal spend allowed")
    require(governance["cost_ceiling"] == 0, "cost ceiling is not zero")
    require(governance["billing_account_linkage_authorized"] is False, "billing linkage authorised")
    require(governance["independent_approver_required_now"] is False, "approver incorrectly required for governance-only")
    for field in (
        "gcp_resource_creation_authorized",
        "gcp_billing_spend_authorized",
        "gcp_iam_mutation_authorized",
        "aws_resource_creation_authorized",
        "aws_billing_spend_authorized",
        "aws_iam_mutation_authorized",
        "retention_lock_authorized",
    ):
        require(governance[field] is False, f"unauthorised platform action enabled: {field}")

    by_issue = {item["issue"]: item for item in work_orders["work_orders"]}
    require(by_issue[40]["state"] == "DEFERRED_PLATFORM_UNDECIDED", "#40 is not deferred")
    require(by_issue[40]["gcp_architecture_status"] == "REFERENCE_ONLY", "#40 GCP status mismatch")
    require(by_issue[40]["cloud_provisioning_required_now"] is False, "#40 provisioning incorrectly required")
    require(by_issue[41]["preparation_status"] == "APPROVED_NOT_CONNECTED", "#41 contract status changed")
    require(by_issue[41]["serving"] == "FORBIDDEN", "#41 serving changed")
    require(by_issue[43]["independent_approver_required_now"] is False, "#43 approver timing mismatch")
    require(by_issue[43]["retention_lock_status"] == "NOT_AUTHORIZED", "#43 retention lock changed")
    require(all(not item["acceptance_status"].startswith("ACCEPTED") for item in work_orders["work_orders"]), "work order falsely accepted")

    require(inputs["decision_status"] == "DEFERRED_PLATFORM_UNDECIDED", "input decision status mismatch")
    require(inputs["final_deployment_platform"] == "UNDECIDED", "input final platform selected")
    require(inputs["deployment_implementation"] == "DEFERRED", "input deployment not deferred")
    require(inputs["gcp_architecture_status"] == "REFERENCE_ONLY", "input GCP status mismatch")
    require(inputs["expected_training_deployment_platform"] == "AWS", "AWS candidate missing")
    require(inputs["aws_deployment_decision_status"] == "PENDING_CURRICULUM_CONFIRMATION", "AWS prematurely selected")
    require(inputs["cloud_provisioning_required_now"] is False, "inputs require cloud provisioning")
    require(inputs["cloud_provisioning_status"] == "DEFERRED_PLATFORM_UNDECIDED", "input provisioning status mismatch")
    require(inputs["required_now_inputs"] == [], "current required-input set must be empty")
    require(inputs["personal_cloud_spend_allowed"] is False, "input matrix allows personal spend")
    require(inputs["paid_cloud_usage"] == "FORBIDDEN", "input matrix allows paid usage")
    require(inputs["cost_ceiling"] == 0, "input cost ceiling changed")
    require(inputs["billing_account_linkage_authorized"] is False, "input billing linkage authorised")
    require(inputs["independent_approver_required_now"] is False, "input approver timing mismatch")
    require(all(value == "NOT_APPLICABLE_REFERENCE_ONLY" for value in inputs["gcp_reference_only_inputs"].values()), "GCP input is active")
    require(all(value == "DEFERRED_UNTIL_EXECUTION" for value in inputs["execution_deferred_inputs"].values()), "execution input not deferred")
    require(all(value == "PENDING_CURRICULUM_CONFIRMATION" for value in inputs["platform_selection_inputs"].values()), "curriculum input prematurely resolved")

    require(gcp_reference["status"] == "REFERENCE_ONLY_NO_PROVISIONING_TARGET", "GCP contract status mismatch")
    require(gcp_reference["execution_mode"] == "REFERENCE_ONLY", "GCP contract execution mode mismatch")
    require(gcp_reference["actual_execution"] == "FORBIDDEN", "GCP contract permits execution")
    require(gcp_reference["final_deployment_platform"] == "UNDECIDED", "GCP contract selects platform")
    require(gcp_reference["gcp_architecture_status"] == "REFERENCE_ONLY", "GCP contract not reference-only")
    require(gcp_reference["gcp_provisioning_planned"] is False, "GCP provisioning planned")
    require(gcp_reference["cloud_provisioning_required_now"] is False, "GCP provisioning required")
    for field in ("billing_change", "iam_mutation", "resource_creation", "endpoint_call"):
        require(gcp_reference[field] == "FORBIDDEN", f"GCP mutation allowed: {field}")
    require(gcp_reference["template_execution_status"] == "HARD_DISABLED_REFERENCE_ONLY", "GCP templates enabled")
    require(gcp_reference["retention_lock_status"] == "NOT_AUTHORIZED", "GCP retention lock authorised")
    require(all(value == "NOT_APPLICABLE_REFERENCE_ONLY" for value in gcp_reference["gcp_reference_identifiers"].values()), "GCP identifier activated")

    require(cost["status"] == "PAID_CLOUD_USAGE_FORBIDDEN", "cost status mismatch")
    require(cost["final_deployment_platform"] == "UNDECIDED", "cost contract selects platform")
    require(cost["cloud_provisioning_required_now"] is False, "cost contract requires provisioning")
    require(cost["personal_cloud_spend_allowed"] is False, "cost contract allows personal spend")
    require(cost["paid_cloud_usage"] == "FORBIDDEN", "cost contract allows paid usage")
    require(cost["cost_ceiling"] == 0, "cost contract ceiling changed")
    require(cost["billing_account_linkage_authorized"] is False, "cost contract allows billing linkage")
    require(cost["teardown"]["required_now"] is False, "teardown incorrectly required now")
    require(cost["evidence_store"]["retention_lock"] == "NOT_AUTHORIZED", "cost contract retention lock authorised")

    require(acceptance["op3_governance_consistent"] is True, "acceptance governance consistency missing")
    require(acceptance["final_deployment_platform"] == "UNDECIDED", "acceptance selects platform")
    require(acceptance["cloud_provisioning_required_now"] is False, "acceptance requires provisioning")
    require(acceptance["independent_approver_required_now"] is False, "acceptance approver timing mismatch")
    require(acceptance["reference_architecture_is_not_execution_target"] is True, "reference architecture treated as target")
    require(acceptance["authoritative_evidence_store"]["type"] == "DEFERRED_PLATFORM_UNDECIDED", "evidence store prematurely selected")
    require(acceptance["authoritative_evidence_store"]["retention_lock_status"] == "NOT_AUTHORIZED", "acceptance retention lock authorised")
    require(acceptance["current_acceptance_result"] == "NOT_READY", "execution evidence falsely ready")
    for field in ("cloud_resource_creation_authorized", "billing_spend_authorized", "iam_mutation_authorized", "retention_lock_authorized"):
        require(acceptance[field] is False, f"acceptance authorises mutation: {field}")

    require(entry_gate["status"] == "BLOCKED" and entry_gate["gate_pass"] is False, "entry gate changed")
    require(entry_gate["sc_op3_execution_approved"] is False, "entry execution approval changed")
    require(entry_gate["effective_nonproduction_traffic_percent"] == 0, "entry non-production traffic changed")
    require(entry_gate["production_traffic_percent"] == 0, "entry production traffic changed")
    require(entry_gate["primary_result_authority"] == "CURRENT_P1_P2_ONLY", "entry authority changed")
    require(entry_gate["shadow_result_serving"] == "FORBIDDEN", "entry candidate serving changed")

    architecture_doc = docs["61-SC-OP3-ARCHITECTURE-DECISION-AND-PROVISIONING-GATE.md"]
    for marker in (
        "FINAL_DEPLOYMENT_PLATFORM=UNDECIDED",
        "GCP_ARCHITECTURE_STATUS=REFERENCE_ONLY",
        "EXPECTED_TRAINING_DEPLOYMENT_PLATFORM=AWS",
        "AWS_DEPLOYMENT_DECISION_STATUS=PENDING_CURRICULUM_CONFIRMATION",
        "PERSONAL_CLOUD_SPEND_ALLOWED=NO",
        "PAID_CLOUD_USAGE=FORBIDDEN",
        "CLOUD_PROVISIONING_REQUIRED_NOW=NO",
        "INDEPENDENT_APPROVER_REQUIRED_NOW=NO",
    ):
        require(marker in architecture_doc, f"architecture marker missing: {marker}")

    gcp_doc = docs["61-SC-OP3-GCP-CLOUD-RUN-DRY-RUN-PROVISIONING-PACKAGE.md"].lower()
    for concept in ("immutable", "zero-traffic", "least-privilege", "observability", "audit", "rollback"):
        require(concept in gcp_doc, f"GCP reference concept missing: {concept}")

    corrected_docs = [
        "61-SC-OP3-ARCHITECTURE-DECISION-AND-PROVISIONING-GATE.md",
        "61-SC-OP3-GCP-CLOUD-RUN-DRY-RUN-PROVISIONING-PACKAGE.md",
        "61-SC-OP3-COST-AND-TEARDOWN-BOUNDARY.md",
        "61-SC-OP3-PARALLEL-EXTERNAL-PREPARATION.md",
        "61-SC-OP3-OPERATIONS-CONTROL-PLANE-PREPARATION.md",
    ]
    for name in corrected_docs:
        text = docs[name]
        require("PLATFORM_ARCHITECTURE_STATUS=SC_APPROVED_NOT_PROVISIONED" not in text, f"obsolete GCP target status remains in {name}")
        require("CLOUD_PROVISIONING_STATUS=BLOCKED_REQUIRED_INPUTS" not in text, f"obsolete provisioning status remains in {name}")

    allowed_current = {
        "docs/platform/governance/sc-next-track/61-SC-OP3-ARCHITECTURE-DECISION-AND-PROVISIONING-GATE.md",
        "docs/platform/governance/sc-next-track/61-SC-OP3-COST-AND-TEARDOWN-BOUNDARY.md",
        "docs/platform/governance/sc-next-track/61-SC-OP3-GCP-CLOUD-RUN-DRY-RUN-PROVISIONING-PACKAGE.md",
        "docs/platform/governance/sc-next-track/61-SC-OP3-PARALLEL-EXTERNAL-PREPARATION.md",
        "docs/platform/governance/sc-next-track/61-SC-OP3-OPERATIONS-CONTROL-PLANE-PREPARATION.md",
        "docs/platform/governance/sc-next-track/61-SC-OP3-EXTERNAL-DEPENDENCY-WORK-ORDERS.md",
        "docs/platform/governance/sc-next-track/61-SC-OP3-EVIDENCE-ACCEPTANCE-CONTRACT.md",
        "verification/sc-next-track/op3-entry/sc-op3-required-input-decision-matrix.json",
        "verification/sc-next-track/op3-entry/sc-op3-gcp-dry-run-contract.json",
        "verification/sc-next-track/op3-entry/sc-op3-cost-teardown-contract.json",
        "verification/sc-next-track/op3-entry/sc-op3-work-orders.json",
        "verification/sc-next-track/op3-entry/sc-op3-evidence-acceptance.json",
        "verification/sc-next-track/op3-entry/run_sc_op3_external_resolution_verification.py",
    }

    current_base = git("merge-base", "origin/main", head)
    current_changed = git("diff", "--name-only", f"{current_base}...{head}").splitlines()

    def external_resolution_sensitive(path: str) -> bool:
        if path in allowed_current:
            return True
        if path.startswith("docs/platform/governance/sc-next-track/61-SC-OP3-"):
            return True
        if path.startswith("verification/sc-next-track/op3-entry/sc-op3-") and path.endswith(".json"):
            return True
        if path == ".github/workflows/sc-op3-entry-governance-ci.yml":
            return True
        return False

    changed = [path for path in current_changed if external_resolution_sensitive(path)]
    require(set(changed).issubset(allowed_current), f"governance-only scope violation: {changed}")
    require(not any(path.startswith((".github/workflows/", "jc-backend/src/", "jc-recommendation-core/", "database/")) for path in changed), "runtime, workflow or database path changed")

    states = live_issue_states() if args.require_live_issues else {issue: "NOT_CHECKED" for issue in ISSUES}
    if args.require_live_issues:
        require(all(state == "open" for state in states.values()), f"work order unexpectedly closed: {states}")

    print(json.dumps({
        "result": "OP3_GOVERNANCE_CONSISTENT",
        "tested_sha": head,
        "programme_issue": 36,
        "live_issue_states": states,
        "final_deployment_platform": "UNDECIDED",
        "gcp_architecture_status": "REFERENCE_ONLY",
        "expected_training_deployment_platform": "AWS",
        "aws_deployment_decision_status": "PENDING_CURRICULUM_CONFIRMATION",
        "cloud_provisioning_required_now": False,
        "op3_cloud_provisioning": "DEFERRED_PLATFORM_UNDECIDED",
        "personal_cloud_spend_allowed": False,
        "paid_cloud_usage": "FORBIDDEN",
        "can_create_gcp_resources": False,
        "can_create_aws_resources": False,
        "can_link_billing_account": False,
        "can_mutate_cloud_iam": False,
        "can_serve_candidate_traffic": False,
        "op3_entry": "BLOCKED",
        "effective_nonproduction_traffic_percent": 0,
        "production_traffic_percent": 0,
        "changed_files": changed,
    }, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
