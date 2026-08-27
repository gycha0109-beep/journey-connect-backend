package com.jc.backend.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserPostReportAllocationContractTest {

    private static final String DOCUMENT =
            "docs/platform/governance/SC-PF9-USER-POST-REPORT-ALLOCATION.md";

    @Test
    void allocationLocksExistingCanonicalReportCommandAndNoSqlBoundary() throws IOException {
        String allocation = read(DOCUMENT);

        for (String required : new String[] {
                "sc-pf9-user-post-report-v1",
                "APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED",
                "CONTRACT_ID=user-post-report-v1",
                "ENDPOINT=POST /api/v1/posts/{postId}/reports",
                "AUTHENTICATION=REQUIRED",
                "TARGET_TYPE=post",
                "WRITE_ROLE=APP",
                "DATABASE_COMMAND=public.submit_report(varchar,bigint,varchar,varchar)",
                "DIRECT_REPORT_TABLE_WRITE=FORBIDDEN",
                "NEW_SQL=NONE",
                "SQL_69_PLUS=UNALLOCATED",
                "REPORT_TARGET_NOT_FOUND",
                "REPORT_ALREADY_EXISTS",
                "INVALID_REPORT_REASON"
        }) {
            assertTrue(allocation.contains(required), "PF9 allocation missing: " + required);
        }
    }

    @Test
    void allocationPreservesCanonicalReasonAndReportabilitySemantics() throws IOException {
        String allocation = read(DOCUMENT);

        for (String reason : new String[] {
                "`spam`",
                "`harassment`",
                "`hate`",
                "`sexual_content`",
                "`violence`",
                "`misinformation`",
                "`privacy`",
                "`copyright`",
                "`other`"
        }) {
            assertTrue(allocation.contains(reason), "PF9 canonical reason missing: " + reason);
        }

        assertTrue(allocation.contains("public.can_user_view_post(...)"));
        assertTrue(allocation.contains("reports_open_target_uq"));
        assertTrue(allocation.contains("terminal (`resolved` or `rejected`)"));
        assertTrue(allocation.contains("one stable `REPORT_TARGET_NOT_FOUND` response"));
    }

    @Test
    void runtimeAllocationIsNarrowAndProtectedSurfacesStayExcluded() throws IOException {
        String allocation = read(DOCUMENT);

        assertTrue(allocation.contains("UserReportController"));
        assertTrue(allocation.contains("UserReportDtos"));
        assertTrue(allocation.contains("UserReportService"));
        assertTrue(allocation.contains("@DatabaseTransactional(role = DatabaseRole.APP)"));

        for (String excluded : new String[] {
                "`PostController`",
                "`PostService`",
                "`AdminReportController`",
                "`AdminReportService`",
                "`SecurityConfig`",
                "Recommendation runtime or persistence",
                "Search runtime",
                "Notification runtime"
        }) {
            assertTrue(allocation.contains(excluded), "PF9 excluded surface missing: " + excluded);
        }

        assertFalse(allocation.contains("SQL_69_PLUS=ALLOCATED"));
        assertFalse(allocation.contains("DIRECT_REPORT_TABLE_WRITE=ALLOWED"));
        assertFalse(allocation.contains("REPORT_NOTIFICATION=YES"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("docs/platform/governance"))
                    && Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("repository root not found from " + current);
    }
}
