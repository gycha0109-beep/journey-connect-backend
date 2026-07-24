#!/usr/bin/env python3
from __future__ import annotations

import csv
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
EV = ROOT / "verification/sc-next-track/rca1-entry"
OUT = EV / "runtime"
SQL = ROOT / "database/journey-connect-db-v2.7"
SC3_BASE_MAIN = "f802a105e46a62718616acaa7a3db6c172e7ed10"
SC3_MERGE_MAIN = "5a0ca52c8226a0f4a6e21f9af96c7da0732c8d5b"
RCA0 = "d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d"
SC3_DOCS = [
    "docs/platform/governance/sc-next-track/SC-3-RCA-1-ENTRY-AUTHORIZATION-AND-BASELINE-ALLOCATION.md",
    *[f"docs/platform/governance/sc-next-track/{number:02d}-" for number in range(13, 23)],
]


def sh(*command: str, check: bool = True) -> str:
    return subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, check=check).stdout.strip()


def need(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        data = list(reader)
    need(bool(reader.fieldnames) and bool(data), f"invalid TSV: {path.name}")
    return data


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    checks: list[dict[str, str]] = []
    failures: list[str] = []
    head = "UNKNOWN"

    def record(name: str, function, command: str) -> None:
        try:
            checks.append({"check": name, "status": "PASS", "command": command, "detail": str(function() or "verified")})
        except Exception as exc:
            failures.append(f"{name}: {exc}")
            checks.append({"check": name, "status": "FAIL", "command": command, "detail": str(exc)})

    try:
        head = sh("git", "rev-parse", "HEAD")
        shallow = ROOT / ".git/shallow"
        sh("git", "fetch", "--unshallow" if shallow.exists() else "origin", check=False)

        def baseline() -> str:
            need("Merge pull request #23" in sh("git", "show", "-s", "--format=%B", SC3_BASE_MAIN), "PR #23 merge absent")
            sh("git", "merge-base", "--is-ancestor", RCA0, SC3_BASE_MAIN)
            sh("git", "diff", "--quiet", RCA0, SC3_BASE_MAIN)
            sh("git", "merge-base", "--is-ancestor", SC3_BASE_MAIN, SC3_MERGE_MAIN)
            sh("git", "merge-base", "--is-ancestor", SC3_MERGE_MAIN, head)
            return "SC-3 base, merge and RCA-0 tree continuity verified"
        record("sc3_baseline_continuity", baseline, "git ancestry/tree checks")

        def documents() -> str:
            base = ROOT / "docs/platform/governance/sc-next-track"
            master = base / "SC-3-RCA-1-ENTRY-AUTHORIZATION-AND-BASELINE-ALLOCATION.md"
            need(master.is_file() and SC3_BASE_MAIN in master.read_text(encoding="utf-8"), "SC-3 master missing")
            for number in range(13, 23):
                matches = list(base.glob(f"{number:02d}-*.md"))
                need(len(matches) == 1 and matches[0].read_text(encoding="utf-8").strip(), f"SC-3 document {number:02d} missing")
            inventory = rows(EV / "SC_RCA1_ENTRY_DOCUMENTS.tsv")
            need(len(inventory) == 11, "SC-3 document inventory mismatch")
            return "SC-3 master, 10 decisions/handoff and inventory preserved"
        record("sc3_documents_preserved", documents, "SC-3 document inventory")

        def evidence() -> str:
            required = {
                "SC_RCA1_COUNTERS.tsv", "SC_RCA1_ENTRY_DECISIONS.tsv", "SC_RCA1_ENTRY_DOCUMENTS.tsv",
                "SC_RCA1_ENTRY_STATUS.tsv", "SC_RCA1_IDENTITY_GOVERNANCE.tsv",
                "SC_RCA1_P1_DIMENSIONS.tsv", "SC_RCA1_P2_DIMENSIONS.tsv",
                "SC_RCA1_PROTECTED_STATE.tsv", "SC_RCA1_RESULT_TAXONOMY.tsv",
                "SC_RCA1_VERIFICATION_STATUS.tsv",
            }
            for name in required:
                rows(EV / name)
            decisions = {row["decision"]: row["value"] for row in rows(EV / "SC_RCA1_ENTRY_DECISIONS.tsv")}
            need(decisions.get("entry") == "RCA1_ENTRY_AUTHORIZED", "SC-3 entry decision changed")
            need(decisions.get("execution_model") == "MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION", "Model A changed")
            need(decisions.get("identity_mode") == "SYNTHETIC_ONLY", "SC-3 identity changed")
            return "SC-3 machine-readable evidence preserved"
        record("sc3_evidence_preserved", evidence, "SC-3 TSV verification")

        def rca0_assets() -> str:
            handoff = (ROOT / "docs/platform/recommendation/RCA-0-HANDOFF.md").read_text(encoding="utf-8")
            need("RCA0_CONTRACT_AND_FIXTURE_COMPLETE" in handoff, "RCA-0 marker missing")
            java = "\n".join(path.read_text(encoding="utf-8") for path in (ROOT / "jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption").glob("*.java"))
            for contract in ("recommendation-data-consumer-alignment-v1", "recommendation-profile-input-consumer-v1", "experiment-outcome-input-consumer-v1", "recommendation-data-consumer-fixture-v1"):
                need(contract in java, f"RCA-0 contract missing: {contract}")
            with (ROOT / "jc-backend/src/test/resources/recommendation-data-adoption/p1-fixtures-v1.tsv").open(encoding="utf-8", newline="") as handle:
                p1 = list(csv.DictReader(handle, delimiter="\t"))
            with (ROOT / "jc-backend/src/test/resources/recommendation-data-adoption/p2-fixtures-v1.tsv").open(encoding="utf-8", newline="") as handle:
                p2 = list(csv.DictReader(handle, delimiter="\t"))
            need(len(p1) == 12 and len(p2) == 21, "RCA-0 fixture inventory changed")
            return "RCA-0 contracts and 12/21 fixtures preserved"
        record("rca0_assets_preserved", rca0_assets, "RCA-0 asset inventory")

        def protected() -> str:
            numbers = [int(match.group(1)) for path in SQL.glob("*.sql") if (match := re.match(r"(\d+)_", path.name))]
            need(set(numbers) == set(range(1, 53)) and len(numbers) == 52, "SQL inventory mismatch")
            return "SQL 01..52 exact and 53+ absent"
        record("sql_protection", protected, "SQL inventory")

    except Exception as exc:
        failures.append(f"verifier_internal: {exc}")

    for name in ("postgresql", "shadow_comparison", "runtime", "canary", "load", "replay", "production"):
        checks.append({"check": name, "status": "NOT_EXECUTED", "command": "NOT_EXECUTED", "detail": "historical SC-3 governance scope"})

    summary = {
        "contractId": "sc-3-rca1-entry-authorization-v1",
        "authoritativeMain": SC3_BASE_MAIN,
        "rca0ExactFinalHead": RCA0,
        "testedSha": head,
        "result": "PASS" if not failures else "FAIL",
        "checks": checks,
        "failures": failures,
    }
    (OUT / "SC_RCA1_ENTRY_VERIFICATION_SUMMARY.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    with (OUT / "SC_RCA1_ENTRY_VERIFICATION_SUMMARY.tsv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(["check", "status", "command", "detail", "tested_sha"])
        for check in checks:
            writer.writerow([check["check"], check["status"], check["command"], check["detail"], head])
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
