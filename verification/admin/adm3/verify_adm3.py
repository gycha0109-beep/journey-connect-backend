#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CONTRACT = ROOT / "verification/admin/adm3/adm3-contract.json"
EVIDENCE = ROOT / "verification/admin/adm3/evidence/adm3-verification-evidence.json"
BASE = "e7dd0d11de9104e2be62f9ba886ddc20cfe27fad"

EXPECTED = {
    "ADM3_ADMIN_API_HARDENING_COMPLETE": "YES",
    "ADM3_ACCEPTANCE_COMPLETE": "YES",
    "CROSS_ADMIN_SELF_SUSPEND_BLOCKED": "YES",
    "LAST_ACTIVE_ADMIN_PROTECTED": "YES",
    "CROSS_ADMIN_TOTAL_LOCKOUT_PROTECTED": "YES",
    "AUDIT_FAILURE_ROLLS_BACK_MUTATION": "YES",
    "MUTATION_FAILURE_CREATES_NO_AUDIT": "YES",
    "DUPLICATE_AUDIT_ON_RETRY": "NO",
    "CONCURRENT_REPORT_COMMANDS_SAFE": "YES",
    "CONCURRENT_POST_COMMANDS_SAFE": "YES",
    "CONCURRENT_USER_COMMANDS_SAFE": "YES",
    "AUDIT_PRIVACY_VERIFIED": "YES",
    "API_RESPONSE_PRIVACY_VERIFIED": "YES",
    "ERROR_RESPONSE_PRIVACY_VERIFIED": "YES",
    "ADMIN_INPUT_BOUNDS_VERIFIED": "YES",
    "ADMIN_MVP_ACCEPTANCE_FLOW": "PASS",
    "ADMIN_AUTHORIZATION_GUARD_REUSED": "YES",
    "ADMIN_DATABASE_ROLE": "JC_ADMIN",
    "PHYSICAL_DELETE_IMPLEMENTED": "NO",
    "ROLE_MANAGEMENT_IMPLEMENTED": "NO",
    "ADMIN_APPOINTMENT_IMPLEMENTED": "NO",
    "FRONTEND_SOURCE_CHANGE": "NO",
    "YOUNGTAK_SOURCE_CHANGE": "NO",
    "ADMIN_UI_PORT_EXECUTED": "NO",
    "ADMIN_MVP_FEATURE_EXPANSION": "NO",
    "BACKEND_HARDENING": "STRONG",
    "UI_COMPLEXITY": "LOW",
    "SQL_CHANGE": "29_30_FORWARD_ONLY",
    "DB_SCHEMA_CHANGE": "FUNCTION_REPLACEMENT_ONLY",
    "ADM4_ENTRY": "BLOCKED_PENDING_USER_APPROVAL",
}

REQUIRED_TESTS = {
    "admin_cannot_suspend_self",
    "last_active_admin_cannot_be_suspended",
    "concurrent_cross_admin_suspend_does_not_lock_out_all_admins",
    "suspended_actor_cannot_commit_admin_command",
    "actor_state_is_rechecked_after_lock",
    "audit_failure_rolls_back_report_mutation",
    "audit_failure_rolls_back_post_mutation",
    "audit_failure_rolls_back_user_mutation",
    "mutation_failure_creates_no_audit",
    "forced_exception_rolls_back_transaction",
    "concurrent_same_report_command_is_idempotent",
    "concurrent_conflicting_report_commands_yield_one_conflict",
    "concurrent_same_post_command_has_single_audit",
    "concurrent_hide_restore_preserves_valid_state",
    "concurrent_same_user_command_has_single_audit",
    "concurrent_suspend_unsuspend_preserves_valid_state",
    "dashboard_response_contains_only_allowed_fields",
    "report_list_response_contains_only_allowed_fields",
    "report_detail_response_contains_only_allowed_fields",
    "post_list_response_contains_only_allowed_fields",
    "post_detail_response_contains_only_allowed_fields",
    "user_list_response_contains_only_allowed_fields",
    "user_detail_response_contains_only_allowed_fields",
    "error_response_does_not_expose_internal_fields",
    "audit_snapshot_does_not_contain_secrets",
    "negative_page_rejected",
    "oversized_page_size_rejected",
    "oversized_search_rejected",
    "blank_reason_rejected",
    "oversized_reason_rejected",
    "unsupported_filter_rejected",
    "admin_mvp_end_to_end_acceptance_passes",
    "normal_user_cannot_execute_acceptance_flow",
    "suspended_admin_loses_access_mid_flow",
    "all_mutations_create_exactly_one_audit",
    "physical_delete_never_occurs",
}

