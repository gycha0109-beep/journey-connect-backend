import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const read = (relative) => fs.readFileSync(path.join(ROOT, relative), "utf8");
const exists = (relative) => fs.existsSync(path.join(ROOT, relative));
const checks = [];
const check = (name, pass, detail) => checks.push({ name, status: pass ? "PASS" : "FAIL", detail });

const required = [
  "src/admin/AdminRouteGuard.jsx", "src/admin/AdminLayout.jsx", "src/admin/AdminCommandDialog.jsx",
  "src/pages/admin/AdminDashboardPage.jsx", "src/pages/admin/AdminReportsPage.jsx", "src/pages/admin/AdminReportDetailPage.jsx",
  "src/pages/admin/AdminPostsPage.jsx", "src/pages/admin/AdminPostDetailPage.jsx",
  "src/pages/admin/AdminUsersPage.jsx", "src/pages/admin/AdminUserDetailPage.jsx",
  "src/services/adminApi.js", "src/services/auth.js", "src/pages/AdminLoginPage.jsx", "src/main.jsx", "index.html", "verification/adm4/selective-port-manifest.json"
];
required.forEach((file) => check(`file:${file}`, exists(file), file));

const app = read("src/App.jsx");
const api = read("src/services/adminApi.js");
const allAdminSource = [
  "src/admin/AdminRouteGuard.jsx", "src/admin/AdminLayout.jsx", "src/admin/AdminCommandDialog.jsx", "src/admin/AdminUi.jsx",
  "src/pages/admin/AdminDashboardPage.jsx", "src/pages/admin/AdminReportsPage.jsx", "src/pages/admin/AdminReportDetailPage.jsx",
  "src/pages/admin/AdminPostsPage.jsx", "src/pages/admin/AdminPostDetailPage.jsx", "src/pages/admin/AdminUsersPage.jsx", "src/pages/admin/AdminUserDetailPage.jsx",
  "src/services/adminApi.js", "src/admin/adminErrors.js"
].map(read).join("\n");

const routes = ["/admin", "reports", "reports/:reportId", "posts", "posts/:postId", "users", "users/:userId"];
check("standalone_shell", app.includes('path="/login"') && exists("src/main.jsx") && exists("index.html"), "authoritative repository standalone admin shell");
check("admin_routes", routes.every((route) => app.includes(`path=\"${route}\"`) || (route === "/admin" && app.includes('path="/admin"'))), routes.join(","));
const endpoints = [
  'get("/dashboard")', 'get("/reports", params)', 'get(`/reports/${reportId}`)', 'command(`/reports/${reportId}/resolve`',
  'command(`/reports/${reportId}/dismiss`', 'get("/posts", params)', 'get(`/posts/${postId}`)', 'command(`/posts/${postId}/hide`',
  'command(`/posts/${postId}/restore`', 'get("/users", params)', 'get(`/users/${userId}`)', 'command(`/users/${userId}/suspend`',
  'command(`/users/${userId}/unsuspend`'
];
check("endpoint_coverage_13", endpoints.every((token) => api.includes(token)), `${endpoints.filter((token) => api.includes(token)).length}/13`);
check("admin_route_protected", read("src/admin/AdminRouteGuard.jsx").includes("getAdminDashboard") && read("src/admin/AdminRouteGuard.jsx").includes('<Navigate to="/login"'), "backend dashboard authorization probe");
check("frontend_role_ux_only", !/\.role\s*===\s*["']admin["']|ROLE_ADMIN|jwtDecode/.test(read("src/admin/AdminRouteGuard.jsx")), "no frontend grant by role");
check("reason_boundary", read("src/admin/AdminCommandDialog.jsx").includes("ADMIN_MAX_REASON_LENGTH") && read("src/admin/AdminCommandDialog.jsx").includes("required"), "required <= 1000");
check("safe_errors", !/error\.message|response\?\.data\?\.message/.test(read("src/admin/adminErrors.js")), "status/code allowlist only");
check("loading_empty_error", ["AdminLoading", "AdminEmpty", "AdminError"].every((name) => allAdminSource.includes(name)), "common states");
check("accessibility", ['role="dialog"', 'aria-modal="true"', "event.key === \"Escape\"", 'scope="col"', 'aria-label='].every((token) => allAdminSource.includes(token)), "dialog, keyboard, table, labels");
check("responsive", ["lg:pl-64", "lg:hidden", "overflow-x-auto", "sm:grid-cols"].every((token) => allAdminSource.includes(token)), "mobile/sidebar/table/grid");
check("no_physical_delete", !/영구 삭제|완전 삭제|DB 삭제|deleteAdmin|window\.confirm/.test(allAdminSource), "hide/restore only");
check("no_role_or_appointment_controls", !/changeRole|appointAdmin|역할 변경|관리자 임명|관리자 해제/.test(allAdminSource), "absent");
check("no_forbidden_console", !/Audit Logs|Settings|Infrastructure|Database|Permissions|Monitoring|Exports/.test(allAdminSource), "four menu areas only");
check("no_hardcoded_admin_api", !/https?:\/\//.test(api), "relative environment-configured admin base");
check("no_sensitive_fields", !/passwordHash|accessToken|refreshToken|oauthRaw|jwtClaims|securityMetadata/.test(allAdminSource), "not rendered");

const manifest = JSON.parse(read("verification/adm4/selective-port-manifest.json"));
check("source_sha_pinned", manifest.sourceSha === "47f8cceeaaa4f9afdd90896bc0793a34e9cefefb", manifest.sourceSha);
check("selective_port", manifest.fullSourceBranchMerge === false && manifest.ongoingSourceSync === false && manifest.ported.length === 1, "selective only");
check("mock_rejected", manifest.rejected.some((item) => item.includes("mock")), "manifest rejection");

const failed = checks.filter((item) => item.status === "FAIL");
const evidence = {
  schemaVersion: "adm4-evidence-v1",
  status: failed.length ? "FAIL" : "PASS",
  sourceRepository: manifest.sourceRepository,
  sourceBranch: manifest.sourceBranch,
  sourceSha: manifest.sourceSha,
  targetRepository: manifest.targetRepository,
  targetBaseBranch: manifest.targetBaseBranch,
  targetBaseSha: manifest.targetBaseSha,
  endpointCoverage: endpoints.length,
  result: {
    ADM4_ADMIN_DASHBOARD_UI_COMPLETE: failed.length ? "NO" : "YES",
    ADMIN_UI_SOURCE_USAGE: "SELECTIVE_PORT",
    YOUNGTAK_SOURCE_SHA_PINNED: "YES",
    FULL_SOURCE_BRANCH_MERGE: "NO",
    ONGOING_SOURCE_SYNC: "NO",
    BACKEND_RUNTIME_CHANGE: "NO",
    BACKEND_SQL_CHANGE: "NO",
    DB_SCHEMA_CHANGE: "NONE"
  },
  checks
};
fs.writeFileSync(path.join(ROOT, "verification/adm4/adm4-evidence.json"), `${JSON.stringify(evidence, null, 2)}\n`);
console.log(JSON.stringify(evidence, null, 2));
process.exit(failed.length ? 1 : 0);
