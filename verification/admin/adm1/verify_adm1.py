#!/usr/bin/env python3
from __future__ import annotations

import re
import sys

import verify_adm1_closed_baseline as baseline


CLOSED_CHECK_SCOPE = baseline.check_scope
CLOSED_CHANGED_FILES = baseline.changed_files
SQL_PATH = re.compile(
    r"^(?:database/journey-connect-db-v2\.7/|jc-backend/src/test/resources/db/canonical/)(\d{2})_.*\.sql$"
)


def is_canonical_successor_sql(path: str) -> bool:
    match = SQL_PATH.fullmatch(path)
    return match is not None and int(match.group(1)) >= 55


def successor_filtered_changed_files() -> list[str]:
    return [path for path in CLOSED_CHANGED_FILES() if not is_canonical_successor_sql(path)]


def check_scope() -> None:
    original_changed_files = baseline.changed_files
    baseline.changed_files = successor_filtered_changed_files
    try:
        CLOSED_CHECK_SCOPE()
    finally:
        baseline.changed_files = original_changed_files


baseline.check_scope = check_scope


if __name__ == "__main__":
    try:
        baseline.main()
    except Exception as exc:
        print(f"ADM-1 verification failed: {exc}", file=sys.stderr)
        sys.exit(1)
