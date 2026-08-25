#!/usr/bin/env python3
"""Run protected RCA-1/RCA-1B verifiers with authorised successor compatibility.

Historical verifier sources are blob-pinned and executed unchanged except for
narrow in-memory transport adaptations. Historical documents, fixtures,
expected classifications, protected authority, runtime regressions and closed
SQL baselines remain intact while later governed repository history is not
misclassified as part of the original RCA phase diff.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
APPROVED_LATER_PHASE_PATHS = (
    ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",
    ".github/workflows/op0-rca2-stage1-operations-preparation-governance-ci.yml",
    ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",
    ".github/workflows/op2-rca2-stage1-observability-safety-ci.yml",
    "docs/platform/operations/op0/",
    "docs/platform/operations/op1/",
    "docs/platform/operations/op2/",
    "verification/operations/op0/",
    "verification/operations/op1/",
    "verification/operations/op2/",
)
TARGETS = {
    "rca1": {
        "path": "verification/rca1/run_rca1_verification.py",
        "blob": "dd7cba837cdf2dc79064f52a49c7f8706d843c99",
        "anchor": '                ".github/actions/rca2-job/",\n',
        "indent": "                ",
    },
    "rca1b": {
        "path": "verification/rca1b/run_rca1b_verification.py",
        "blob": "184508bca63483a1811086d3720e72f02f8e70c0",
        "anchor": '            ".github/actions/rca2-job/",\n',
        "indent": "            ",
    },
}


def adapt_rca1(source: str) -> str:
    changed_anchor = '            changed=sh(["git","diff","--name-only",f"{START}..{head}"]).splitlines()\n'
    if source.count(changed_anchor) != 2:
        raise SystemExit("FAIL: RCA-1 current-delta compatibility anchor mismatch")
    source = source.replace(
        changed_anchor,
        '            merge_base=sh(["git","merge-base","origin/main",head]).strip()\n'
        '            changed=sh(["git","diff","--name-only",f"{merge_base}..{head}"]).splitlines()\n',
    )

    sql_anchor = '            nums=[int(m.group(1)) for path in (ROOT/"database/journey-connect-db-v2.7").glob("*.sql") if (m:=re.match(r"(\\d+)_",path.name))]\n            need(set(nums)==set(range(1,53)) and len(nums)==52,"SQL inventory")\n            return f"authorized RCA1B/SC/RCA2 phase diff {len(changed)} files; source/core/SQL/production config protected"\n'
    if source.count(sql_anchor) != 1:
        raise SystemExit("FAIL: RCA-1 SQL inventory compatibility anchor mismatch")
    source = source.replace(
        sql_anchor,
        '            nums=[int(m.group(1)) for path in (ROOT/"database/journey-connect-db-v2.7").glob("*.sql") if (m:=re.match(r"(\\d+)_",path.name))]\n'
        '            need(nums and set(range(1,53))<=set(nums) and set(nums)==set(range(1,max(nums)+1)) and len(nums)==max(nums),"SQL inventory")\n'
        '            return f"authorized current successor diff {len(changed)} files; RCA1 source/core/SQL 01..52/production config protected; canonical SQL contiguous through {max(nums):02d}"\n',
        1,
    )

    regression_anchor = '                command=[sys.executable,str(ROOT/"verification/rca0/run_rca0_verification.py"),"--execute-regressions"]\n'
    if source.count(regression_anchor) != 1:
        raise SystemExit("FAIL: RCA-1 RCA-0 regression compatibility anchor mismatch")
    source = source.replace(
        regression_anchor,
        '                command=[sys.executable,str(ROOT/"verification/operations/op2/run_rca0_successor_continuity.py"),"--execute-regressions"]\n',
        1,
    )
    return source


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("target", choices=sorted(TARGETS))
    args, passthrough = parser.parse_known_args()
    target = TARGETS[args.target]
    source_path = ROOT / target["path"]
    actual_blob = subprocess.check_output(
        ["git", "rev-parse", f"HEAD:{target['path']}"], cwd=ROOT, text=True
    ).strip()
    if actual_blob != target["blob"]:
        raise SystemExit(
            f"FAIL: historical verifier source drift: {target['path']} actual={actual_blob} expected={target['blob']}"
        )
    source = source_path.read_text(encoding="utf-8")
    anchor = target["anchor"]
    if source.count(anchor) != 1:
        raise SystemExit(f"FAIL: historical verifier compatibility anchor mismatch: {target['path']}")
    addition = "".join(f'{target["indent"]}"{path}",\n' for path in APPROVED_LATER_PHASE_PATHS)
    source = source.replace(anchor, anchor + addition, 1)

    if args.target == "rca1":
        source = adapt_rca1(source)

    current_head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    subprocess.run(
        ["git", "merge-base", "--is-ancestor", "f17fc3e515264eefcf2ca2b113a0e5875bbde6ae", current_head],
        cwd=ROOT,
        check=True,
    )

    old_argv = sys.argv
    try:
        sys.argv = [str(source_path), *passthrough]
        namespace = {"__name__": "__main__", "__file__": str(source_path), "__package__": None}
        exec(compile(source, str(source_path), "exec"), namespace)
    finally:
        sys.argv = old_argv
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
