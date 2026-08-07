#!/usr/bin/env python3
"""Validate stage DB endpoint equivalence without emitting endpoint material."""

from __future__ import annotations

import hashlib
import json
import os
import shlex
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlsplit


class ValidationError(RuntimeError):
    pass


def required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise ValidationError(f"missing required environment variable: {name}")
    return value


def parse_endpoint(value: str, *, jdbc: bool) -> tuple[str, int, str, str]:
    candidate = value[5:] if jdbc and value.startswith("jdbc:") else value
    parsed = urlsplit(candidate)
    if parsed.scheme not in {"postgres", "postgresql"}:
        raise ValidationError("database URL scheme is not PostgreSQL")
    if not parsed.hostname:
        raise ValidationError("database URL host is missing")
    database = unquote(parsed.path.lstrip("/"))
    if not database or "/" in database:
        raise ValidationError("database URL must identify exactly one database")
    port = parsed.port or 5432
    ssl_mode = parse_qs(parsed.query).get("sslmode", [""])[0].strip()
    return parsed.hostname.lower(), port, database, ssl_mode


def atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8")
    os.chmod(temporary, 0o600)
    temporary.replace(path)


def main() -> int:
    try:
        admin_url = required("SR6FH_STAGE_ADMIN_DATABASE_URL")
        jdbc_url = required("SPRING_DATASOURCE_URL")
        expected = required("SR6FH_AUTHORIZED_STAGE_ENDPOINT_SHA256").lower()
        evidence_path = Path(required("SR6FH_ENDPOINT_EVIDENCE_FILE"))
        runtime_env_path = Path(required("SR6FH_RUNTIME_DB_ENV_FILE"))

        if expected == "unassigned" or len(expected) != 64:
            raise ValidationError("authorized stage endpoint fingerprint is not assigned")

        admin = parse_endpoint(admin_url, jdbc=False)
        backend = parse_endpoint(jdbc_url, jdbc=True)
        if admin[:3] != backend[:3]:
            raise ValidationError("admin and backend URLs do not target the same database endpoint")

        host, port, database, backend_ssl_mode = backend
        canonical = f"postgresql://{host}:{port}/{database}"
        actual = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
        if actual != expected:
            raise ValidationError("stage endpoint fingerprint does not match the authorized contract")

        evidence = {
            "contractVersion": required("SR6FH_CONTRACT_VERSION"),
            "environment": required("SR6FH_AUTHORIZED_ENVIRONMENT"),
            "endpointFingerprint": actual,
            "adminBackendEndpointMatch": True,
            "validatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }
        atomic_write(evidence_path, json.dumps(evidence, sort_keys=True, separators=(",", ":")) + "\n")

        runtime_values = {
            "PGHOST": host,
            "PGPORT": str(port),
            "PGDATABASE": database,
        }
        if backend_ssl_mode:
            runtime_values["PGSSLMODE"] = backend_ssl_mode
        atomic_write(
            runtime_env_path,
            "".join(f"{key}={shlex.quote(value)}\n" for key, value in runtime_values.items()),
        )
        print(f"SR6FH_STAGE_ENDPOINT_FINGERPRINT={actual}")
        return 0
    except (ValidationError, ValueError, OSError) as error:
        print(f"SR6FH_ENDPOINT_VALIDATION=FAILED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
