#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CONTRACT = ROOT / "verification/admin/adm3/adm3-contract.json"
DATA_CLOSURE_MAIN = "c528f6fb0942389b70a348cb9aa672eb7819a392"
DATA_CLOSURE_WRAPPER = Path("verification/data-platform-closure/run_data_platform_closure_verification.py")
EXPECTED_SQL = {
    "database/journey-connect-db-v2.7/53_admin_control_plane_hardening.sql",
    "database/journey-connect-db-v2.7/54_admin_control_plane_hardening_smoke_test.sql",
}
COMMANDS = (
    ("python", "verification/dp5/run_dp5_static_verification.py"),
    ("python", "verification/dp6/run_dp6_allocation_verification.py"),
    ("python", "verification/dp6/run_dp6_static_verification.py"),
    ("python", "verification/dp7/run_dp7_allocation_verification.py"),
    ("python", "verification/dp7/run_dp7_static_verification.py"),
    ("python", str(DATA_CLOSURE_WRAPPER)),
)


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


def run_data_closure_verifier(command: tuple[str, ...], worktree: Path) -> None:
    current_wrapper = ROOT / DATA_CLOSURE_WRAPPER
    historical_wrapper = worktree / DATA_CLOSURE_WRAPPER
    if not current_wrapper.is_file() or not historical_wrapper.is_file():
        raise RuntimeError("Data closure compatibility wrapper is absent")
    shutil.copyfile(current_wrapper, historical_wrapper)
    env = os.environ.copy()
    env["JC_DATA_CLOSURE_DIFF_BASE"] = DATA_CLOSURE_MAIN
    subprocess.run(command, cwd=worktree, check=True, env=env)


def run() -> None:
    data = contract()
    baseline = data["work_start_sha"]
    temp_root = Path(tempfile.mkdtemp(prefix="adm3-data-baseline-"))
    worktree = temp_root / "repo"
    try:
        subprocess.run(
            ["git", "worktree", "add", "--detach", str(worktree), baseline],
            cwd=ROOT,
            check=True,
        )
        for command in COMMANDS:
            if command[-1] == str(DATA_CLOSURE_WRAPPER):
                run_data_closure_verifier(command, worktree)
            else:
                subprocess.run(command, cwd=worktree, check=True)
    finally:
        subprocess.run(
            ["git", "worktree", "remove", "--force", str(worktree)],
            cwd=ROOT,
            check=False,
        )
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
        print("Historical Data verifiers at ADM-2 baseline: PASS")
        return 0
    parser.error("choose --check-active or --run")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
