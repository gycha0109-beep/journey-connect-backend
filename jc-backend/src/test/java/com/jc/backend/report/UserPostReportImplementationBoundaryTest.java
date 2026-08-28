package com.jc.backend.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserPostReportImplementationBoundaryTest {

    @Test
    void runtimeReusesOnlyCanonicalAppRoleReportCommand() throws IOException {
        String service = read("jc-backend/src/main/java/com/jc/backend/report/UserReportService.java");
        String controller = read("jc-backend/src/main/java/com/jc/backend/report/UserReportController.java");
        String dtos = read("jc-backend/src/main/java/com/jc/backend/report/UserReportDtos.java");
        String runtime = service + controller + dtos;

        for (String required : new String[] {
                "@DatabaseTransactional(role = DatabaseRole.APP",
                "select public.submit_report('post', ?, ?, ?)",
                "REPORT_TARGET_NOT_FOUND",
                "REPORT_ALREADY_EXISTS",
                "USER_INACTIVE",
                "INVALID_REPORT_REASON",
                "@RequestMapping(\"/api/v1/posts\")",
                "@PostMapping(\"/{postId}/reports\")",
                "@ResponseStatus(HttpStatus.CREATED)"
        }) {
            assertTrue(runtime.contains(required), "PF9 runtime contract missing: " + required);
        }

        for (String reason : new String[] {
                "\"spam\"",
                "\"harassment\"",
                "\"hate\"",
                "\"sexual_content\"",
                "\"violence\"",
                "\"misinformation\"",
                "\"privacy\"",
                "\"copyright\"",
                "\"other\""
        }) {
            assertTrue(service.contains(reason), "PF9 reason vocabulary missing: " + reason);
        }

        for (String prohibited : new String[] {
                "insert into public.reports",
                "update public.reports",
                "delete from public.reports",
                "can_user_view_post(",
                "JourneyPostRepository",
                "Recommendation",
                "Search",
                "Notification",
                "AdminReportService",
                "WebSocket",
                "SseEmitter"
        }) {
            assertFalse(runtime.contains(prohibited), "PF9 crossed protected boundary: " + prohibited);
        }
    }

    @Test
    void canonicalSecurityAuthorityStillOwnsVisibilityEvidenceAndPrivileges() throws IOException {
        String security = read("database/journey-connect-db-v2.7/05_security_roles.sql");

        for (String required : new String[] {
                "CREATE OR REPLACE FUNCTION public.submit_report(",
                "v_reporter_id := public.require_active_user();",
                "public.can_user_view_post(v_reporter_id, p.id)",
                "p.author_id <> v_reporter_id",
                "target_snapshot, reason_category, reason_detail",
                "GRANT EXECUTE ON FUNCTION public.submit_report(varchar, bigint, varchar, varchar) TO jc_app;",
                "REVOKE INSERT, UPDATE, DELETE ON public.reports FROM jc_app, jc_auth, jc_admin;"
        }) {
            assertTrue(security.contains(required), "PF9 canonical security authority missing: " + required);
        }

        assertFalse(security.contains("GRANT INSERT ON public.reports TO jc_app"));
        assertFalse(security.contains("GRANT UPDATE ON public.reports TO jc_app"));
        assertFalse(security.contains("GRANT DELETE ON public.reports TO jc_app"));
    }

    @Test
    void protectedControllersAndSecurityConfigRemainSeparatedFromPf9ReportRuntime() throws IOException {
        String postController = read("jc-backend/src/main/java/com/jc/backend/post/PostController.java");
        String securityConfig = read("jc-backend/src/main/kotlin/com/jc/backend/config/SecurityConfig.kt");

        assertFalse(postController.contains("/{postId}/reports"));
        assertFalse(postController.contains("UserReportService"));
        assertFalse(securityConfig.contains("/api/v1/posts/*/reports"));
        assertTrue(securityConfig.contains(".anyRequest().authenticated()"));
    }

    @Test
    void pf9NoSqlBoundaryIsHistoricalAndPf10ExclusivelyOwnsSql69And70() throws IOException {
        String pf9 = read("docs/platform/governance/SC-PF9-USER-POST-REPORT-ALLOCATION.md");
        String pf10 = read("docs/platform/governance/SC-PF10-POST-LIKE-NOTIFICATION-ALLOCATION.md");
        assertTrue(pf9.contains("SQL_69_PLUS=UNALLOCATED"));
        assertTrue(pf10.contains("SQL_69=ALLOCATED"));
        assertTrue(pf10.contains("SQL_70=ALLOCATED"));
        assertTrue(pf10.contains("SQL_71_PLUS=UNALLOCATED"));

        Path production = repositoryRoot().resolve("database/journey-connect-db-v2.7");
        Path mirror = repositoryRoot().resolve("jc-backend/src/test/resources/db/canonical");
        assertTrue(Files.isRegularFile(production.resolve("69_post_like_notification_type.sql")));
        assertTrue(Files.isRegularFile(production.resolve("70_post_like_notification_type_smoke_test.sql")));
        assertTrue(Files.isRegularFile(mirror.resolve("69_post_like_notification_type.sql")));
        assertTrue(Files.isRegularFile(mirror.resolve("70_post_like_notification_type_smoke_test.sql")));
        try (var files = Files.list(production)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().matches("^71_.*\\.sql$")));
        }
        try (var files = Files.list(mirror)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().matches("^71_.*\\.sql$")));
        }
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("database/journey-connect-db-v2.7"))
                    && Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("repository root not found from " + current);
    }
}
