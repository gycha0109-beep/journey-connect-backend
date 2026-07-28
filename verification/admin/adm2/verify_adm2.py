#!/usr/bin/env python3
import argparse
import json
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CONTRACT_PATH = ROOT / "verification/admin/adm2/adm2-contract.json"
EVIDENCE_PATH = ROOT / "verification/admin/adm2/evidence/adm2-verification-evidence.json"

REQUIRED_FILES = [
    "jc-backend/src/main/java/com/jc/backend/admin/AdminDtos.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminQueryPolicy.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminDashboardService.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminDashboardController.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminReportService.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminReportController.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminPostService.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminPostController.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminUserService.java",
    "jc-backend/src/main/java/com/jc/backend/admin/AdminUserController.java",
    "jc-backend/src/test/java/com/jc/backend/admin/AdminBasicApisIntegrationTest.java",
    "docs/admin/adm2/ADM-2-ADMIN-MODERATION-BASIC-MANAGEMENT-APIS.md",
    "docs/admin/adm2/ADM-3-ENTRY-GATE-AND-HANDOFF.md",
    "verification/admin/adm2/adm2-contract.json",
    ".github/workflows/adm2-admin-basic-apis.yml",
]

EXPECTED_RESULT = {
    "ADM2_ADMIN_BASIC_APIS_IMPLEMENTED": "YES",
    "ADMIN_DASHBOARD_API_IMPLEMENTED": "YES",
    "ADMIN_REPORT_QUERY_API_IMPLEMENTED": "YES",
    "ADMIN_REPORT_COMMAND_API_IMPLEMENTED": "YES",
    "ADMIN_POST_QUERY_API_IMPLEMENTED": "YES",
    "ADMIN_POST_COMMAND_API_IMPLEMENTED": "YES",
    "ADMIN_USER_QUERY_API_IMPLEMENTED": "YES",
    "ADMIN_USER_COMMAND_API_IMPLEMENTED": "YES",
    "ADMIN_AUTHORIZATION_GUARD_REUSED": "YES",
    "ADMIN_DATABASE_ROLE": "JC_ADMIN",
    "ADMIN_QUERY_ROLE_ROUTING": "YES",
    "ADMIN_COMMAND_ROLE_ROUTING": "YES",
    "REPORT_RESOLVE_IMPLEMENTED": "YES",
    "REPORT_DISMISS_IMPLEMENTED": "YES",
    "POST_HIDE_IMPLEMENTED": "YES",
    "POST_RESTORE_IMPLEMENTED": "YES",
    "USER_SUSPEND_IMPLEMENTED": "YES",
    "USER_UNSUSPEND_IMPLEMENTED": "YES",
    "PHYSICAL_DELETE_IMPLEMENTED": "NO",
    "ROLE_MANAGEMENT_IMPLEMENTED": "NO",
    "ADMIN_APPOINTMENT_IMPLEMENTED": "NO",
    "AUDIT_REQUIRED_FOR_ALL_COMMANDS": "YES",
    "AUDIT_AND_MUTATION_ATOMIC": "YES",
    "STATE_TRANSITIONS_VALIDATED": "YES",
    "UNBOUNDED_ADMIN_QUERY": "NO",
    "FRONTEND_SOURCE_CHANGE": "NO",
    "YOUNGTAK_SOURCE_CHANGE": "NO",
    "ADMIN_UI_PORT_EXECUTED": "NO",
    "ADMIN_MVP_SURFACE": "DASHBOARD_BASIC",
    "BACKEND_HARDENING": "STRONG",
    "UI_COMPLEXITY": "LOW",
    "SQL_CHANGE": "NONE",
    "DB_SCHEMA_CHANGE": "NONE",
}


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def check_contract() -> None:
    contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
    require(contract["work_start_sha"] == "d4e1189953fe81a0df8ddc680c60076bf8fb51c4", "wrong work-start SHA")
    for key, value in EXPECTED_RESULT.items():
        require(contract["result"].get(key) == value, f"contract mismatch: {key}")
    require(contract["api"]["max_page_size"] == 100, "max page size must be 100")
    require(len(contract["api"]["endpoints"]) == 13, "endpoint inventory must contain 13 endpoints")


def check_files() -> None:
    missing = [path for path in REQUIRED_FILES if not (ROOT / path).is_file()]
    require(not missing, f"missing files: {missing}")
    require(not (ROOT / ".github/workflows/adm2-source-intake.yml").exists(), "temporary intake workflow remains")


