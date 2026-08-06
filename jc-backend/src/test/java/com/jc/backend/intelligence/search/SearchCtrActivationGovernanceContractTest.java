package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SearchCtrActivationGovernanceContractTest {

    @Test
    void governanceDocumentPreservesThePreAuthorizationDecisionHistory() throws IOException {
        String document = read("docs/recommendation/SR-6F-E-SEARCH-CTR-ACTIVATION-FINALITY-GOVERNANCE.md");

        for (String required : new String[] {
                "Runtime mode: DISABLED",
                "Finality write: DISABLED",
                "NONPRODUCTION_MANUAL",
                "windowEnd + 35 minutes",
                "windowEnd + 30 days 35 minutes",
                "identity-free projection head read boundary",
                "append-only operational run audit",
                "existing snapshot mutation: forbidden",
                "SR-6F-F: NONPRODUCTION_MANUAL_ACTIVATION_FOUNDATION"
        }) {
            assertTrue(document.contains(required), "governance document missing: " + required);
        }
    }

    @Test
    void authorizationPolicyIsBoundedAndStillDoesNotExposeRuntimeEntryPoints() throws IOException {
        String policy = read(
                "jc-backend/src/main/java/com/jc/backend/intelligence/search/SearchCtrActivationPolicy.java");

        assertTrue(policy.contains(
                "AUTHORIZED_RUNTIME_MODE = RuntimeMode.NONPRODUCTION_MANUAL"));
        assertTrue(policy.contains("AUTHORIZED_MANUAL_ENVIRONMENT = \"stage\""));
        assertTrue(policy.contains(
                "Instant.parse(\"2026-08-06T08:00:00Z\")"));
        assertTrue(policy.contains(
                "approval:sr6fg-stage-20260806t0800z"));
        assertTrue(policy.contains("return false;"));
        assertFalse(policy.contains("@Scheduled"));
        assertFalse(policy.contains("@RestController"));
        assertFalse(policy.contains("@Controller"));
        assertFalse(policy.contains("@Service"));
        assertFalse(policy.contains("@Component"));
    }

    @Test
    void currentMetricContractStillAuthorizesProvisionalOnly() throws IOException {
        String contract = read(
                "jc-backend/src/main/java/com/jc/backend/intelligence/search/SearchCtrContract.java");

        assertTrue(contract.contains("PROVISIONAL_STATUS"));
        assertFalse(contract.contains("SETTLED_STATUS"));
        assertFalse(contract.contains("SUPERSEDED_STATUS"));
    }

    @Test
    void workflowCoversTheStackedGovernanceFoundationAndAuthorizationBranches() throws IOException {
        String workflow = read(".github/workflows/sr-search-recommendation.yml");

        assertTrue(workflow.contains("agent/sr6fd-search-ctr-projection-writer"));
        assertTrue(workflow.contains("agent/sr6fe-search-ctr-activation-finality-governance"));
        assertTrue(workflow.contains("agent/sr6ff-search-ctr-nonprod-manual-foundation"));
        assertTrue(workflow.contains("Run SR-0 to SR-6F-G focused tests"));
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
