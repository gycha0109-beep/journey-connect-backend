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
CONTRACT = ROOT / "verification/admin/adm1/adm1-contract.json"
DOC = ROOT / "docs/admin/adm1/ADM-1-ADMIN-DATABASE-SECURITY-FOUNDATION.md"
HANDOFF = ROOT / "docs/admin/adm1/ADM-2-ENTRY-GATE-AND-HANDOFF.md"
WORKFLOW = ROOT / ".github/workflows/adm1-admin-security.yml"
EVIDENCE = ROOT / "verification/admin/adm1/evidence/adm1-verification-evidence.json"
BASE = "a2b9e3d8e79df3dcf9d75b418011b3a8cca754b1"

EXPECTED = {
    "ADM1_DATABASE_BASELINE_VERIFIED": "YES",
    "ADM1_SECURITY_FOUNDATION_IMPLEMENTED": "YES",
    "ADMIN_ROLE_SOURCE": "APP_USERS_ROLE",
    "ADMIN_ACCOUNT_STATUS_SOURCE": "APP_USERS_ACCOUNT_STATUS",
    "ADMIN_AUTHORIZATION_SOURCE": "DB_AUTHORITATIVE",
    "ADMIN_API_PREFIX": "/api/admin",
    "ADMIN_ROUTE_AUTHENTICATION_REQUIRED": "YES",
    "ADMIN_ROUTE_ADMIN_ROLE_REQUIRED": "YES",
    "SUSPENDED_ADMIN_BLOCKED": "YES",
    "NORMAL_USER_BLOCKED": "YES",
    "DUPLICATE_ADMIN_TABLE_CREATED": "NO",
    "DUPLICATE_REPORT_TABLE_CREATED": "NO",
    "DUPLICATE_AUDIT_TABLE_CREATED": "NO",
    "SQL_CHANGE": "NONE",
    "DB_SCHEMA_CHANGE": "NONE",
    "FRONTEND_SOURCE_CHANGE": "NO",
    "YOUNGTAK_SOURCE_CHANGE": "NO",
    "ADMIN_UI_PORT_EXECUTED": "NO",
    "ADMIN_MVP_SURFACE": "DASHBOARD_BASIC",
    "BACKEND_HARDENING": "STRONG",
    "UI_COMPLEXITY": "LOW",
    "ADM2_ENTRY": "BLOCKED_PENDING_USER_APPROVAL",
}
TESTS = {
    "anonymous_admin_request_returns_401",
    "normal_user_admin_request_returns_403",
    "active_admin_admin_request_is_allowed",
    "suspended_admin_request_returns_403",
    "inactive_admin_request_returns_403",
    "missing_db_user_returns_403",
    "jwt_admin_but_db_user_role_user_returns_403",
    "jwt_user_but_db_user_role_admin_does_not_bypass_contract",
    "all_admin_routes_require_authentication",
    "all_admin_routes_require_admin_authority",
    "non_admin_routes_are_not_accidentally_blocked",
    "public_routes_remain_public_where_intended",
    "admin_guard_returns_authoritative_actor",
    "admin_guard_rejects_suspended_actor",
    "admin_guard_rejects_role_mismatch",
    "admin_guard_rejects_missing_actor",
}


class Failure(Exception):
    pass


def expect(condition: bool, message: str) -> None:
    if not condition:
        raise Failure(message)


def read(relative: str) -> str:
    path = ROOT / relative
    expect(path.is_file(), f"missing file: {relative}")
    return path.read_text(encoding="utf-8")


def check_head() -> None:
    expected = os.getenv("ADM1_HEAD_SHA")
    if expected:
        actual = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        expect(actual == expected, f"not exact PR head: {actual} != {expected}")


def changed_files() -> list[str]:
    base, head = os.getenv("ADM1_BASE_SHA"), os.getenv("ADM1_HEAD_SHA")
    if not base or not head:
        return []
    out = subprocess.check_output(["git", "diff", "--name-only", base, head], cwd=ROOT, text=True)
    return [line for line in out.splitlines() if line]


