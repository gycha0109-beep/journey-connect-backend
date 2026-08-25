#!/usr/bin/env python3
from __future__ import annotations
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
WORK_START = "83da5e5a075cdde2e5fd7c3e81d81f77d8c987f8"
OP2_HEAD = "79009cf047fe67775b972d43a5f3f72aa8351908"
DOC_ROOT = ROOT / "docs/platform/governance/sc-next-track"
CONTRACT_ROOT = ROOT / "verification/sc-next-track/op3-entry"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


head = git("rev-parse", "HEAD")
require(git("merge-base", WORK_START, head) == WORK_START, "SC work-start is not an ancestor")
require(git("cat-file", "-t", OP2_HEAD) == "commit", "OP-2 exact head absent")
require(git("rev-parse", f"{WORK_START}^{{tree}}") == git("rev-parse", f"{OP2_HEAD}^{{tree}}"), "OP-2 merge tree differs from exact head")

required_docs = [
    "57-SC-OP2-MERGE-STATE-AND-OP3-CONTROL.md",
    "58-SC-OP3-ENTRY-AND-GATE.md",
    "59-SC-EXTERNAL-BLOCKER-OWNERSHIP-AND-COMPLETION.md",
    "60-SC-OP3-EXECUTION-HANDOFF.md",
    "61-SC-OP3-IMPLEMENTATION-PROMPT.md",
]
for name in required_docs:
    path = DOC_ROOT / name
    require(path.is_file() and path.read_text(encoding="utf-8").strip(), f"missing document {name}")

entry = json.loads((CONTRACT_ROOT / "sc-op3-entry-gate.json").read_text(encoding="utf-8"))
blockers = json.loads((CONTRACT_ROOT / "external-blocker-register.json").read_text(encoding="utf-8"))
require(entry["work_start_sha"] == WORK_START, "work-start mismatch")
require(entry["source_op2_exact_head"] == OP2_HEAD, "OP-2 head mismatch")
require(entry["gate_pass"] is False and entry["status"] == "BLOCKED", "OP-3 gate must remain blocked")
require(entry["effective_nonproduction_traffic_percent"] == 0, "effective traffic must remain zero")
require(entry["production_traffic_percent"] == 0, "production traffic must remain zero")
require(entry["primary_result_authority"] == "CURRENT_P1_P2_ONLY", "primary authority changed")
require(entry["shadow_result_serving"] == "FORBIDDEN", "shadow serving must remain forbidden")
require(entry["automatic_rollout"] == "FORBIDDEN", "automatic rollout must remain forbidden")
require(entry["sc_op3_execution_approved"] is False, "execution approval must not be granted")
require(blockers["status"] == "OPEN" and len(blockers["blockers"]) >= 13, "blocker register incomplete")
require(all(item["state"] != "COMPLETE" for item in blockers["blockers"]), "external blocker falsely completed")

# OP-3 is a closed entry gate. Later unrelated product/platform work must not be
# interpreted as part of its historical governance-only delta. Evaluate only
# the current main-relative delta that can actually affect OP-3 authority.
current_base = git("merge-base", "origin/main", head)
current_changed = git("diff", "--name-only", f"{current_base}...{head}").splitlines()

allowed = (
    "docs/platform/governance/sc-next-track/57-",
    "docs/platform/governance/sc-next-track/58-",
    "docs/platform/governance/sc-next-track/59-",
    "docs/platform/governance/sc-next-track/60-",
    "docs/platform/governance/sc-next-track/61-",
    "verification/sc-next-track/op3-entry/",
    ".github/workflows/sc-op3-entry-governance-ci.yml",
    ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",
    ".github/workflows/sc-baseline-reconciliation.yml",
    ".github/workflows/data-platform-closure-ci.yml",
)


def op3_sensitive(path: str) -> bool:
    if any(path.startswith(prefix) for prefix in allowed):
        return True
    if path.startswith((
        "verification/sc-next-track/rca2-nonzero-nonprod-entry/",
        "verification/operations/op3/",
        "docs/platform/operations/op3/",
        ".github/actions/rca2-job/",
        "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/",
    )):
        return True
    if path in (
        "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java",
        "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java",
        "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java",
    ):
        return True
    return path.startswith("jc-backend/src/main/resources/application-rca2")


changed = [path for path in current_changed if op3_sensitive(path)]
require(all(any(path.startswith(prefix) for prefix in allowed) for path in changed), "scope violation")
require(not any(path.endswith(".sql") or path.startswith("database/") for path in changed), "SQL change forbidden")
require(not any(path.startswith("jc-backend/src/") for path in changed), "runtime source change forbidden")
print(json.dumps({
    "result": "SC_OP3_ENTRY_GOVERNANCE_PACKAGE_READY",
    "tested_sha": head,
    "op3_entry": "BLOCKED",
    "effective_nonproduction_traffic_percent": 0,
    "production_traffic_percent": 0,
    "changed_files": changed,
}, indent=2))
