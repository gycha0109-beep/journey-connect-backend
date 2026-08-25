#!/usr/bin/env python3
"""Run authoritative SC-6 verification across later successor work.

The historical SC-6 verifier and evidence remain frozen. This wrapper evaluates
only the current successor delta that is relevant to SC-6, while canonical SQL
successors are protected by contiguous numbering rather than the obsolete
repository-wide "53+ absent" assumption.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE_COMMIT = "7603081fa07b14946c66799954846eed84f62f39"
SOURCE_PATH = (
    "verification/sc-next-track/rca2-nonzero-nonprod-entry/"
    "run_sc6_rca2_nonzero_nonprod_entry_verification.py"
)

source = subprocess.check_output(
    ["git", "show", f"{SOURCE_COMMIT}:{SOURCE_PATH}"],
    cwd=ROOT,
    text=True,
)

current_base = subprocess.check_output(
    ["git", "merge-base", "origin/main", "HEAD"], cwd=ROOT, text=True
).strip()
successor_current_changed = subprocess.check_output(
    ["git", "diff", "--name-only", f"{current_base}...HEAD"], cwd=ROOT, text=True
).splitlines()


def canonical_sql_number(rel: str) -> int | None:
    prefix = "database/journey-connect-db-v2.7/"
    if not rel.startswith(prefix):
        return None
    name = rel[len(prefix):]
    if len(name) >= 4 and name[:2].isdigit() and name[2] == "_" and name.endswith(".sql"):
        return int(name[:2])
    return None


def sc6_sensitive(rel: str) -> bool:
    number = canonical_sql_number(rel)
    if number is not None:
        return number < 53
    exact = {
        ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",
        ".github/workflows/sc-op3-entry-governance-ci.yml",
        ".github/workflows/sc-baseline-reconciliation.yml",
        ".github/workflows/data-platform-closure-ci.yml",
        "docs/platform/governance/SC-DECISION-REGISTER.md",
        "docs/platform/governance/SC-PLATFORM-REGISTRY.md",
        "docs/platform/governance/SC-RACI.md",
        "docs/platform/governance/SC-HANDOFF.md",
        "jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java",
        "jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java",
    }
    if rel in exact:
        return True
    prefixes = (
        "verification/sc-next-track/rca2-nonzero-nonprod-entry/",
        "verification/sc-next-track/op3-entry/",
        "verification/rca0/",
        "verification/rca1/",
        "verification/rca1b/",
        "verification/rca2/",
        "docs/platform/recommendation/rca2/",
        "docs/platform/governance/sc-next-track/SC-6-",
        "docs/platform/governance/sc-next-track/57-",
        "docs/platform/governance/sc-next-track/58-",
        "docs/platform/governance/sc-next-track/59-",
        "docs/platform/governance/sc-next-track/60-",
        "docs/platform/governance/sc-next-track/61-",
        "docs/platform/governance/sc-next-track/62-",
        "docs/platform/governance/sc-next-track/63-",
        "docs/platform/governance/sc-next-track/64-",
        "docs/platform/governance/sc-next-track/65-",
        "docs/platform/governance/sc-next-track/66-",
        "docs/platform/governance/sc-next-track/67-",
        "docs/platform/governance/sc-next-track/68-",
        "docs/platform/governance/sc-next-track/69-",
        "docs/platform/governance/sc-next-track/70-",
        "docs/platform/governance/sc-next-track/71-",
        "docs/platform/governance/sc-next-track/72-",
        "docs/platform/governance/sc-next-track/73-",
        "docs/platform/governance/sc-next-track/74-",
        "docs/platform/governance/sc-next-track/75-",
        "database/",
        "jc-recommendation-core/",
        "jc-backend/src/main/resources/application",
    )
    return rel.startswith(prefixes)


successor_sc6_changed = [rel for rel in successor_current_changed if sc6_sensitive(rel)]

changed_anchor = (
    'return [p for p in sh("git", "diff", "--name-only", '
    'f"{WORK_START}..{head}").splitlines() if p]'
)
if source.count(changed_anchor) != 1:
    raise SystemExit("FAIL: authoritative SC-6 changed-path anchor mismatch")
source = source.replace(changed_anchor, 'return list(successor_sc6_changed)', 1)

sql_anchor = '''    if list(SQL.glob("5[3-9]_*.sql")) or list(SQL.glob("[6-9][0-9]_*.sql")):
        raise AssertionError("SQL 53+ exists")
    if any(p.startswith("database/") for p in changed_paths(head)):
        raise AssertionError("database path changed")
    return "canonical SQL 01..52 protected and SQL 53+ absent"
'''
if source.count(sql_anchor) != 1:
    raise SystemExit("FAIL: SC-6 SQL successor compatibility anchor mismatch")
source = source.replace(
    sql_anchor,
    '''    successor_numbers = sorted(
        int(path.name[:2])
        for path in SQL.glob("[0-9][0-9]_*.sql")
        if path.name[:2].isdigit() and int(path.name[:2]) >= 53
    )
    if successor_numbers and successor_numbers != list(range(53, max(successor_numbers) + 1)):
        raise AssertionError("canonical SQL successor sequence gap or duplicate")
    if any(p.startswith("database/") for p in changed_paths(head)):
        raise AssertionError("protected database path changed")
    return "canonical SQL 01..52 protected and successor sequence contiguous"
''',
    1,
)

exact_anchor = (
    '        ".github/workflows/sc6-rca2-nonzero-nonprod-stage1-governance-ci.yml",\n'
)
prefix_anchor = (
    '        "verification/sc-next-track/rca2-nonzero-nonprod-entry/",\n'
)
if source.count(exact_anchor) != 1:
    raise SystemExit("FAIL: authoritative SC-6 exact allowlist anchor mismatch")
if source.count(prefix_anchor) != 1:
    raise SystemExit("FAIL: authoritative SC-6 prefix allowlist anchor mismatch")
source = source.replace(
    exact_anchor,
    exact_anchor
    + '        ".github/workflows/sc-op3-entry-governance-ci.yml",\n'
    + '        ".github/workflows/sc-baseline-reconciliation.yml",\n'
    + '        ".github/workflows/data-platform-closure-ci.yml",\n',
    1,
)
source = source.replace(
    prefix_anchor,
    prefix_anchor + '        "verification/sc-next-track/op3-entry/",\n',
    1,
)

namespace = {
    "__name__": "__main__",
    "__file__": str(ROOT / SOURCE_PATH),
    "__package__": None,
    "successor_sc6_changed": successor_sc6_changed,
}
exec(compile(source, str(ROOT / SOURCE_PATH), "exec"), namespace)
