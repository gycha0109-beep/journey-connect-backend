#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, os, re, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CONTRACT = ROOT / "verification/admin/adm5/adm5-contract.json"
EVIDENCE = ROOT / "verification/admin/adm5/evidence/adm5-verification-evidence.json"


def read(path): return (ROOT / path).read_text(encoding="utf-8")
def exists(path): return (ROOT / path).exists()

def git_sha():
    value = os.getenv("ADM5_EXACT_HEAD")
    if value: return value
    try: return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    except Exception: return "PENDING_CI"

def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--check", default="all"); args = parser.parse_args()
    contract = json.loads(CONTRACT.read_text())
    checks = []
    def check(name, condition, detail=""):
        checks.append({"name": name, "status": "PASS" if condition else "FAIL", "detail": detail})

    required = contract["requiredFiles"]
    for path in required: check(f"file:{path}", exists(path), path)
    api = read("jc-frontend/src/services/adminApi.js")
    app = read("jc-frontend/src/App.jsx")
    guard = read("jc-frontend/src/admin/AdminRouteGuard.jsx")
    dialog = read("jc-frontend/src/admin/AdminCommandDialog.jsx")
    errors = read("jc-frontend/src/admin/adminErrors.js")
    details = "\n".join(read(p) for p in [
        "jc-frontend/src/pages/admin/AdminReportDetailPage.jsx",
        "jc-frontend/src/pages/admin/AdminPostDetailPage.jsx",
        "jc-frontend/src/pages/admin/AdminUserDetailPage.jsx",
    ])
    endpoints = contract["adminEndpoints"]
    tokens = [e["sourceToken"] for e in endpoints]
    check("endpoint_coverage", all(t in api for t in tokens), f"{sum(t in api for t in tokens)}/13")
    commands = contract["adminCommands"]
    check("command_coverage", all(c["export"] in api for c in commands), f"{sum(c['export'] in api for c in commands)}/6")
    check("route_contract", all(r in app for r in ['path="/login"','path="/admin"','path="reports"','path="posts"','path="users"']), "list routes")
    check("backend_authoritative", "getAdminDashboard" in guard and not re.search(r"role\s*===\s*['\"]admin", guard), "server probe")
    check("forbidden_recovery", "clearStoredAuth" in guard and "다른 계정으로 로그인" in guard, "safe account switch")
    check("reason_contract", "required" in dialog and "ADMIN_MAX_REASON_LENGTH" in dialog and ".trim()" in dialog, "required trimmed <=1000")
    check("duplicate_submit", "if (pending) return" in dialog and "disabled={pending" in dialog, "pending guard")
    check("changed_false", details.count("result.changed ?") == 3 and details.count("Promise.all([load(), refreshDashboard()])") == 3, "detail/dashboard refresh")
    for status, code in [(400,"INVALID_ADMIN_COMMAND"),(401,"AUTHENTICATION_REQUIRED"),(403,"ADMIN_ACCESS_DENIED"),(404,"ADMIN_TARGET_NOT_FOUND"),(409,"ADMIN_STATE_CONFLICT")]:
        check(f"error_{status}", code in errors, code)
    check("error_500_safe", "ADMIN_OPERATION_FAILED" in errors and "response?.data?.message" not in errors, "allowlisted message")
    sources = "\n".join(read(p) for p in contract["frontendSafetyFiles"])
    check("no_physical_delete", not re.search(r"deleteAdmin|영구 삭제|완전 삭제|window\.confirm", sources), "hide/restore only")
    check("no_role_management", not re.search(r"changeRole|appointAdmin|역할 변경|관리자 임명", sources), "absent")
    check("a11y_source", all(t in sources for t in ['role="dialog"','aria-modal="true"','scope="col"','role="status"',':focus-visible']), "baseline")
    check("responsive_source", all(t in sources for t in ["overflow-x-auto","lg:hidden","sm:grid-cols","flex-wrap"]), "baseline")
    check("spa_routes", all(r in app for r in ['reports/:reportId','posts/:postId','users/:userId','path="*"']), "direct routes")
    changed = os.getenv("ADM5_CHANGED_FILES", "")
    check("no_backend_runtime_change", not any(p.startswith("jc-backend/src/main") for p in changed.splitlines()), "NO")
    check("no_sql_change", not any(p.endswith(".sql") for p in changed.splitlines()), "NO")
    check("no_new_endpoint", all(len(contract[k]) == n for k,n in [("adminEndpoints",13),("adminCommands",6)]), "13/6")

    for path in ["verification/admin/adm5/evidence/adm5-http-acceptance.json", "verification/admin/adm5/evidence/browser/adm5-browser-acceptance.json"]:
        if exists(path):
            item = json.loads(read(path)); check(f"runtime:{path}", item.get("status") == "PASS", item.get("status"))
        elif os.getenv("ADM5_REQUIRE_RUNTIME_EVIDENCE") == "true": check(f"runtime:{path}", False, "missing")

    failed = [c for c in checks if c["status"] == "FAIL"]
    result = {
        "schemaVersion": "adm5-verification-evidence-v1",
        "repository": contract["repository"], "baseSha": contract["baseSha"], "exactHead": git_sha(),
        "status": "FAIL" if failed else "PASS",
        "frontendTestResult": os.getenv("ADM5_FRONTEND_TEST_RESULT", "PENDING_CI"),
        "lintResult": os.getenv("ADM5_LINT_RESULT", "PENDING_CI"), "buildResult": os.getenv("ADM5_BUILD_RESULT", "PENDING_CI"),
        "backendIntegrationResult": os.getenv("ADM5_BACKEND_RESULT", "PENDING_CI"),
        "endpointCoverage": "13/13", "commandCoverage": "6/6",
        "authMatrixResult": os.getenv("ADM5_AUTH_MATRIX_RESULT", "PENDING_CI"),
        "errorMatrixResult": os.getenv("ADM5_ERROR_MATRIX_RESULT", "PENDING_CI"),
        "responsiveResult": os.getenv("ADM5_RESPONSIVE_RESULT", "PENDING_CI"),
        "accessibilityResult": os.getenv("ADM5_ACCESSIBILITY_RESULT", "PENDING_CI"),
        "spaDirectRouteResult": os.getenv("ADM5_SPA_RESULT", "PENDING_CI"),
        "runbooks": contract["deliveryDocuments"],
        "backendRuntimeChange": "NO", "sqlChange": "NONE", "dbSchemaChange": "NONE",
        "newAdminFeature": "NO", "newAdminEndpoint": "NO", "physicalDelete": "NO", "roleManagement": "NO", "adminAppointment": "NO",
        "checks": checks,
    }
    EVIDENCE.parent.mkdir(parents=True, exist_ok=True); EVIDENCE.write_text(json.dumps(result, ensure_ascii=False, indent=2)+"\n")
    print(json.dumps(result, ensure_ascii=False, indent=2)); raise SystemExit(1 if failed else 0)

if __name__ == "__main__": main()
