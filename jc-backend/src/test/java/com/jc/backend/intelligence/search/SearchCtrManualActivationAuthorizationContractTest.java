package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SearchCtrManualActivationAuthorizationContractTest {

    @Test
    void authorizationDocumentBindsOneStageWindowAndKeepsExecutionSeparate()
            throws IOException {
        String document = read(
                "docs/recommendation/SR-6F-G-SEARCH-CTR-NONPRODUCTION-MANUAL-ACTIVATION-AUTHORIZATION.md");

        for (String required : new String[] {
                "Authorized runtime mode: NONPRODUCTION_MANUAL",
                "Authorized environment: stage",
                "Authorized login role: jc_backend",
                "Authorized window start: 2026-08-06T08:00:00Z",
                "Authorized window end: 2026-08-06T09:00:00Z",
                "approval:sr6fg-stage-20260806t0800z",
                "Runner default: OFF",
                "Kill switch default: ON",
                "Membership grant: NOT_PERFORMED",
                "Manual execution: NOT_PERFORMED",
                "Finality write: DISABLED",
                "SR-6F-H: CONTROLLED_STAGE_ONE_SHOT_EXECUTION_AND_EVIDENCE"
        }) {
            assertTrue(document.contains(required), "authorization document missing: " + required);
        }
    }

    @Test
    void membershipProceduresAreExactFailClosedAndReversible() throws IOException {
        String grant = read("operations/search-ctr/sr6fg/01_grant_stage_reliability.sql");
        String verify = read("operations/search-ctr/sr6fg/02_verify_stage_reliability.sql");
        String revoke = read("operations/search-ctr/sr6fg/03_revoke_stage_reliability.sql");

        for (String script : new String[] {grant, verify, revoke}) {
            assertTrue(script.contains("\\set ON_ERROR_STOP on"));
            assertTrue(script.contains("sr6fg_environment"));
            assertTrue(script.contains("sr6fg_approval_ref"));
            assertTrue(script.contains("approval:sr6fg-stage-20260806t0800z"));
            assertTrue(script.contains("'stage'"));
            assertFalse(script.contains("production"));
        }

        assertTrue(grant.contains("GRANT jc_reliability TO jc_backend"));
        assertTrue(grant.contains("NOLOGIN contract"));
        assertTrue(verify.contains("SET LOCAL ROLE jc_reliability"));
        assertTrue(verify.contains("execute_search_ctr_manual_v1"));
        assertTrue(revoke.contains("REVOKE jc_reliability FROM jc_backend"));
        assertTrue(revoke.contains("membership revoke verification failed"));
    }

    @Test
    void authorizationDoesNotAddSchedulerEndpointOrFinalityWriter() throws IOException {
        String policy = read(
                "jc-backend/src/main/java/com/jc/backend/intelligence/search/SearchCtrActivationPolicy.java");
        String configuration = read(
                "jc-backend/src/main/java/com/jc/backend/intelligence/search/SearchCtrManualActivationConfiguration.java");

        assertTrue(policy.contains("RuntimeMode.NONPRODUCTION_MANUAL"));
        assertTrue(policy.contains("isFinalityWriteAuthorized()"));
        assertTrue(policy.contains("return false;"));
        assertFalse(configuration.contains("@Scheduled"));
        assertFalse(configuration.contains("@RestController"));
        assertFalse(configuration.contains("@Controller"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("docs/recommendation"))
                    && Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("repository root not found from " + current);
    }
}
