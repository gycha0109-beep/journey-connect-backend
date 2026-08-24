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
COMMANDS = (
    ("python", "verification/dp5/run_dp5_static_verification.py"),
    ("python", "verification/dp6/run_dp6_allocation_verification.py"),
    ("python", "verification/dp6/run_dp6_static_verification.py"),
    ("python", "verification/dp7/run_dp7_allocation_verification.py"),
    ("python", "verification/dp7/run_dp7_static_verification.py"),
    ("python", "verification/data-platform-closure/run_data_platform_closure_verification.py"),
)
HISTORICAL_DATA_CLOSURE = Path(
    "verification/data-platform-closure/run_data_platform_closure_verification.py"
)
HISTORICAL_FETCH_BLOCK = '''subprocess.run(\n    ["git", "fetch", "origin", "main", "--depth=1"],\n    cwd=ROOT,\n    check=False,\n    stdout=subprocess.DEVNULL,\n    stderr=subprocess.DEVNULL,\n)\n'''
HISTORICAL_NAMESPACE_ANCHOR = "namespace = {\n"
HISTORICAL_DIFF_PATCH = '''fetch_block = \'\'\'subprocess.run(\\n    ["git", "fetch", "origin", "main", "--depth=1"],\\n    cwd=ROOT,\\n    check=False,\\n    stdout=subprocess.DEVNULL,\\n    stderr=subprocess.DEVNULL,\\n)\\n\'\'\'\nif fetch_block not in source or '\"origin/main...HEAD\"' not in source:\n    raise SystemExit("FAIL: authoritative Data closure historical diff anchor missing")\nsource = source.replace(fetch_block, "", 1)\nsource = source.replace('\"origin/main...HEAD\"', 'f\"{MAIN}...HEAD\"', 1)\n\n'''


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


def patch_historical_data_closure(worktree: Path) -> None:
    wrapper = worktree / HISTORICAL_DATA_CLOSURE
    source = wrapper.read_text(encoding="utf-8")
    if HISTORICAL_NAMESPACE_ANCHOR not in source:
        raise RuntimeError("historical Data closure namespace anchor missing")
    if HISTORICAL_FETCH_BLOCK in source:
        raise RuntimeError("historical Data closure wrapper unexpectedly owns fetch block")
    patched = source.replace(
        HISTORICAL_NAMESPACE_ANCHOR,
        HISTORICAL_DIFF_PATCH + HISTORICAL_NAMESPACE_ANCHOR,
        1,
    )
    wrapper.write_text(patched, encoding="utf-8")


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
        patch_historical_data_closure(worktree)
        for command in COMMANDS:
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