def check_contract() -> None:
    data = json.loads(read("verification/admin/adm1/adm1-contract.json"))
    expect(data["work_start_sha"] == BASE and data["remote_main_sha"] == BASE, "baseline SHA mismatch")
    expect(data["status"] == "IMPLEMENTED_PENDING_USER_APPROVAL", "invalid status")
    for key, value in EXPECTED.items():
        expect(data["result"].get(key) == value, f"contract mismatch: {key}")
    reused = set(data["database_reuse"])
    for item in ["app_users.role", "app_users.account_status", "reports", "admin_actions", "jc_admin"]:
        expect(item in reused, f"reuse contract missing: {item}")


def check_source() -> None:
    role = read("jc-backend/src/main/java/com/jc/backend/database/DatabaseRole.java")
    verifier = read("jc-backend/src/main/java/com/jc/backend/database/DatabaseRoleCapabilityVerifier.java")
    auth = read("jc-backend/src/main/java/com/jc/backend/auth/AuthService.java")
    account = read("jc-backend/src/main/java/com/jc/backend/auth/AuthAccount.java")
    security = read("jc-backend/src/main/kotlin/com/jc/backend/config/SecurityConfig.kt")
    guard = read("jc-backend/src/main/java/com/jc/backend/admin/security/AdminAuthorizationGuard.java")
    actor = read("jc-backend/src/main/java/com/jc/backend/admin/security/AdminActor.java")
    runtime = read("database/journey-connect-db-v2.7/README_P0_3.md")

    required = [
        (role, 'ADMIN("jc_admin")'),
        (verifier, "DatabaseRole.ADMIN.sqlName()"),
        (auth, '.claim("role", user.getRole())'),
        (account, "String getRole()"),
        (account, "String getAccountStatus()"),
        (security, '"/api/admin"'),
        (security, '"/api/admin/**"'),
        (security, '.hasRole("ADMIN")'),
        (security, "JwtAuthenticationConverter"),
        (security, "ROLE_ADMIN"),
        (guard, "DatabaseRole.ADMIN"),
        (guard, "public.app_users"),
        (guard, "requestIdentity.currentUserId()"),
        (guard, "ADMIN_ACCESS_DENIED"),
        (guard, "ACTIVE_STATUS.equals(actor.accountStatus())"),
        (actor, "record AdminActor"),
        (runtime, "GRANT jc_app, jc_auth, jc_admin, jc_recommendation TO jc_backend;"),
        (runtime, "DatabaseRole.ADMIN"),
    ]
    for text, pattern in required:
        expect(pattern in text, f"source contract missing: {pattern}")
    expect("password" not in actor.lower(), "AdminActor exposes password material")


def check_tests() -> None:
    tests = read("jc-backend/src/test/java/com/jc/backend/admin/security/AdminSecurityIntegrationTest.java")
    routing = read("jc-backend/src/test/java/com/jc/backend/database/DatabaseRoleRoutingIntegrationTest.java")
    for name in TESTS:
        expect(re.search(rf"void\s+{re.escape(name)}\s*\(", tests), f"test missing: {name}")
    for pattern in ["@CanonicalPostgresTest", "token_subject_mismatch_is_rejected", "/api/admin/__test/access"]:
        expect(pattern in tests, f"integration evidence missing: {pattern}")
    for pattern in ["adminRoleReadsAuthorityStateButCannotReadPasswordOrMutateDirectly", "DatabaseRole.ADMIN", "password_hash"]:
        expect(pattern in routing, f"DB role evidence missing: {pattern}")


