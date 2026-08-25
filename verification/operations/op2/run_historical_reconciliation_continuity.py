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
        "path": "verification/rca1b/run_rca1b_verification_closed_baseline.py",
        "blob": "0d3cff8b73ad73339cc35232f991bfd6fa25b4d2",
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

    unexpected_anchor = '            unexpected=[item for item in changed if not any(item==prefix or item.startswith(prefix) for prefix in allowed)]\n'
    if source.count(unexpected_anchor) != 1:
        raise SystemExit("FAIL: RCA-1 protected-scope compatibility anchor mismatch")
    source = source.replace(
        unexpected_anchor,
        '''            def is_canonical_successor_sql(item):
                for prefix in (
                    "database/journey-connect-db-v2.7/",
                    "jc-backend/src/test/resources/db/canonical/",
                ):
                    if item.startswith(prefix):
                        name=item[len(prefix):]
                        return len(name)>=4 and name[:2].isdigit() and name[2]=="_" and name.endswith(".sql") and int(name[:2])>=53
                return False
            def is_rca1_sensitive(item):
                if any(item==prefix or item.startswith(prefix) for prefix in allowed):
                    return True
                if item.startswith(("database/","jc-recommendation-core/")):
                    return True
                if item in (
                    "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java",
                    "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java",
                ):
                    return True
                return bool(re.search(r"jc-backend/src/main/resources/application.*\\.(?:yml|yaml|properties)$",item))
            changed=[item for item in changed if not is_canonical_successor_sql(item) and is_rca1_sensitive(item)]
            unexpected=[item for item in changed if not any(item==prefix or item.startswith(prefix) for prefix in allowed)]
''',
        1,
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


def adapt_rca1b(source: str) -> str:
    sql_anchor = '        need(set(scripts) == set(range(1, 55)), "SQL 01..54 inventory")\n'
    if source.count(sql_anchor) != 1:
        raise SystemExit("FAIL: RCA-1B canonical SQL compatibility anchor mismatch")
    source = source.replace(
        sql_anchor,
        '        need(set(range(1, 55)) <= set(scripts), "closed SQL 01..54 baseline missing")\n'
        '        need(set(scripts) == set(range(1, max(scripts) + 1)), "canonical SQL successor sequence gap")\n',
        1,
    )

    diff_anchor = '        changed = sh("git", "diff", "--name-only", f"{WORK_START}..{head}").splitlines()\n'
    if source.count(diff_anchor) != 1:
        raise SystemExit("FAIL: RCA-1B changed-file compatibility anchor mismatch")
    source = source.replace(
        diff_anchor,
        '''        current_base = sh("git", "merge-base", "origin/main", head)
        current_changed = sh("git", "diff", "--name-only", f"{current_base}...{head}").splitlines()

        def canonical_sql_number(path: str) -> int | None:
            for prefix in (
                "database/journey-connect-db-v2.7/",
                "jc-backend/src/test/resources/db/canonical/",
            ):
                if path.startswith(prefix):
                    name = path[len(prefix):]
                    if len(name) >= 4 and name[:2].isdigit() and name[2] == "_" and name.endswith(".sql"):
                        return int(name[:2])
            return None

        relevant_prefixes = (
            ".github/actions/rca2-job/",
            ".github/workflows/rca1b-nonproduction-readonly-reconciliation-ci.yml",
            ".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",
            ".github/workflows/sc-baseline-reconciliation.yml",
            "docs/platform/governance/",
            "docs/platform/recommendation/RCA-1B-",
            "docs/platform/recommendation/rca2/",
            "jc-backend/build.gradle.kts",
            "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java",
            "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/",
            "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml",
            "jc-backend/src/test/java/com/jc/backend/recommendation/dataadoption/reconciliation/database/",
            "jc-backend/src/test/java/com/jc/backend/recommendation/rca2/",
            "jc-backend/src/test/java/com/jc/backend/verification/IP9ControlledBackendHookStaticTest.java",
            "jc-backend/src/test/resources/recommendation-data-adoption/rca1b/",
            "jc-search-readiness/src/test/java/com/jc/intelligence/readiness/search/SearchShadowReadinessContractTest.java",
            "verification/rca0/run_rca0_verification.py",
            "verification/rca1/run_rca1_verification.py",
            "verification/rca1b/",
            "verification/rca2/",
            "verification/data-platform-closure/run_data_platform_closure_verification.py",
            "verification/dp5/run_dp5_static_verification.py",
            "verification/dp6/run_dp6_allocation_verification.py",
            "verification/dp6/run_dp6_static_verification.py",
            "verification/dp7/run_dp7_allocation_verification.py",
            "verification/dp7/run_dp7_static_verification.py",
            "verification/sc-dp1-baseline-reconciliation/",
            "verification/sc-next-track/",
            "jc-recommendation-core/",
            "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java",
            "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java",
        )
        changed = []
        for item in current_changed:
            sql_number = canonical_sql_number(item)
            if sql_number is not None:
                if sql_number <= 54:
                    changed.append(item)
                continue
            if item.startswith(relevant_prefixes):
                changed.append(item)
''',
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
    elif args.target == "rca1b":
        source = adapt_rca1b(source)

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
