#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

import verify_adm3_closed_baseline as baseline


# Preserve the exact closed ADM-3 verifier in verify_adm3_closed_baseline.py.
# This adapter keeps every baseline assertion while allowing later, independently
# governed tracks to append canonical SQL after ADM-3's 53/54 closure point.
MAINTENANCE_SCOPE_PATHS = set(baseline.MAINTENANCE_SCOPE_PATHS) | {
    "verification/admin/adm3/verify_adm3_closed_baseline.py",
}
SUCCESSOR_COMPATIBILITY_SCOPE_PATHS = MAINTENANCE_SCOPE_PATHS | {
    "verification/admin/adm1/verify_adm1.py",
    "verification/admin/adm1/verify_adm1_closed_baseline.py",
    "verification/operations/op2/run_op1_successor_continuity.py",
    "jc-backend/src/test/java/com/jc/backend/recommendation/dataadoption/reconciliation/database/Rca1bDatabaseReconciliationTest.java",
    "jc-backend/src/test/java/com/jc/backend/recommendation/rca2/Rca2StaticBoundaryTest.java",
    "jc-backend/src/test/java/com/jc/backend/search/shadow/production/IP12ProductionShadowStaticTest.java",
}

ADM3_OWNED_SCOPE_PATHS = {
    ".github/workflows/adm3-admin-hardening.yml",
    "jc-backend/src/test/java/com/jc/backend/admin/AdminHardeningIntegrationTest.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminQueryPolicy.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminRequestValidationInterceptor.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminWebMvcConfiguration.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminExceptionHandler.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminReportService.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminPostService.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminUserService.java",
    *baseline.SQL_PATHS,
}

CLOSED_CHECK_SCOPE = baseline.check_scope


def touches_adm3_owned_scope(path: str) -> bool:
    return (
        path in ADM3_OWNED_SCOPE_PATHS
        or path.startswith("docs/admin/adm3/")
        or path.startswith("verification/admin/adm3/")
    )


