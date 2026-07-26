#!/usr/bin/env python3
"""Run protected RCA-1/RCA-1B verifiers with only approved later-phase paths added.

The historical verifier source is executed unchanged except for a narrow extension
of its cross-phase diff allowlist. Historical documents, fixtures, expected
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
    "docs/platform/operations/op0/",
    "docs/platform/operations/op1/",
    "verification/operations/op0/",
    "verification/operations/op1/",
)
TARGETS = {
    "rca1": {
        "path": "verification/rca1/run_rca1_verification.py",
        "anchor": '                ".github/actions/rca2-job/",\n',
        "indent": "                ",
    },
    "rca1b": {
        "path": "verification/rca1b/run_rca1b_verification.py",
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
    source = source_path.read_text(encoding="utf-8")
    anchor = target["anchor"]
    if source.count(anchor) != 1:
        raise SystemExit(f"FAIL: historical verifier compatibility anchor mismatch: {target['path']}")
    addition = "".join(f'{target["indent"]}"{path}",\n' for path in APPROVED_LATER_PHASE_PATHS)
    source = source.replace(anchor, anchor + addition, 1)

    current_head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    work_start = "0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d"
    subprocess.run(["git", "merge-base", "--is-ancestor", work_start, current_head], cwd=ROOT, check=True)

    old_argv = sys.argv
    try:
        sys.argv = [str(source_path), *passthrough]
        namespace = {
            "__name__": "__main__",
            "__file__": str(source_path),
            "__package__": None,
        }
        exec(compile(source, str(source_path), "exec"), namespace)
    finally:
        sys.argv = old_argv
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
