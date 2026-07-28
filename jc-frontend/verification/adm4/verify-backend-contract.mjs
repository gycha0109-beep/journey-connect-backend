import fs from "node:fs";
import path from "node:path";

const backend = path.resolve(process.argv[2] || "../../backend-contract");
const read = (relative) => fs.readFileSync(path.join(backend, relative), "utf8");
const base = "jc-backend/src/main/java/com/jc/backend";
const controllers = [
  ["admin/AdminDashboardController.java", ["/api/admin/dashboard", "@GetMapping"]],
  ["admin/AdminReportController.java", ["/api/admin/reports", "@GetMapping", "/resolve", "/dismiss"]],
  ["admin/AdminPostController.java", ["/api/admin/posts", "@GetMapping", "/hide", "/restore"]],
  ["admin/AdminUserController.java", ["/api/admin/users", "@GetMapping", "/suspend", "/unsuspend"]]
];
for (const [file, tokens] of controllers) {
  const source = read(`${base}/${file}`);
  for (const token of tokens) if (!source.includes(token)) throw new Error(`${file} missing ${token}`);
}
const dto = read(`${base}/admin/AdminDtos.java`);
for (const token of ["totalUsers", "activePostCount", "pendingReportCount", "suspendedUserCount", "recentReports", "recentAdminActions", "CommandRequest", "CommandResult", "canResolve", "canDismiss", "moderationStatus", "accountStatus"]) {
  if (!dto.includes(token)) throw new Error(`AdminDtos missing ${token}`);
}
const security = read("jc-backend/src/main/kotlin/com/jc/backend/config/SecurityConfig.kt");
if (!security.includes('.requestMatchers("/api/admin", "/api/admin/**").hasRole("ADMIN")')) throw new Error("admin security matcher missing");
console.log("ADM4_BACKEND_SOURCE_CONTRACT_SMOKE=PASS");