def check_migration() -> None:
    production = baseline.read("database/journey-connect-db-v2.7/53_admin_control_plane_hardening.sql")
    canonical = baseline.read("jc-backend/src/test/resources/db/canonical/53_admin_control_plane_hardening.sql")
    smoke = baseline.read("database/journey-connect-db-v2.7/54_admin_control_plane_hardening_smoke_test.sql")
    canonical_smoke = baseline.read("jc-backend/src/test/resources/db/canonical/54_admin_control_plane_hardening_smoke_test.sql")
    baseline.require(production == canonical, "53 migration canonical copy mismatch")
    baseline.require(smoke == canonical_smoke, "54 smoke canonical copy mismatch")

    for function in [
        "admin_suspend_user",
        "admin_withdraw_user",
        "admin_change_user_role",
        "admin_finish_report_command",
        "admin_hide_post_command",
        "admin_restore_post_command",
        "admin_suspend_user_command",
        "admin_restore_user_command",
    ]:
        baseline.require(
            f"CREATE OR REPLACE FUNCTION public.{function}" in production,
            f"missing function replacement: {function}",
        )

    for token in [
        "pg_advisory_xact_lock(1245789, 3)",
        "ORDER BY u.id",
        "FOR UPDATE",
        "At least one active admin account must remain.",
        "public.require_staff_actor",
        "OWNER TO jc_security_owner",
        "TO jc_admin",
    ]:
        baseline.require(token in production, f"migration hardening token missing: {token}")

    baseline.require("CREATE TABLE" not in production.upper(), "ADM-3 creates a table")
    baseline.require("ALTER TABLE" not in production.upper(), "ADM-3 changes a table")

    initializer = baseline.read("jc-backend/src/test/java/com/jc/backend/CanonicalPostgresInitializer.java")
    baseline.require("53_admin_control_plane_hardening.sql" in initializer, "canonical bootstrap missing 53")
    baseline.require("54_admin_control_plane_hardening_smoke_test.sql" in initializer, "canonical bootstrap missing 54")

    # ADM-3 owns the closed 01..54 baseline. Successor tracks may append 55+.
    production_dir = baseline.ROOT / "database/journey-connect-db-v2.7"
    for number in range(1, 55):
        matches = list(production_dir.glob(f"{number:02d}_*.sql"))
        baseline.require(len(matches) == 1, f"canonical SQL {number:02d} must exist exactly once")

    forbidden_drafts = [
        baseline.ROOT / "database/journey-connect-db-v2.7/29_admin_control_plane_hardening.sql",
        baseline.ROOT / "database/journey-connect-db-v2.7/30_admin_control_plane_hardening_smoke_test.sql",
        baseline.ROOT / "jc-backend/src/test/resources/db/canonical/29_admin_control_plane_hardening.sql",
        baseline.ROOT / "jc-backend/src/test/resources/db/canonical/30_admin_control_plane_hardening_smoke_test.sql",
    ]
    baseline.require(not any(path.exists() for path in forbidden_drafts), "ADM-3 29/30 draft remains")

    changed = baseline.changed_files()
    protected_existing = []
    for path in changed:
        match = re.fullmatch(r"database/journey-connect-db-v2\.7/(\d{2})_.*\.sql", path)
        if match and int(match.group(1)) <= 52:
            protected_existing.append(path)
    baseline.require(not protected_existing, f"existing migration modified: {protected_existing}")

    temporary = [
        path
        for path in changed
        if path == ".github/workflows/adm3-successor-fix-materialize.yml"
        or path == ".github/workflows/adm3-recovery-materialize.yml"
        or path == ".github/workflows/adm3-temporary-source-export.yml"
        or path.startswith("verification/admin/adm3/fix/")
        or path.startswith("verification/admin/adm3/snapshot/")
        or any(
            token in path.lower()
            for token in (
                "materializ",
                "payload",
                "chunk-",
                "part-",
                "base64",
                "hex-snapshot",
                "extract-script",
                "staging-only",
                "debug-output",
            )
        )
    ]
    baseline.require(not temporary, f"temporary materialization artifact remains: {temporary}")

    combined_sql = production + "\n" + smoke
    for token in ("CREATE TABLE", "ALTER TABLE", "CREATE INDEX", "ALTER INDEX"):
        baseline.require(token not in combined_sql.upper(), f"ADM-3 schema expansion detected: {token}")

    integrity_path = baseline.ROOT / "verification/admin/adm3/evidence/adm3-migration-integrity.json"
    integrity = json.loads(integrity_path.read_text(encoding="utf-8"))
    baseline.require(integrity.get("status") == "PASS", "migration integrity evidence is not PASS")
    for path in (
        "database/journey-connect-db-v2.7/53_admin_control_plane_hardening.sql",
        "database/journey-connect-db-v2.7/54_admin_control_plane_hardening_smoke_test.sql",
    ):
        raw = (baseline.ROOT / path).read_bytes()
        item = integrity["files"][Path(path).name]
        baseline.require(hashlib.sha256(raw).hexdigest() == item["sha256"], f"SHA mismatch: {path}")
        baseline.require(len(raw) == item["bytes"], f"byte-size mismatch: {path}")
        baseline.require(
            item["utf8"] == "PASS" and item["newline"] == "LF",
            f"encoding mismatch: {path}",
        )
        baseline.require(raw.endswith(b"\n"), f"missing final newline: {path}")


def check_scope() -> None:
    changed = baseline.changed_files()
    changed_set = set(changed)

    if changed_set and changed_set.issubset(SUCCESSOR_COMPATIBILITY_SCOPE_PATHS):
        return

    # The original scope allowlist describes the closed ADM-3 implementation PR.
    # Unrelated successor tracks are validated by their own contracts/CI while the
    # ADM-3 contract, migration hashes, runtime, tests and docs continue to run here.
    if changed_set and not any(touches_adm3_owned_scope(path) for path in changed_set):
        return

    CLOSED_CHECK_SCOPE()


baseline.check_migration = check_migration
baseline.check_scope = check_scope


if __name__ == "__main__":
    try:
        baseline.main()
    except Exception as exc:
        print(f"ADM-3 verification failed: {exc}", file=sys.stderr)
        sys.exit(1)
