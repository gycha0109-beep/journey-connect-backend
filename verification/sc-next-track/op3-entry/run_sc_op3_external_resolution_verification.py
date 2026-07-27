#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
WORK_START = "fcd930550eb0f8b4c529ac53fb8f2aa9bce767a9"
CONTRACT = ROOT / "verification/sc-next-track/op3-entry"
DOC_ROOT = ROOT / "docs/platform/governance/sc-next-track"
ISSUES = list(range(36, 44))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


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

    docs = [
        "61-SC-OP3-EXTERNAL-DEPENDENCY-WORK-ORDERS.md",
        "61-SC-OP3-EVIDENCE-ACCEPTANCE-CONTRACT.md",
        "61-SC-OP3-READINESS-REASSESSMENT.md",
    ]
    for name in docs:
        path = DOC_ROOT / name
        require(path.is_file() and path.read_text(encoding="utf-8").strip(), f"missing document {name}")

    work_orders = json.loads((CONTRACT / "sc-op3-work-orders.json").read_text(encoding="utf-8"))
    acceptance = json.loads((CONTRACT / "sc-op3-evidence-acceptance.json").read_text(encoding="utf-8"))
    entry_gate = json.loads((CONTRACT / "sc-op3-entry-gate.json").read_text(encoding="utf-8"))

    require(work_orders["authoritative_main"] == WORK_START, "work-order baseline mismatch")
    require(work_orders["programme_issue"] == 36, "programme issue mismatch")
    require([item["issue"] for item in work_orders["work_orders"]] == list(range(37, 44)), "work-order issue set mismatch")
    require(all(item["state"] != "COMPLETE" for item in work_orders["work_orders"]), "work order falsely complete")
    require(work_orders["op3_entry"] == "BLOCKED", "OP-3 entry must remain blocked")
    require(work_orders["stage1_enablement"] == "BLOCKED", "Stage 1 must remain blocked")
    require(work_orders["effective_nonproduction_traffic_percent"] == 0, "effective traffic changed")
    require(work_orders["production_traffic_percent"] == 0, "production traffic changed")

    require(acceptance["required_work_order_issues"] == list(range(37, 44)), "acceptance issue set mismatch")
    require(acceptance["all_work_orders_required"] is True, "AND gate weakened")
    require(acceptance["separate_sc_execution_approval_required"] is True, "separate SC approval missing")
    require(acceptance["current_acceptance_result"] == "NOT_READY", "acceptance result fabricated")

    require(entry_gate["status"] == "BLOCKED" and entry_gate["gate_pass"] is False, "entry gate changed")
    require(entry_gate["sc_op3_execution_approved"] is False, "execution approval granted")
    require(entry_gate["effective_nonproduction_traffic_percent"] == 0, "entry-gate traffic changed")
    require(entry_gate["production_traffic_percent"] == 0, "entry-gate production traffic changed")

    changed = git("diff", "--name-only", f"{WORK_START}...HEAD").splitlines()
    allowed = (
        "docs/platform/governance/sc-next-track/61-SC-OP3-",
        "verification/sc-next-track/op3-entry/sc-op3-work-orders.json",
        "verification/sc-next-track/op3-entry/sc-op3-evidence-acceptance.json",
        "verification/sc-next-track/op3-entry/run_sc_op3_external_resolution_verification.py",
        ".github/workflows/sc-op3-entry-governance-ci.yml",
    )
    require(all(any(path.startswith(prefix) for prefix in allowed) for path in changed), f"scope violation: {changed}")
    require(not any(path.startswith(("database/", "jc-backend/src/", "jc-recommendation-core/")) for path in changed), "protected path changed")

    states = live_issue_states() if args.require_live_issues else {issue: "NOT_CHECKED" for issue in ISSUES}
    if args.require_live_issues:
        require(all(state == "open" for state in states.values()), f"unexpected issue state: {states}")

    print(json.dumps({
        "result": "SC_OP3_EXTERNAL_WORK_ORDERS_REGISTERED",
        "tested_sha": head,
        "programme_issue": 36,
        "work_order_issues": list(range(37, 44)),
        "live_issue_states": states,
        "op3_entry": "BLOCKED",
        "stage1_enablement": "BLOCKED",
        "effective_nonproduction_traffic_percent": 0,
        "production_traffic_percent": 0,
    }, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