def check_scope() -> None:
    adm3_contract = ROOT / "verification/admin/adm3/adm3-contract.json"
    adm3_complete = False
    if adm3_contract.is_file():
        adm3 = json.loads(adm3_contract.read_text(encoding="utf-8"))
        adm3_complete = adm3.get("result", {}).get("ADM3_ADMIN_API_HARDENING_COMPLETE") == "YES"
    adm3_sql = {
        "database/journey-connect-db-v2.7/53_admin_control_plane_hardening.sql",
        "database/journey-connect-db-v2.7/54_admin_control_plane_hardening_smoke_test.sql",
        "jc-backend/src/test/resources/db/canonical/53_admin_control_plane_hardening.sql",
        "jc-backend/src/test/resources/db/canonical/54_admin_control_plane_hardening_smoke_test.sql",
    }

    forbidden = []
    for path in changed_files():
        lower = path.lower()
        sql_or_database = lower.endswith(".sql") or "/db/migration/" in lower or (
            lower.startswith("database/") and not lower.endswith(("readme.md", "readme_p0_3.md"))
        )
        if sql_or_database and not (adm3_complete and path in adm3_sql):
            forbidden.append(path)
        if "frontend" in lower:
            forbidden.append(path)
        if path == ".github/workflows/adm1-source-intake.yml":
            forbidden.append(path)
    expect(not forbidden, f"forbidden ADM-1 scope: {sorted(set(forbidden))}")

    admin_controllers = {
        str(path.relative_to(ROOT)).replace("\\", "/")
        for path in (ROOT / "jc-backend/src/main").rglob("*Controller*")
        if path.is_file() and "admin" in {part.lower() for part in path.parts}
    }
    if admin_controllers:
        adm2_contract_path = ROOT / "verification/admin/adm2/adm2-contract.json"
        expect(adm2_contract_path.is_file(), "production Admin controller exists without ADM-2 contract")
        adm2 = json.loads(adm2_contract_path.read_text(encoding="utf-8"))
        expect(
            adm2.get("result", {}).get("ADM2_ADMIN_BASIC_APIS_IMPLEMENTED") == "YES",
            "production Admin controller exists without completed ADM-2 contract",
        )
        expected_controllers = {
            "jc-backend/src/main/java/com/jc/backend/admin/AdminDashboardController.java",
            "jc-backend/src/main/java/com/jc/backend/admin/AdminReportController.java",
            "jc-backend/src/main/java/com/jc/backend/admin/AdminPostController.java",
            "jc-backend/src/main/java/com/jc/backend/admin/AdminUserController.java",
        }
        expect(
            admin_controllers == expected_controllers,
            f"unexpected ADM-2 Admin controllers: {sorted(admin_controllers ^ expected_controllers)}",
        )
        for service in [
            "AdminDashboardService.java",
            "AdminReportService.java",
            "AdminPostService.java",
            "AdminUserService.java",
        ]:
            source = read(f"jc-backend/src/main/java/com/jc/backend/admin/{service}")
            expect("requireActiveAdmin()" in source, f"ADM-2 guard reuse missing: {service}")
            expect("DatabaseRole.ADMIN" in source, f"ADM-2 DB role routing missing: {service}")


def check_docs_and_workflow() -> None:
    doc = read("docs/admin/adm1/ADM-1-ADMIN-DATABASE-SECURITY-FOUNDATION.md")
    read("docs/admin/adm1/README.md")
    handoff = read("docs/admin/adm1/ADM-2-ENTRY-GATE-AND-HANDOFF.md")
    workflow = read(".github/workflows/adm1-admin-security.yml")
    for value in ["ADM1_DATABASE_BASELINE_VERIFIED=YES", "ADMIN_AUTHORIZATION_SOURCE=DB_AUTHORITATIVE", "SQL_CHANGE=NONE", "FRONTEND_SOURCE_CHANGE=NO"]:
        expect(value in doc, f"documentation missing: {value}")
    expect("ADM2_ENTRY=BLOCKED_PENDING_USER_APPROVAL" in handoff, "ADM-2 gate is not blocked")
    for value in ["contract_and_scope:", "admin_postgres_security:", "independent_verifier:", "actions/checkout@v7", "AdminSecurityIntegrationTest", "verify_adm1.py --check all"]:
        expect(value in workflow, f"workflow contract missing: {value}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", choices=["all", "contract", "source", "tests", "scope", "docs"], default="all")
    args = parser.parse_args()
    check_head()
    checks = {
        "contract": check_contract,
        "source": check_source,
        "tests": check_tests,
        "scope": check_scope,
        "docs": check_docs_and_workflow,
    }
    selected = list(checks) if args.check == "all" else [args.check]
    for name in selected:
        checks[name]()
    evidence = {
        "schema_version": "adm1-verification-evidence-v1",
        "status": "PASS",
        "checked": selected,
        "head": os.getenv("ADM1_HEAD_SHA") or "LOCAL_STATIC",
        "contract_sha256": hashlib.sha256(CONTRACT.read_bytes()).hexdigest(),
        "sql_change": "NONE",
        "db_schema_change": "NONE",
    }
    EVIDENCE.parent.mkdir(parents=True, exist_ok=True)
    EVIDENCE.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(evidence))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ADM-1 verification failed: {exc}", file=sys.stderr)
        sys.exit(1)
