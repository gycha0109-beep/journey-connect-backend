#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path


def need(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def find(root: Path, version: str, name: str) -> Path:
    matches = [path for path in root.rglob(name) if f"postgresql-{version}" in path.as_posix()]
    need(len(matches) == 1, f"expected one {name} for PostgreSQL {version}, found {matches}")
    return matches[0]


def tsv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def normalized_evidence(path: Path) -> list[dict[str, object]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    for row in data:
        row.pop("databaseVersion", None)
    return sorted(data, key=lambda row: (
        row["lane"], row["hashedCaseId"], row["comparisonDimension"], row["queryId"]))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    failures: list[str] = []
    differences: list[str] = []
    try:
        canonical15 = json.loads(find(args.root, "15", "RCA1B_CANONICAL_RESULT.json").read_text(encoding="utf-8"))
        canonical18 = json.loads(find(args.root, "18", "RCA1B_CANONICAL_RESULT.json").read_text(encoding="utf-8"))
        need(canonical15 == canonical18, "canonical result differs")
        for name in ("RCA1B_QUERY_INVENTORY.tsv", "RCA1B_VERIFICATION_COUNTERS.tsv", "RCA1B_PERMISSION_NEGATIVE_RESULTS.tsv"):
            left = tsv(find(args.root, "15", name))
            right = tsv(find(args.root, "18", name))
            need(left == right, f"{name} differs")
        evidence15 = normalized_evidence(find(args.root, "15", "RCA1B_RECONCILIATION_EVIDENCE.json"))
        evidence18 = normalized_evidence(find(args.root, "18", "RCA1B_RECONCILIATION_EVIDENCE.json"))
        need(evidence15 == evidence18, "normalized evidence differs")
    except Exception as exception:
        failures.append(str(exception))
        differences.append(str(exception))
    result = {
        "contractId": "rca1b-cross-version-equivalence-v1",
        "postgresqlVersionMatrix": ["15", "18"],
        "result": "PASS" if not failures else "FAIL",
        "differences": differences,
        "failures": failures,
    }
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "RCA1B_CROSS_VERSION_EQUIVALENCE.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    with (args.output / "RCA1B_CROSS_VERSION_EQUIVALENCE.tsv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(("postgresql_15", "postgresql_18", "result", "differences"))
        writer.writerow(("EXECUTED", "EXECUTED", result["result"], ";".join(differences)))
    print(json.dumps(result, indent=2, sort_keys=True))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
