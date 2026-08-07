package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SearchCtrStageOneShotExecutionContractTest {

    @Test
    void executionContractRemainsBlockedUntilAnEndpointFingerprintIsApproved() throws IOException {
        String contract = read("operations/search-ctr/sr6fh/stage-execution-contract.env");

        for (String required : new String[] {
                "SR6FH_EXECUTION_STATUS=BLOCKED_EXTERNAL_STAGE_ACCESS",
                "SR6FH_AUTHORIZED_ENVIRONMENT=stage",
                "SR6FH_AUTHORIZED_LOGIN_ROLE=jc_backend",
                "SR6FH_AUTHORIZED_WINDOW_START=2026-08-06T08:00:00Z",
                "SR6FH_AUTHORIZED_WINDOW_END=2026-08-06T09:00:00Z",
                "SR6FH_AUTHORIZED_APPROVAL_REF=approval:sr6fg-stage-20260806t0800z",
                "SR6FH_AUTHORIZED_STAGE_ENDPOINT_SHA256=UNASSIGNED",
                "SR6FH_REQUIRED_GITHUB_ENVIRONMENT=stage",
                "SR6FH_FINALITY_WRITE=DISABLED"
        }) {
            assertTrue(contract.contains(required), "stage contract missing: " + required);
        }
        assertFalse(contract.contains("postgresql://"));
        assertFalse(contract.contains("jdbc:postgresql://"));
        assertFalse(contract.toLowerCase().contains("password="));
    }

    @Test
    void workflowIsManualStageProtectedAndHasNoAutomaticTrigger() throws IOException {
        String workflow = read(".github/workflows/sr-search-ctr-stage-one-shot.yml");

        assertTrue(workflow.contains("workflow_dispatch:"));
        assertTrue(workflow.contains("environment: stage"));
        assertTrue(workflow.contains("cancel-in-progress: false"));
        assertTrue(workflow.contains("execute_stage_one_shot.sh"));
        assertTrue(workflow.contains("SR6FH_STAGE_ADMIN_DATABASE_URL"));
        assertTrue(workflow.contains("SR6FH_STAGE_BACKEND_JDBC_URL"));
        assertFalse(workflow.contains("\npull_request:"));
        assertFalse(workflow.contains("\npush:"));
        assertFalse(workflow.contains("\nschedule:"));
        assertFalse(workflow.contains("cron:"));
    }

    @Test
    void orchestrationGuaranteesRevokeAndDoesNotEnableShellTracing() throws IOException {
        String script = read("operations/search-ctr/sr6fh/execute_stage_one_shot.sh");

        for (String required : new String[] {
                "trap cleanup EXIT",
                "trap 'exit 130' INT",
                "trap 'exit 143' TERM",
                "revoke_membership",
                "03_revoke_stage_reliability.sql",
                "05_verify_stage_revoked.sql",
                "SET ROLE jc_reliability;",
                "SR6FH_EXECUTION_STATUS\" == \"READY_FOR_ONE_SHOT",
                "SR6FH_FINALITY_WRITE\" == \"DISABLED",
                "searchCtrStageOneShot --no-build-cache"
        }) {
            assertTrue(script.contains(required), "one-shot script missing: " + required);
        }
        assertFalse(script.contains("set -x"));
        assertFalse(script.contains("curl "));
        assertFalse(script.contains("--schedule"));
    }

    @Test
    void uploadedEvidenceIsIdentityFreeAndEndpointRedacted() throws IOException {
        String evidence = read("operations/search-ctr/sr6fh/04_collect_stage_evidence.sql");
        String endpointValidator = read("operations/search-ctr/sr6fh/validate_stage_endpoints.py");
        String sanitizer = read("operations/search-ctr/sr6fh/sanitize_stage_evidence.py");

        for (String required : new String[] {
                "operationId", "writeStatus", "projectionId", "projectionFingerprint",
                "eligibleExposureCount", "attributedExposureCount", "ctrBasisPoints",
                "sourceMaxReceivedAt", "finalityWriteAttempted"
        }) {
            assertTrue(evidence.contains(required), "stage evidence missing: " + required);
        }
        for (String prohibited : new String[] {
                "user_id", "subject_ref", "session_id", "exposure_id",
                "click_event_id", "raw_query"
        }) {
            assertFalse(evidence.contains(prohibited), "stage evidence leaks: " + prohibited);
        }
        assertTrue(endpointValidator.contains("endpointFingerprint"));
        assertFalse(endpointValidator.contains("print(admin_url)"));
        assertFalse(endpointValidator.contains("print(jdbc_url)"));
        assertTrue(sanitizer.contains("<redacted-database-endpoint>"));
        assertTrue(sanitizer.contains("runtime-db.env"));
    }

    @Test
    void dedicatedApplicationIsNonWebAndClosesItsContext() throws IOException {
        String application = read(
                "operations/search-ctr/sr6fh/java/com/jc/backend/intelligence/search/"
                        + "SearchCtrStageOneShotApplication.java");

        assertTrue(application.contains("WebApplicationType.NONE"));
        assertTrue(application.contains("try (ConfigurableApplicationContext"));
        String initScript = read("operations/search-ctr/sr6fh/stage-one-shot.init.gradle");
        assertTrue(initScript.contains("compileSearchCtrStageOneShot"));
        assertTrue(initScript.contains("searchCtrStageOneShot"));
        assertFalse(application.contains("System.exit"));
    }

    @Test
    void executionDocumentsDoNotClaimStageSuccess() throws IOException {
        String implementation = read(
                "docs/recommendation/SR-6F-H-SEARCH-CTR-CONTROLLED-STAGE-ONE-SHOT-EXECUTION.md");
        String status = read(
                "docs/platform/system/SR-6F-H-SEARCH-CTR-STAGE-EXECUTION-STATUS.md");

        assertTrue(implementation.contains("BLOCKED_EXTERNAL_STAGE_ACCESS"));
        assertTrue(implementation.contains("Actual stage execution: NOT_PERFORMED"));
        assertTrue(implementation.contains("IMPLEMENTED_EXECUTION_CONTROL_HOLD_EXTERNAL_STAGE_ACCESS"));
        assertTrue(status.contains("Actual external stage mutation: NOT_POSSIBLE_FROM_CURRENT_EVIDENCE"));
        assertTrue(status.contains("Finality write: NOT_AUTHORIZED"));
        assertFalse(status.contains("Actual stage execution: PASS"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("operations/search-ctr"))
                    && Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("repository root not found from " + current);
    }
}
