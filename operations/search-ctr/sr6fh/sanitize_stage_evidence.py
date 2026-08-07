#!/usr/bin/env python3
"""Copy operational logs into an upload-safe directory with endpoint and secret redaction."""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path


URI_PATTERNS = (
    re.compile(r"jdbc:postgresql://[^\s\"']+", re.IGNORECASE),
    re.compile(r"postgres(?:ql)?://[^\s\"']+", re.IGNORECASE),
)


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: sanitize_stage_evidence.py RAW_DIR SAFE_DIR", file=sys.stderr)
        return 2
    raw_dir = Path(sys.argv[1])
    safe_dir = Path(sys.argv[2])
    safe_dir.mkdir(parents=True, exist_ok=True)

    exact_secrets = {
        os.environ.get(name, "")
        for name in (
            "SR6FH_STAGE_ADMIN_DATABASE_URL",
            "SR6FH_STAGE_ADMIN_USERNAME",
            "SR6FH_STAGE_ADMIN_PASSWORD",
            "SPRING_DATASOURCE_URL",
            "SPRING_DATASOURCE_USERNAME",
            "SPRING_DATASOURCE_PASSWORD",
            "APP_SECURITY_JWT_SECRET",
            "PGHOST",
            "PGDATABASE",
            "PGUSER",
            "PGPASSWORD",
        )
        if os.environ.get(name, "")
    }

    for source in sorted(raw_dir.rglob("*")):
        if not source.is_file() or source.name == "runtime-db.env":
            continue
        relative = source.relative_to(raw_dir)
        target = safe_dir / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if source.stat().st_size > 5 * 1024 * 1024:
            target.write_text("<omitted: log exceeded 5 MiB>\n", encoding="utf-8")
            continue
        try:
            text = source.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            target.write_text("<omitted: non-text evidence>\n", encoding="utf-8")
            os.chmod(target, 0o600)
            continue
        for secret in sorted(exact_secrets, key=len, reverse=True):
            text = text.replace(secret, "<redacted>")
        for pattern in URI_PATTERNS:
            text = pattern.sub("<redacted-database-endpoint>", text)
        text = re.sub(r"(?i)(password|secret|token)=([^\s]+)", r"\1=<redacted>", text)
        target.write_text(text, encoding="utf-8")
        os.chmod(target, 0o600)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
