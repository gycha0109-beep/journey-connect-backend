package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchCtrStageReadinessHandoffContractTest {

    @Test
    void manifestRemainsPlatformNeutralBlockedAndSecretsFree() throws IOException {
        String manifest = read("operations/search-ctr/sr6fi/stage-readiness-manifest.env.example");

        for (String required : new String[] {
                "SR6FI_CONTRACT_VERSION=search-ctr-stage-readiness-handoff-v1",
                "SR6FI_READINESS_STATUS=BLOCKED_PLATFORM_UNDECIDED",
                "SR6FI_FINAL_DEPLOYMENT_PLATFORM=UNDECIDED",
                "SR6FI_PLATFORM_DECISION_REFERENCE=verification/sc-next-track/op3-entry/"
                        + "sc-op3-required-input-decision-matrix.json",
                "SR6FI_RESOURCE_CREATION_AUTHORIZED=NO",
                "SR6FI_BILLING_SPEND_AUTHORIZED=NO",
                "SR6FI_IAM_MUTATION_AUTHORIZED=NO",
                "SR6FI_STAGE_ENDPOINT_SHA256=UNASSIGNED",
                "SR6FI_STAGE_TRAFFIC_PERCENT=0",
                "SR6FI_PRODUCTION_TRAFFIC_PERCENT=0",
                "SR6FI_CANDIDATE_SERVING=FORBIDDEN",
                "SR6FI_FINALITY_WRITE=DISABLED",
                "SR6FI_SR6FH_CONTRACT_STATUS=BLOCKED_EXTERNAL_STAGE_ACCESS"
        }) {
            assertTrue(manifest.contains(required), "readiness manifest missing: " + required);
        }
        for (String prohibited : new String[] {
                "postgresql://", "jdbc:postgresql://", "https://", "http://",
                "password=", "token=", "secret=", "GCP_CLOUD_RUN", "AWS_ECS_FARGATE"
        }) {
            assertFalse(manifest.toLowerCase().contains(prohibited.toLowerCase()),
                    "readiness manifest must not contain: " + prohibited);
        }
    }

    @Test
    void verifierUsesAuthoritativeMatrixAndFailsClosed() throws IOException {
        String verifier = read("operations/search-ctr/sr6fi/verify_stage_readiness.py");

        for (String required : new String[] {
                "MATRIX_REFERENCE = \"verification/sc-next-track/op3-entry/"
                        + "sc-op3-required-input-decision-matrix.json\"",
                "BLOCKED_STATUS = \"BLOCKED_PLATFORM_UNDECIDED\"",
                "READY_STATUS = \"READY_FOR_SR6FH_BINDING\"",
                "cloud_resource_creation_authorized",
                "billing_spend_authorized",
                "iam_mutation_authorized",
                "source_db_package_sha256",
                "reject_secret_material",
                "authoritative platform matrix still blocks readiness",
                "deployed DB package digest does not match reviewed source package"
        }) {
            assertTrue(verifier.contains(required), "readiness verifier missing: " + required);
        }
        for (String prohibited : new String[] {
                "subprocess.run(['gcloud'", "subprocess.run(['aws'", "terraform apply",
                "curl ", "requests.get(", "requests.post("
        }) {
            assertFalse(verifier.contains(prohibited), "readiness verifier executes external mutation: " + prohibited);
        }
    }

    @Test
    void secretInventoryContainsNamesOnlyAndNoDuplicate() throws IOException {
        List<String> lines = read("operations/search-ctr/sr6fi/required-secret-names.txt")
                .lines()
                .filter(line -> !line.isBlank())
                .toList();

        assertTrue(lines.contains("SR6FH_STAGE_ADMIN_DATABASE_URL"));
        assertTrue(lines.contains("SR6FH_STAGE_BACKEND_JDBC_URL"));
        assertTrue(lines.contains("SR6FH_STAGE_JWT_SECRET"));
        assertTrue(lines.stream().allMatch(line -> line.matches("SR6FH_[A-Z0-9_]+")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("=")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("://")));
        assertTrue(lines.stream().noneMatch(line -> line.toLowerCase().contains("password=")));
        assertTrue(new HashSet<>(lines).size() == lines.size());
    }

    @Test
    void bindingRendererIsReviewOnlyAndPreservesFinalityBoundary() throws IOException {
        String renderer = read("operations/search-ctr/sr6fi/render_sr6fh_binding.py");

        for (String required : new String[] {
                "SR6FH_EXECUTION_STATUS=READY_FOR_ONE_SHOT",
                "SR6FH_BINDING_REVIEW_STATUS=PROPOSED_NOT_AUTHORIZED",
                "SR6FH_FINALITY_WRITE=DISABLED",
                "current SR-6F-H contract is not in the expected blocked state",
                "SR6FH_AUTHORIZED_STAGE_ENDPOINT_SHA256=UNASSIGNED"
        }) {
            assertTrue(renderer.contains(required), "binding renderer missing: " + required);
        }
        assertFalse(renderer.contains("write_text(contract_text"));
        assertFalse(renderer.contains("stage-execution-contract.env\").write_text"));
        assertFalse(renderer.contains("subprocess"));
    }

    @Test
    void currentOp3AuthorityStillBlocksPlatformSelection() throws IOException {
        String matrix = read("verification/sc-next-track/op3-entry/"
                + "sc-op3-required-input-decision-matrix.json");

        assertTrue(matrix.contains("\"decision_status\": \"DEFERRED_PLATFORM_UNDECIDED\""));
        assertTrue(matrix.contains("\"final_deployment_platform\": \"UNDECIDED\""));
        assertTrue(matrix.contains("\"deployment_implementation\": \"DEFERRED\""));
        assertTrue(matrix.contains("\"cloud_resource_creation_authorized\": false"));
        assertTrue(matrix.contains("\"billing_spend_authorized\": false"));
        assertTrue(matrix.contains("\"iam_mutation_authorized\": false"));
        assertTrue(matrix.contains("\"paid_cloud_usage\": \"FORBIDDEN\""));
    }

    @Test
    void documentsDoNotClaimPlatformOrStageExecution() throws IOException {
        String implementation = read("docs/recommendation/"
                + "SR-6F-I-SEARCH-CTR-PLATFORM-NEUTRAL-STAGE-READINESS-HANDOFF.md");
        String status = read("docs/platform/system/"
                + "SR-6F-I-SEARCH-CTR-EXTERNAL-READINESS-STATUS.md");

        assertTrue(implementation.contains("Readiness status: BLOCKED_PLATFORM_UNDECIDED"));
        assertTrue(implementation.contains("Actual stage execution: NOT_PERFORMED"));
        assertTrue(implementation.contains("Cloud resource creation: NOT_PERFORMED"));
        assertTrue(status.contains("Final deployment platform: UNDECIDED"));
        assertTrue(status.contains("Actual external mutation: NOT_AUTHORIZED"));
        assertFalse(status.contains("Actual stage execution: PASS"));
        assertFalse(status.contains("Final deployment platform: GCP_CLOUD_RUN"));
        assertFalse(status.contains("Final deployment platform: AWS"));
    }

    @Test
    void searchWorkflowValidatesReadinessScriptsAndTemplate() throws IOException {
        String workflow = read(".github/workflows/sr-search-recommendation.yml");

        assertTrue(workflow.contains("agent/sr6fh-search-ctr-stage-one-shot-execution"));
        assertTrue(workflow.contains("verify_stage_readiness.py"));
        assertTrue(workflow.contains("render_sr6fh_binding.py"));
        assertTrue(workflow.contains("test_verify_stage_readiness.py"));
        assertTrue(workflow.contains("--mode template"));
        assertTrue(workflow.contains("Run SR-0 to SR-6F-I focused tests"));
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
