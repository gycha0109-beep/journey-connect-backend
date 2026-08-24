#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CONTRACT = ROOT / "verification/admin/adm3/adm3-contract.json"
EXPECTED_SQL = {
    "database/journey-connect-db-v2.7/53_admin_control_plane_hardening.sql",
    "database/journey-connect-db-v2.7/54_admin_control_plane_hardening_smoke_test.sql",
}
ADM2_BASELINE_COMMANDS = (
    ("python", "verification/dp5/run_dp5_static_verification.py"),
    ("python", "verification/dp6/run_dp6_allocation_verification.py"),
    ("python", "verification/dp6/run_dp6_static_verification.py"),
    ("python", "verification/dp7/run_dp7_allocation_verification.py"),
    ("python", "verification/dp7/run_dp7_static_verification.py"),
)
DATA_CLOSURE_BASE_SHA = "c528f6fb0942389b70a348cb9aa672eb7819a392"
DATA_CLOSURE_HEAD_SHA = "478a15929db43b1b3d3fde4648a5027a36ee75da"
DATA_CLOSURE_VERIFIER = Path(
    "verification/data-platform-closure/run_data_platform_closure_verification.py"
)
DATA_CLOSURE_FETCH_BLOCK = '''subprocess.run(\n    ["git", "fetch", "origin", "main", "--depth=1"],\n    cwd=ROOT,\n    check=False,\n    stdout=subprocess.DEVNULL,\n    stderr=subprocess.DEVNULL,\n)\n'''


def contract() -> dict:
    if not CONTRACT.is_file():
        raise RuntimeError("ADM-3 contract is absent")
    data = json.loads(CONTRACT.read_text(encoding="utf-8"))
    if data.get("result", {}).get("ADM3_ADMIN_API_HARDENING_COMPLETE") != "YES":
        raise RuntimeError("ADM-3 hardening contract is not active")
    if set(data.get("migration", {}).get("sql_change", [])) != EXPECTED_SQL:
        raise RuntimeError("ADM-3 successor SQL contract mismatch")
    if data.get("migration", {}).get("existing_migration_modified") != "NO":
        raise RuntimeError("existing migration protection is not asserted")
    return data


def active() -> bool:
    try:
        contract()
        return all((ROOT / path).is_file() for path in EXPECTED_SQL)
    except (OSError, ValueError, RuntimeError):
        return False


def add_worktree(path: Path, commit: str) -> None:
    subprocess.run(
        ["git", "worktree", "add", "--detach", str(path), commit],
        cwd=ROOT,
        check=True,
    )


def remove_worktree(path: Path) -> None:
    subprocess.run(
        ["git", "worktree", "remove", "--force", str(path)],
        cwd=ROOT,
        check=False,
    )


def patch_data_closure_pr_diff(worktree: Path) -> None:
    verifier = worktree / DATA_CLOSURE_VERIFIER
    source = verifier.read_text(encoding="utf-8")
    if DATA_CLOSURE_FETCH_BLOCK not in source:
        raise RuntimeError("Data closure fetch anchor missing at exact PR head")
    if source.count('"origin/main...HEAD"') != 1:
        raise RuntimeError("Data closure diff anchor mismatch at exact PR head")
    source = source.replace(DATA_CLOSURE_FETCH_BLOCK, "", 1)
    source = source.replace(
        '"origin/main...HEAD"',
        f'"{DATA_CLOSURE_BASE_SHA}...HEAD"',
        1,
    )
    verifier.write_text(source, encoding="utf-8")


def run_data_closure_exact_pr(temp_root: Path) -> None:
    worktree = temp_root / "data-closure-pr21"
    try:
        add_worktree(worktree, DATA_CLOSURE_HEAD_SHA)
        patch_data_closure_pr_diff(worktree)
        subprocess.run(
            ("python", str(DATA_CLOSURE_VERIFIER)),
            cwd=worktree,
            check=True,
        )
    finally:
        remove_worktree(worktree)


def run() -> None:
    data = contract()
    adm2_baseline = data["work_start_sha"]
    temp_root = Path(tempfile.mkdtemp(prefix="adm3-data-baseline-"))
    adm2_worktree = temp_root / "adm2-baseline"
    try:
        add_worktree(adm2_worktree, adm2_baseline)
        for command in ADM2_BASELINE_COMMANDS:
            subprocess.run(command, cwd=adm2_worktree, check=True)
        run_data_closure_exact_pr(temp_root)
    finally:
        remove_worktree(adm2_worktree)
        shutil.rmtree(temp_root, ignore_errors=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check-active", action="store_true")
    parser.add_argument("--run", action="store_true")
    args = parser.parse_args()
    if args.check_active:
        return 0 if active() else 1
    if args.run:
        run()
        print("Historical Data verifiers at ADM-2 baseline and Data closure PR #21: PASS")
        return 0
    parser.error("choose --check-active or --run")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