def check_runtime() -> None:
    all_source = "\n".join(read(path) for path in REQUIRED_FILES if path.endswith(".java"))
    for endpoint in [
        "/api/admin/dashboard",
        "/api/admin/reports",
        "/api/admin/posts",
        "/api/admin/users",
        "/resolve",
        "/dismiss",
        "/hide",
        "/restore",
        "/suspend",
        "/unsuspend",
    ]:
        require(endpoint in all_source, f"missing endpoint token: {endpoint}")
    for function in [
        "admin_finish_report",
        "admin_hide_post",
        "admin_restore_post",
        "admin_suspend_user",
        "admin_restore_user",
    ]:
        require(function in all_source, f"missing DB function reuse: {function}")
    require(all_source.count("guard.requireActiveAdmin()") >= 10, "AdminAuthorizationGuard is not consistently reused")
    require(all_source.count("DatabaseRole.ADMIN") >= 10, "DatabaseRole.ADMIN routing is incomplete")
    require("MAX_PAGE_SIZE = 100" in all_source, "bounded page policy missing")
    require("MAX_SEARCH_LENGTH = 100" in all_source, "bounded search policy missing")
    require("INVALID_ADMIN_COMMAND" in all_source, "Admin input error contract missing")
    require("ADMIN_STATE_CONFLICT" in all_source, "Admin state conflict contract missing")
    require("ADMIN_TARGET_NOT_FOUND" in all_source, "Admin target not found contract missing")
    require("delete from public.posts" not in all_source.lower(), "physical post delete found")
    require("admin_change_user_role" not in all_source, "role-management function exposed")
    require("insert into public.admin_actions" not in all_source.lower(), "application-side audit insert found")


def check_tests() -> None:
    tests = read("jc-backend/src/test/java/com/jc/backend/admin/AdminBasicApisIntegrationTest.java")
    expected = [
        "anonymous_returns_401_for_all_admin_endpoints",
        "normal_user_returns_403_for_all_admin_endpoints",
        "suspended_admin_returns_403",
        "jwt_db_role_mismatch_returns_403",
        "dashboard_returns_minimal_aggregates",
        "dashboard_recent_reports_are_limited",
        "dashboard_recent_actions_are_limited",
        "admin_can_list_reports_and_filter_by_status",
        "admin_can_resolve_pending_report_and_write_audit",
        "admin_can_dismiss_pending_report",
        "concurrent_report_commands_do_not_corrupt_state",
        "admin_can_hide_and_restore_post_without_physical_delete",
        "admin_can_list_filter_and_get_users_without_sensitive_fields",
        "admin_can_suspend_and_unsuspend_user_with_audit",
        "admin_cannot_suspend_self",
        "withdrawn_user_cannot_be_unsuspended",
        "suspend_invalidates_db_authoritative_admin_access",
        "unbounded_admin_query_is_rejected",
    ]
    for name in expected:
        require(name in tests, f"missing integration test: {name}")


def check_scope() -> None:
    base = os.environ.get("ADM2_BASE_SHA", "d4e1189953fe81a0df8ddc680c60076bf8fb51c4")
    try:
        subprocess.check_call(["git", "cat-file", "-e", f"{base}^{{commit}}"], cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        changed = git("diff", "--name-only", f"{base}...HEAD").splitlines()
    except subprocess.CalledProcessError:
        changed = git("diff", "--name-only", "HEAD").splitlines()
    forbidden = []
    for path in changed:
        lower = path.lower()
        if "frontend" in lower or "youngtak" in lower:
            forbidden.append(path)
        if path.endswith(".sql"):
            forbidden.append(path)
        if path.startswith("database/") and not path.endswith(".md"):
            forbidden.append(path)
    require(not forbidden, f"forbidden scope changes: {forbidden}")


def write_evidence(checks: list[str]) -> None:
    head = os.environ.get("ADM2_HEAD_SHA")
    if not head:
        try:
            head = git("rev-parse", "HEAD")
        except Exception:
            head = None
    payload = {
        "schema_version": "adm2-evidence-v1",
        "status": "PASS",
        "checked": checks,
        "head_sha": head,
        "base_sha": os.environ.get("ADM2_BASE_SHA", "d4e1189953fe81a0df8ddc680c60076bf8fb51c4"),
        "result": EXPECTED_RESULT,
    }
    EVIDENCE_PATH.parent.mkdir(parents=True, exist_ok=True)
    EVIDENCE_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", default="all")
    args = parser.parse_args()
    checks = ["contract", "files", "runtime", "tests", "scope"] if args.check == "all" else [args.check]
    functions = {
        "contract": check_contract,
        "files": check_files,
        "runtime": check_runtime,
        "tests": check_tests,
        "scope": check_scope,
    }
    for name in checks:
        functions[name]()
    write_evidence(checks)
    print("ADM-2 verifier PASS: " + ", ".join(checks))


if __name__ == "__main__":
    main()