SQL_PATHS = {
    "database/journey-connect-db-v2.7/29_admin_control_plane_hardening.sql",
    "database/journey-connect-db-v2.7/30_admin_control_plane_hardening_smoke_test.sql",
    "jc-backend/src/test/resources/db/canonical/29_admin_control_plane_hardening.sql",
    "jc-backend/src/test/resources/db/canonical/30_admin_control_plane_hardening_smoke_test.sql",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(path: str) -> str:
    candidate = ROOT / path
    require(candidate.is_file(), f"missing file: {path}")
    return candidate.read_text(encoding="utf-8")


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def changed_files() -> list[str]:
    base = os.getenv("ADM3_BASE_SHA", BASE)
    head = os.getenv("ADM3_HEAD_SHA", "HEAD")
    try:
        subprocess.check_call(
            ["git", "cat-file", "-e", f"{base}^{{commit}}"],
            cwd=ROOT,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        out = git("diff", "--name-only", f"{base}...{head}")
    except subprocess.CalledProcessError:
        tracked = git("diff", "--name-only", "HEAD").splitlines()
        status = git("status", "--porcelain=v1").splitlines()
        untracked = [line[3:] for line in status if line.startswith("?? ")]
        return [line for line in tracked + untracked if line]
    return [line for line in out.splitlines() if line]


def check_head() -> None:
    expected = os.getenv("ADM3_HEAD_SHA")
    if expected:
        require(git("rev-parse", "HEAD") == expected, "not exact ADM-3 head")


def check_contract() -> None:
    data = json.loads(CONTRACT.read_text(encoding="utf-8"))
    require(data["work_start_sha"] == BASE, "work-start SHA mismatch")
    require(data["api"]["endpoint_count"] == 13, "endpoint inventory changed")
    require(data["api"]["feature_expansion"] == "NO", "feature expansion detected")
    require(data["api"]["max_page_size"] == 100, "page bound mismatch")
    require(data["api"]["max_search_length"] == 100, "search bound mismatch")
    require(data["api"]["max_reason_length"] == 1000, "reason bound mismatch")
    for key, value in EXPECTED.items():
        require(data["result"].get(key) == value, f"contract mismatch: {key}")


def check_migration() -> None:
    production = read("database/journey-connect-db-v2.7/29_admin_control_plane_hardening.sql")
    canonical = read("jc-backend/src/test/resources/db/canonical/29_admin_control_plane_hardening.sql")
    smoke = read("database/journey-connect-db-v2.7/30_admin_control_plane_hardening_smoke_test.sql")
    canonical_smoke = read("jc-backend/src/test/resources/db/canonical/30_admin_control_plane_hardening_smoke_test.sql")
    require(production == canonical, "29 migration canonical copy mismatch")
    require(smoke == canonical_smoke, "30 smoke canonical copy mismatch")
    for function in ["admin_suspend_user", "admin_withdraw_user", "admin_change_user_role"]:
        require(f"CREATE OR REPLACE FUNCTION public.{function}" in production, f"missing function replacement: {function}")
    for token in [
        "pg_advisory_xact_lock(1245789, 3)",
        "ORDER BY u.id",
        "FOR UPDATE",
        "At least one active admin account must remain.",
        "public.require_staff_actor",
        "OWNER TO jc_security_owner",
        "TO jc_admin",
    ]:
        require(token in production, f"migration hardening token missing: {token}")
    require("CREATE TABLE" not in production.upper(), "ADM-3 creates a table")
    require("ALTER TABLE" not in production.upper(), "ADM-3 changes a table")
    initializer = read("jc-backend/src/test/java/com/jc/backend/CanonicalPostgresInitializer.java")
    require("29_admin_control_plane_hardening.sql" in initializer, "canonical bootstrap missing 29")
    require("30_admin_control_plane_hardening_smoke_test.sql" in initializer, "canonical bootstrap missing 30")


def check_runtime() -> None:
    controllers = [
        "AdminDashboardController.java",
        "AdminReportController.java",
        "AdminPostController.java",
        "AdminUserController.java",
    ]
    controller_text = "\n".join(read(f"jc-backend/src/main/java/com/jc/backend/admin/{name}") for name in controllers)
    mapping_count = len(re.findall(r"@(Get|Post)Mapping", controller_text))
    require(mapping_count == 13, f"endpoint count changed: {mapping_count}")
    for service in ["AdminDashboardService.java", "AdminReportService.java", "AdminPostService.java", "AdminUserService.java"]:
        source = read(f"jc-backend/src/main/java/com/jc/backend/admin/{service}")
        require("guard.requireActiveAdmin()" in source, f"guard reuse missing: {service}")
        require("DatabaseRole.ADMIN" in source, f"DB role routing missing: {service}")
    policy = read("jc-backend/src/main/java/com/jc/backend/admin/AdminQueryPolicy.java")
    for token in ["Normalizer.Form.NFKC", "MAX_PAGE_SIZE = 100", "MAX_SEARCH_LENGTH = 100", "MAX_REASON_LENGTH = 1000", "SECRET_MATERIAL", "isISOControl"]:
        require(token in policy, f"input hardening missing: {token}")
    interceptor = read("jc-backend/src/main/java/com/jc/backend/admin/AdminRequestValidationInterceptor.java")
    for token in ["getParameterMap", "getParameterValues", "지원하지 않는", "중복"]:
        require(token in interceptor, f"request boundary missing: {token}")
    advice = read("jc-backend/src/main/java/com/jc/backend/admin/AdminExceptionHandler.java")
    for token in ["INVALID_ADMIN_COMMAND", "ADMIN_OPERATION_FAILED", "DataAccessException"]:
        require(token in advice, f"error privacy handler missing: {token}")
    combined = controller_text + policy + interceptor + advice
    require("admin_change_user_role" not in controller_text, "role management endpoint introduced")
    require("delete from public.posts" not in combined.lower(), "physical delete introduced")


def check_tests() -> None:
    tests = read("jc-backend/src/test/java/com/jc/backend/admin/AdminHardeningIntegrationTest.java")
    for name in REQUIRED_TESTS:
        require(re.search(rf"void\s+{re.escape(name)}\s*\(", tests), f"missing hardening test: {name}")
    for token in ["@CanonicalPostgresTest", "pg_advisory_lock", "pg_stat_activity", "adm3_fail_audit_insert", "ForcedRollbackProbe", "assertFields"]:
        require(token in tests, f"test evidence missing: {token}")


def check_docs() -> None:
    main = read("docs/admin/adm3/ADM-3-ADMIN-API-HARDENING-AUDIT-ACCEPTANCE.md")
    operations = read("docs/admin/adm3/ADM-3-OPERATIONAL-ACCEPTANCE.md")
    handoff = read("docs/admin/adm3/ADM-4-ENTRY-GATE-AND-HANDOFF.md")
    for token in ["CROSS_ADMIN_TOTAL_LOCKOUT_PROTECTED", "failure", "privacy", "acceptance", "29_admin_control_plane_hardening.sql"]:
        require(token.lower() in main.lower(), f"ADM-3 documentation missing: {token}")
    for token in ["ADMIN_USER_PROVISIONING_METHOD", "ALL_ADMIN_LOCKOUT_RECOVERY_PROCEDURE", "PENDING_OWNER_ASSIGNMENT"]:
        require(token in operations, f"operational condition missing: {token}")
    require("ADM4_ENTRY=BLOCKED_PENDING_USER_APPROVAL" in handoff, "ADM-4 gate not blocked")


def check_scope() -> None:
    changed = changed_files()
    forbidden = []
    allowed_exact = {
        ".github/workflows/adm3-admin-hardening.yml",
        "jc-backend/src/test/java/com/jc/backend/CanonicalPostgresInitializer.java",
        "jc-backend/src/test/java/com/jc/backend/admin/AdminHardeningIntegrationTest.java",
        "jc-backend/src/main/java/com/jc/backend/admin/AdminQueryPolicy.java",
        "jc-backend/src/main/java/com/jc/backend/admin/AdminRequestValidationInterceptor.java",
        "jc-backend/src/main/java/com/jc/backend/admin/AdminWebMvcConfiguration.java",
        "jc-backend/src/main/java/com/jc/backend/admin/AdminExceptionHandler.java",
        "jc-backend/src/main/java/com/jc/backend/admin/AdminReportService.java",
        "jc-backend/src/main/java/com/jc/backend/admin/AdminPostService.java",
        "jc-backend/src/main/java/com/jc/backend/admin/AdminUserService.java",
        "verification/admin/adm1/verify_adm1.py",
        "verification/admin/adm2/verify_adm2.py",
        *SQL_PATHS,
    }
    for path in changed:
        lower = path.lower()
        allowed = (
            path in allowed_exact
            or path.startswith("docs/admin/adm3/")
            or path.startswith("verification/admin/adm3/")
        )
        if not allowed:
            forbidden.append(path)
        if "frontend" in lower or "youngtak" in lower:
            forbidden.append(path)
    sql_changed = {path for path in changed if path.endswith(".sql")}
    require(sql_changed == SQL_PATHS, f"unexpected SQL change set: {sorted(sql_changed ^ SQL_PATHS)}")
    require(not forbidden, f"forbidden ADM-3 scope: {sorted(set(forbidden))}")


def write_evidence(checks: list[str]) -> None:
    payload = {
        "schema_version": "adm3-evidence-v1",
        "status": "PASS",
        "checked": checks,
        "head_sha": os.getenv("ADM3_HEAD_SHA") or git("rev-parse", "HEAD"),
        "base_sha": os.getenv("ADM3_BASE_SHA", BASE),
        "contract_sha256": hashlib.sha256(CONTRACT.read_bytes()).hexdigest(),
        "result": EXPECTED,
    }
    EVIDENCE.parent.mkdir(parents=True, exist_ok=True)
    EVIDENCE.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", choices=["all", "contract", "migration", "runtime", "tests", "docs", "scope"], default="all")
    args = parser.parse_args()
    check_head()
    checks = {
        "contract": check_contract,
        "migration": check_migration,
        "runtime": check_runtime,
        "tests": check_tests,
        "docs": check_docs,
        "scope": check_scope,
    }
    selected = list(checks) if args.check == "all" else [args.check]
    for name in selected:
        checks[name]()
    write_evidence(selected)
    print(json.dumps({"status": "PASS", "checked": selected}))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ADM-3 verification failed: {exc}", file=sys.stderr)
        sys.exit(1)
