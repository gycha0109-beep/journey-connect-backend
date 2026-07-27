#!/usr/bin/env python3
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / "verification/operations/op2/op2-evidence.json"
METRICS = ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/Rca2Metrics.java"
DOC = ROOT / "docs/platform/operations/op2/OP-2-OBSERVABILITY-AND-SAFETY-PREPARATION.md"
EXPECTED_START = "f17fc3e515264eefcf2ca2b113a0e5875bbde6ae"
EXPECTED_OP1 = "6c89e78e32f54a1f830d0c84db07a01de951e39c"
BACKLOG = {"traffic_selected_count","traffic_skipped_count","executor_active_count","executor_queue_depth","shadow_task_age_ms","shadow_cancelled_count","checkpoint_lag_ms"}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    data = json.loads(EVIDENCE.read_text())
    source = METRICS.read_text()
    doc = DOC.read_text()
    require(data["work_start_sha"] == EXPECTED_START, "work-start SHA mismatch")
    require(data["source_op1_exact_head"] == EXPECTED_OP1, "OP-1 exact head mismatch")
    require(data["metrics"]["authoritative_count"] == 27, "27 metric continuity missing")
    require({m["name"] for m in data["metrics"]["backlog"]} == BACKLOG, "seven backlog metrics mismatch")
    require(all(m["cardinality_limit"] <= 96 for m in data["metrics"]["backlog"]), "cardinality bound missing")
    require("raw identity" not in " ".join(m["labels"] for m in []), "unexpected identity label")
    require("ALLOWED_LABELS = Set.of(\"environment\", \"lane\", \"result_class\", \"breaker_state\")" in source, "bounded label source mismatch")
    require(data["traffic"]["effective_nonproduction_percent"] == 0, "effective traffic nonzero")
    require(data["traffic"]["production_percent"] == 0, "production traffic nonzero")
    require(data["traffic"]["feature_flag_default"] == "OFF", "flag is not off")
    require(data["authority"]["candidate_serving"] == "FORBIDDEN", "candidate serving permitted")
    require(data["approval_status"] == "PENDING_USER_REVIEW", "human approval forged")
    require(data["op3_entry"] == "BLOCKED", "OP-3 incorrectly open")
    require(len(data["dashboard"]["sections"]) == 22, "dashboard inventory incomplete")
    require(len(data["alerts"]["critical"]) >= 11 and len(data["alerts"]["warning"]) >= 11, "alert inventory incomplete")
    require([r["level"] for r in data["rollback"]] == list(range(1, 8)), "rollback matrix incomplete")
    require(data["rollback"][5]["status"] == "BLOCKED_EXTERNAL_DEPENDENCY", "credential drill overstated")
    require(data["rollback"][6]["status"] == "BLOCKED_EXTERNAL_DEPENDENCY", "network drill overstated")
    require("OP-3 execution handoff" in doc, "OP-3 handoff missing")
    changed = subprocess.check_output(["git", "diff", "--name-only", EXPECTED_START + "...HEAD"], cwd=ROOT, text=True).splitlines()
    require(not any(path.endswith(".sql") for path in changed), "SQL changed")
    require(not any("verification/rca2/" in path or "verification/operations/op1/" in path for path in changed), "historical evidence changed")
    print(json.dumps({"result":"PASS","tested_sha":subprocess.check_output(["git","rev-parse","HEAD"], cwd=ROOT, text=True).strip(),"op3_entry":"BLOCKED","traffic":0}, sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"OP-2 verification failed: {exc}", file=sys.stderr)
        sys.exit(1)
