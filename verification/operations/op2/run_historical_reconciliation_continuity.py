#!/usr/bin/env python3
"""Run protected RCA-1/RCA-1B verifiers with authorised OP-2 successor paths.

Historical verifier source is executed unchanged except for a narrow extension
of the cross-phase diff allowlist. Historical documents, fixtures, expected
classifications, SQL protections and runtime regression checks remain intact.
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
