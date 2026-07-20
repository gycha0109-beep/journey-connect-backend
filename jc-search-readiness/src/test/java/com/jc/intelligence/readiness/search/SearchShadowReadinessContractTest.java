package com.jc.intelligence.readiness.search;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreAuthorView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityAdapter;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityContext;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreItemView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExplorePageView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView;
import com.jc.intelligence.contract.support.WireValue;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.integration.search.v1.SearchShadowContextV1;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationPort;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputProvider;
import com.jc.intelligence.readiness.search.v1.SearchActivationPrerequisiteMatrixV1;
import com.jc.intelligence.readiness.search.v1.SearchActivationPrerequisiteV1;
import com.jc.intelligence.readiness.search.v1.SearchBudgetKey;
import com.jc.intelligence.readiness.search.v1.SearchControlledHookProposalV1;
import com.jc.intelligence.readiness.search.v1.SearchDisabledModeEquivalenceResultV1;
import com.jc.intelligence.readiness.search.v1.SearchDisabledModeEquivalenceVerifier;
import com.jc.intelligence.readiness.search.v1.SearchDisabledModeObservationV1;
import com.jc.intelligence.readiness.search.v1.SearchGradleRegressionManifestV1;
import com.jc.intelligence.readiness.search.v1.SearchKillSwitchKey;
import com.jc.intelligence.readiness.search.v1.SearchKillSwitchStepV1;
import com.jc.intelligence.readiness.search.v1.SearchPrerequisiteRequirement;
import com.jc.intelligence.readiness.search.v1.SearchPrerequisiteStatus;
import com.jc.intelligence.readiness.search.v1.SearchReadinessContractIds;
import com.jc.intelligence.readiness.search.v1.SearchRollbackLevel;
import com.jc.intelligence.readiness.search.v1.SearchRollbackStepV1;
import com.jc.intelligence.readiness.search.v1.SearchShadowActivationReadinessAssessmentV1;
import com.jc.intelligence.readiness.search.v1.SearchShadowBudgetEntryV1;
import com.jc.intelligence.readiness.search.v1.SearchShadowExecutionBudgetContractV1;
import com.jc.intelligence.readiness.search.v1.SearchShadowKillSwitchContractV1;
import com.jc.intelligence.readiness.search.v1.SearchShadowObservabilityRetentionContractV1;
import com.jc.intelligence.readiness.search.v1.SearchShadowReadinessAuthorityV1;
import com.jc.intelligence.readiness.search.v1.SearchShadowReadinessDecision;
import com.jc.intelligence.readiness.search.v1.SearchShadowReadinessEvidenceV1;
import com.jc.intelligence.readiness.search.v1.SearchShadowRollbackContractV1;
import com.jc.intelligence.readiness.search.v1.fixture.SearchReadinessFixtureCaseV1;
import com.jc.intelligence.readiness.search.v1.fixture.SearchReadinessFixtureJsonCodecV1;
import com.jc.intelligence.wiring.search.v1.DefaultSearchShadowDispatcher;
import com.jc.intelligence.wiring.search.v1.FixedSearchShadowCircuitBreaker;
import com.jc.intelligence.wiring.search.v1.NoOpSearchShadowHook;
import com.jc.intelligence.wiring.search.v1.SearchShadowCircuitState;
import com.jc.intelligence.wiring.search.v1.SearchShadowComparisonLogPort;
import com.jc.intelligence.wiring.search.v1.SearchShadowComparisonLogResultV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowDispatchStatus;
import com.jc.intelligence.wiring.search.v1.SearchShadowExecutor;
import com.jc.intelligence.wiring.search.v1.SearchShadowExecutorResultV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowExecutorStatus;
import com.jc.intelligence.wiring.search.v1.SearchShadowHookRequestV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowLogStatus;
import com.jc.intelligence.wiring.search.v1.SearchShadowStructuredRecordV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowTask;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringConfigV1;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class SearchShadowReadinessContractTest {
    private static final Instant TIME = Instant.parse("2026-07-20T03:00:00Z");
    private static final ProducerBuildId BUILD = new ProducerBuildId("ip8-readiness-test-build");
    private static int assertions;

    private SearchShadowReadinessContractTest() { }

    public static void main(String[] args) throws Exception {
        fixtureAndWireContracts();
        prerequisiteAndDecisionContracts();
        disabledModeEquivalenceContracts();
        controlledHookProposalContracts();
        budgetKillSwitchAndRollbackContracts();
        observabilityAndEvidenceContracts();
        gradleRegressionManifestContracts();
        backendExploreInventoryContracts();
        protectedBaselineContracts();
        architecturePrivacyAndDeterminismContracts();
        System.out.println("IP-8 Search shadow readiness/regression assertions: " + assertions + " PASS");
    }

    private static void fixtureAndWireContracts() throws IOException {
        List<Path> fixtures = fixturePaths();
        check(fixtures.size() == 8, "eight IP-8 fixtures exist");
        Set<String> scenarios = new HashSet<>();
        for (Path fixturePath : fixtures) {
            String json = Files.readString(fixturePath);
            SearchReadinessFixtureCaseV1 fixture = SearchReadinessFixtureJsonCodecV1.read(json);
            SearchReadinessFixtureCaseV1 roundTrip = SearchReadinessFixtureJsonCodecV1.read(
                    SearchReadinessFixtureJsonCodecV1.write(fixture));
            check(fixture.equals(roundTrip), "fixture round trip " + fixture.scenario());
            check(scenarios.add(fixture.scenario()), "fixture scenario unique");
            check(json.contains("\"proposalReady\""), "fixture camelCase");
            check(!json.contains("rawQuery"), "fixture omits raw query");
            check(fixture.expectedProposalDecision().matches("[a-z][a-z0-9_]*"), "proposal decision wire");
            check(fixture.expectedActivationDecision().matches("[a-z][a-z0-9_]*"), "activation decision wire");
            check(fixture.unresolvedActivationCount() >= 0, "fixture blocker count");
            SearchActivationPrerequisiteMatrixV1 fixtureMatrix = switch (fixture.scenario()) {
                case "proposal_incomplete" -> defaultMatrix(false, false, false);
                case "architecture_change_hold" -> defaultMatrix(true, true, false);
                case "all_prerequisites_resolved" -> defaultMatrix(true, false, true);
                default -> defaultMatrix(true, false, false);
            };
            check(fixtureMatrix.controlledHookProposalReady() == fixture.proposalReady(),
                    "fixture proposal readiness binding " + fixture.scenario());
            check(fixtureMatrix.activationBlockerCount() == fixture.unresolvedActivationCount(),
                    "fixture blocker count binding " + fixture.scenario());
            check(fixtureMatrix.proposalDecision().wireValue().equals(fixture.expectedProposalDecision()),
                    "fixture proposal decision binding " + fixture.scenario());
            check(fixtureMatrix.activationDecision().wireValue().equals(fixture.expectedActivationDecision()),
                    "fixture activation decision binding " + fixture.scenario());
            check(fixture.disabledEquivalenceExpected() == fixture.proposalReady(),
                    "fixture disabled-equivalence readiness binding " + fixture.scenario());
        }
        List<Class<? extends Enum<?>>> enums = List.of(
                SearchShadowReadinessDecision.class,
                SearchPrerequisiteStatus.class,
                SearchPrerequisiteRequirement.class,
                SearchBudgetKey.class,
                SearchKillSwitchKey.class,
                SearchRollbackLevel.class);
        for (Class<? extends Enum<?>> enumType : enums) {
            Set<String> wires = new HashSet<>();
            for (Enum<?> value : enumType.getEnumConstants()) {
                check(value instanceof WireValue, "enum implements WireValue");
                String wire = ((WireValue) value).wireValue();
                check(wire.matches("[a-z][a-z0-9_]*"), "wire is lowercase_snake_case");
                check(wires.add(wire), "wire enum value unique");
            }
        }
        check(SearchReadinessContractIds.READINESS.value().equals("search-shadow-activation-readiness-v1"), "readiness contract ID");
        check(SearchReadinessContractIds.REGRESSION.value().equals("ip-8-search-regression-closure-v1"), "regression contract ID");
        expectFailure(() -> SearchReadinessFixtureJsonCodecV1.read("{}"));
        expectFailure(() -> SearchReadinessFixtureJsonCodecV1.read("[]"));
    }

    private static void prerequisiteAndDecisionContracts() {
        SearchActivationPrerequisiteMatrixV1 matrix = defaultMatrix(true, false, false);
        check(matrix.controlledHookProposalReady(), "controlled hook proposal requirements resolved");
        check(matrix.proposalDecision() == SearchShadowReadinessDecision.READY_FOR_CONTROLLED_HOOK_PROPOSAL,
                "proposal readiness is positive");
        check(matrix.activationDecision() == SearchShadowReadinessDecision.HOLD_FOR_OWNER_DECISIONS,
                "production activation held for owner decisions");
        check(matrix.activationBlockerCount() >= 20, "activation blockers are explicit");
        check(matrix.cutoverBlockerCount() >= 2, "cutover blockers are explicit");
        check(matrix.prerequisites().stream().map(SearchActivationPrerequisiteV1::prerequisiteId).toList()
                .equals(matrix.prerequisites().stream().map(SearchActivationPrerequisiteV1::prerequisiteId).sorted().toList()),
                "matrix canonical order");

        SearchActivationPrerequisiteMatrixV1 incomplete = defaultMatrix(false, false, false);
        check(!incomplete.controlledHookProposalReady(), "incomplete proposal is not ready");
        check(incomplete.proposalDecision() == SearchShadowReadinessDecision.NOT_READY, "incomplete proposal decision");

        SearchActivationPrerequisiteMatrixV1 architecture = defaultMatrix(true, true, false);
        check(architecture.proposalDecision() == SearchShadowReadinessDecision.HOLD_FOR_ARCHITECTURE_CHANGE,
                "architecture hold represented");

        SearchActivationPrerequisiteMatrixV1 allResolved = defaultMatrix(true, false, true);
        check(allResolved.activationBlockerCount() == 0, "resolved activation matrix has no blocker");
        check(allResolved.activationDecision() == SearchShadowReadinessDecision.READY_FOR_CONTROLLED_HOOK_PROPOSAL,
                "resolved activation matrix advances");

        SearchShadowActivationReadinessAssessmentV1 assessment = new SearchShadowActivationReadinessAssessmentV1(
                SearchReadinessContractIds.READINESS,
                matrix.proposalDecision(),
                matrix.activationDecision(),
                matrix,
                true,
                false,
                false,
                TIME,
                BUILD,
                SearchShadowReadinessAuthorityV1.legacyOnly());
        check(assessment.proposalDecision() == SearchShadowReadinessDecision.READY_FOR_CONTROLLED_HOOK_PROPOSAL,
                "assessment proposal decision");
        check(assessment.productionActivationDecision() == SearchShadowReadinessDecision.HOLD_FOR_OWNER_DECISIONS,
                "assessment activation hold");
        check(!assessment.productionHookInserted() && !assessment.productionActivationEnabled(),
                "assessment has no production activation");
        expectFailure(() -> new SearchShadowActivationReadinessAssessmentV1(
                SearchReadinessContractIds.READINESS,
                matrix.proposalDecision(), matrix.activationDecision(), matrix, false, false, false, TIME, BUILD,
                SearchShadowReadinessAuthorityV1.legacyOnly()));
        expectFailure(() -> new SearchActivationPrerequisiteMatrixV1(
                SearchReadinessContractIds.PREREQUISITE_MATRIX,
                List.of(resolved("duplicate"), resolved("duplicate"))));
    }

    private static void disabledModeEquivalenceContracts() {
        PagedLegacyResponse response = new PagedLegacyResponse(List.of("post:2", "post:1"), 0, 20, 2L, 1, true);
        AtomicInteger integrationCalls = new AtomicInteger();
        AtomicInteger inputCalls = new AtomicInteger();
        CountingExecutor executor = new CountingExecutor();
        CountingLogPort logger = new CountingLogPort();
        SearchShadowIntegrationPort<PagedLegacyResponse> integrationPort = (legacyResponse, compatibility, context, provider) -> {
            integrationCalls.incrementAndGet();
            throw new AssertionError("disabled dispatcher must not invoke integration");
        };
        SearchShadowRuntimeInputProvider inputProvider = context -> {
            inputCalls.incrementAndGet();
            throw new AssertionError("disabled dispatcher must not invoke runtime input provider");
        };
        DefaultSearchShadowDispatcher<PagedLegacyResponse> dispatcher = new DefaultSearchShadowDispatcher<>(
                SearchShadowWiringConfigV1.disabledByDefault(BUILD),
                executor,
                new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED, false),
                integrationPort,
                inputProvider,
                logger);
        var receipt = dispatcher.dispatch(response, legacyCompatibility(), shadowContext());
        check(receipt.status() == SearchShadowDispatchStatus.DISABLED, "default dispatcher disabled");
        check(receipt.legacyResponse() == response, "disabled dispatcher preserves response identity");
        check(receipt.legacyResponse().equals(response), "disabled dispatcher preserves response equality");
        check(executor.submissions() == 0, "disabled executor submissions zero");
        check(integrationCalls.get() == 0, "disabled runtime/integration calls zero");
        check(inputCalls.get() == 0, "disabled input provider calls zero");
        check(logger.calls() == 0, "disabled logging calls zero");
        check(receipt.integrationResult() == null && receipt.logResult() == null, "disabled receipt has no fabricated results");

        NoOpSearchShadowHook<PagedLegacyResponse> noOp = new NoOpSearchShadowHook<>();
        var noOpReceipt = noOp.dispatch(hookRequest(response));
        check(noOpReceipt.status() == SearchShadowDispatchStatus.DISABLED, "no-op hook disabled");
        check(noOpReceipt.legacyResponse() == response, "no-op hook preserves response identity");

        SearchDisabledModeEquivalenceVerifier verifier = new SearchDisabledModeEquivalenceVerifier();
        SearchDisabledModeObservationV1<PagedLegacyResponse> observation = new SearchDisabledModeObservationV1<>(
                response, receipt.legacyResponse(), executor.submissions(), integrationCalls.get(), 0, logger.calls(),
                response.items().equals(receipt.legacyResponse().items()),
                samePage(response, receipt.legacyResponse()),
                true,
                true);
        SearchDisabledModeEquivalenceResultV1 equivalence = verifier.verify(observation);
        check(equivalence.equivalent(), "disabled mode equivalence verified");
        check(equivalence.violationCodes().isEmpty(), "disabled equivalence has no violations");

        List<SearchDisabledModeObservationV1<PagedLegacyResponse>> invalid = List.of(
                new SearchDisabledModeObservationV1<>(response, response, 1, 0, 0, 0, true, true, true, true),
                new SearchDisabledModeObservationV1<>(response, response, 0, 1, 0, 0, true, true, true, true),
                new SearchDisabledModeObservationV1<>(response, response, 0, 0, 1, 0, true, true, true, true),
                new SearchDisabledModeObservationV1<>(response, response, 0, 0, 0, 1, true, true, true, true),
                new SearchDisabledModeObservationV1<>(response, response, 0, 0, 0, 0, false, true, true, true),
                new SearchDisabledModeObservationV1<>(response, response, 0, 0, 0, 0, true, false, true, true),
                new SearchDisabledModeObservationV1<>(response, response, 0, 0, 0, 0, true, true, false, true),
                new SearchDisabledModeObservationV1<>(response, response, 0, 0, 0, 0, true, true, true, false));
        for (SearchDisabledModeObservationV1<PagedLegacyResponse> candidate : invalid) {
            SearchDisabledModeEquivalenceResultV1 result = verifier.verify(candidate);
            check(!result.equivalent(), "invalid observation rejected");
            check(!result.violationCodes().isEmpty(), "invalid observation has violation code");
            check(result.violationCodes().equals(result.violationCodes().stream().sorted().toList()),
                    "violation code ordering deterministic");
        }
        for (int iteration = 0; iteration < 200; iteration++) {
            check(verifier.verify(observation).equals(equivalence), "disabled equivalence deterministic");
            check(response.items().equals(List.of("post:2", "post:1")), "response order never changes");
            check(response.page() == 0 && response.size() == 20 && response.totalElements() == 2L,
                    "pagination never changes");
        }
    }

    private static void controlledHookProposalContracts() {
        SearchControlledHookProposalV1 proposal = controlledProposal();
        check(proposal.endpointId().equals("get:/api/v1/explore"), "proposal endpoint exact");
        check(proposal.recommendedBoundary().equals("controller_return_boundary_after_legacy_service_success"),
                "recommended hook boundary exact");
        check(proposal.protectedSourceFiles().contains("jc-backend/src/main/java/com/jc/backend/post/PostController.java"),
                "proposal identifies protected controller");
        check(!proposal.sourceApplied(), "proposal not applied");
        check(proposal.hookReturnIgnored(), "hook return ignored");
        check(proposal.defaultNoOp() && proposal.defaultDisabled() && proposal.defaultSampleZero(),
                "proposal fail-safe defaults");
        check(proposal.exceptionIsolated() && proposal.boundedExecutorRequired(), "proposal failure/latency isolation");
        check(proposal.legacyResponseAuthority(), "proposal preserves legacy response authority");
        expectFailure(() -> new SearchControlledHookProposalV1(
                "get:/api/v1/explore", "unsafe", proposal.protectedSourceFiles(), true,
                true, true, true, true, true, true, true));
        expectFailure(() -> new SearchControlledHookProposalV1(
                "get:/api/v1/explore", "unsafe", proposal.protectedSourceFiles(), false,
                false, true, true, true, true, true, true));
    }

    private static void budgetKillSwitchAndRollbackContracts() {
        List<SearchShadowBudgetEntryV1> unresolvedEntries = new ArrayList<>();
        for (SearchBudgetKey key : SearchBudgetKey.values()) {
            unresolvedEntries.add(new SearchShadowBudgetEntryV1(
                    key, SearchPrerequisiteStatus.UNRESOLVED, null, budgetUnit(key), "unassigned"));
        }
        SearchShadowExecutionBudgetContractV1 budget = new SearchShadowExecutionBudgetContractV1(
                SearchReadinessContractIds.BUDGET, unresolvedEntries);
        check(budget.entries().size() == SearchBudgetKey.values().length, "all budget dimensions present");
        check(!budget.productionApproved(), "production budget remains unresolved");
        check(budget.entries().stream().allMatch(entry -> entry.approvedValue().equals("unresolved")),
                "test values are not confused with production approvals");
        expectFailure(() -> new SearchShadowBudgetEntryV1(
                SearchBudgetKey.MAX_CONCURRENCY, SearchPrerequisiteStatus.RESOLVED, null, "count", "platform_owner"));
        expectFailure(() -> new SearchShadowBudgetEntryV1(
                SearchBudgetKey.MAX_CONCURRENCY, SearchPrerequisiteStatus.RESOLVED, "10 unsafe", "count", "platform_owner"));
        expectFailure(() -> new SearchShadowExecutionBudgetContractV1(
                SearchReadinessContractIds.BUDGET,
                List.of(unresolvedEntries.get(0), unresolvedEntries.get(0))));

        SearchShadowKillSwitchContractV1 killSwitch = killSwitch();
        check(killSwitch.steps().size() == 5, "five kill-switch levels");
        check(killSwitch.steps().get(0).key() == SearchKillSwitchKey.GLOBAL_DISABLED, "global disable first");
        check(killSwitch.steps().get(4).key() == SearchKillSwitchKey.EXECUTOR_UNAVAILABLE, "executor unavailable last fallback");
        check(killSwitch.failsClosedForShadow(), "kill-switch fails closed for shadow");
        check(killSwitch.failsOpenForLegacyResponse(), "kill-switch fails open for legacy response");
        check(killSwitch.steps().stream().allMatch(step -> step.effectCode().equals("shadow_skipped_legacy_unchanged")),
                "all kill-switches preserve legacy response");
        expectFailure(() -> new SearchShadowKillSwitchContractV1(
                SearchReadinessContractIds.KILL_SWITCH, killSwitch.steps().subList(0, 4)));

        SearchShadowRollbackContractV1 rollback = rollback();
        check(rollback.steps().size() == 5, "five rollback levels");
        for (int index = 0; index < rollback.steps().size(); index++) {
            SearchRollbackStepV1 step = rollback.steps().get(index);
            check(step.level().ordinal() == index, "rollback level canonical order");
            check(!step.legacyResponseImpact(), "rollback never changes legacy response");
            check(step.verificationCode().equals("disabled_equivalence_regression"), "rollback verification stable");
        }
        check(!rollback.steps().get(0).requiresDeployment(), "rollback level 0 requires no deployment");
        check(!rollback.steps().get(1).requiresDeployment(), "rollback level 1 requires no deployment by contract proposal");
        check(rollback.steps().get(3).requiresDeployment(), "hook removal requires deployment");
        check(rollback.steps().get(4).requiresDeployment(), "module removal requires deployment");
    }

    private static void observabilityAndEvidenceContracts() {
        SearchShadowObservabilityRetentionContractV1 observability = observability();
        check(!observability.persistentWriterImplemented(), "persistent observability writer absent");
        check(!observability.persistentLoggingReady(), "persistent logging held for owner decisions");
        for (String prohibited : List.of("raw_query", "normalized_query_text", "full_request", "full_response",
                "candidate_payload", "authentication_token", "raw_user_id", "raw_session_id", "precise_location")) {
            check(observability.prohibitedFields().contains(prohibited), "prohibited observability field " + prohibited);
            check(!observability.allowedFields().contains(prohibited), "prohibited field not allowed " + prohibited);
        }
        check(observability.allowedFields().contains("correlation_fingerprint"), "correlation fingerprint allowed");
        check(observability.allowedFields().contains("duration_bucket"), "duration bucket allowed");
        check(observability.allowedFields().equals(observability.allowedFields().stream().sorted().toList()),
                "allowed observability fields canonical order");
        check(observability.prohibitedFields().equals(observability.prohibitedFields().stream().sorted().toList()),
                "prohibited observability fields canonical order");
        expectFailure(() -> new SearchShadowObservabilityRetentionContractV1(
                SearchReadinessContractIds.OBSERVABILITY,
                List.of("reference_time", "reference_time"), observability.prohibitedFields(),
                SearchPrerequisiteStatus.UNRESOLVED, SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED, SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED, SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED, false));
        expectFailure(() -> new SearchShadowObservabilityRetentionContractV1(
                SearchReadinessContractIds.OBSERVABILITY,
                List.of("raw_query"), observability.prohibitedFields(),
                SearchPrerequisiteStatus.UNRESOLVED, SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED, SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED, SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED, false));
        expectFailure(() -> new SearchShadowObservabilityRetentionContractV1(
                SearchReadinessContractIds.OBSERVABILITY,
                observability.allowedFields(), observability.prohibitedFields(),
                SearchPrerequisiteStatus.UNRESOLVED, SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED, SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED, SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED, true));

        SearchActivationPrerequisiteMatrixV1 matrix = defaultMatrix(true, false, false);
        String fingerprint = sha256("ip8|" + matrix.prerequisites());
        SearchShadowReadinessEvidenceV1 evidence = new SearchShadowReadinessEvidenceV1(
                fingerprint,
                matrix.proposalDecision(),
                matrix.activationDecision(),
                matrix.activationBlockerCount(),
                matrix.cutoverBlockerCount(),
                true,
                TIME,
                BUILD,
                SearchShadowReadinessAuthorityV1.legacyOnly());
        check(evidence.assessmentFingerprint().matches("[0-9a-f]{64}"), "evidence fingerprint stable");
        check(evidence.proposalDecision() == SearchShadowReadinessDecision.READY_FOR_CONTROLLED_HOOK_PROPOSAL,
                "evidence proposal decision");
        check(evidence.activationDecision() == SearchShadowReadinessDecision.HOLD_FOR_OWNER_DECISIONS,
                "evidence activation hold");
        Set<String> componentNames = new HashSet<>();
        for (RecordComponent component : SearchShadowReadinessEvidenceV1.class.getRecordComponents()) {
            componentNames.add(component.getName());
        }
        for (String forbidden : List.of("rawQuery", "normalizedQuery", "sessionId", "correlationId", "fullResponse")) {
            check(!componentNames.contains(forbidden), "readiness evidence omits " + forbidden);
        }
        check(evidence.authority().equals(SearchShadowReadinessAuthorityV1.legacyOnly()), "evidence has no production authority");
        expectFailure(() -> new SearchShadowReadinessAuthorityV1(
                "legacy", false, true, false, false, false, false, false, false, false));
    }

    private static void gradleRegressionManifestContracts() throws IOException {
        SearchGradleRegressionManifestV1 manifest = regressionManifest();
        check(manifest.gradleVersion().equals("8.14.5"), "Gradle version exact");
        check(manifest.javaVersion() == 21, "Java version exact");
        check(!manifest.ignoreFailures(), "ignoreFailures prohibited");
        check(!manifest.unifiedTaskDependencies().contains(":ip8SearchRegressionClosure"),
                "unified task has no self dependency");
        check(manifest.unifiedTaskDependencies().stream().distinct().count() == manifest.unifiedTaskDependencies().size(),
                "unified task graph has unique dependencies");
        check(manifest.dockerRequiredForBackend(), "backend Docker/Testcontainers prerequisite explicit");
        check(manifest.postgresImage().equals("postgres:15-alpine"), "PostgreSQL image exact");
        for (String required : requiredUnifiedTasks()) {
            check(manifest.unifiedTaskDependencies().contains(required), "unified task dependency " + required);
        }
        for (String required : requiredBackendTasks()) {
            check(manifest.externalBackendCommands().contains(required), "backend command " + required);
        }
        check(!manifest.externalBackendCommands().stream().anyMatch(task -> task.startsWith(":jc-backend:")),
                "root backend tasks do not use nonexistent subproject path");

        Path project = projectRoot();
        String settings = Files.readString(project.resolve("jc-backend/settings.gradle.kts"));
        check(settings.contains("include(\":jc-search-readiness\")"), "readiness module registered");
        check(settings.contains("../jc-search-readiness"), "readiness module path correct");
        String backendBuild = Files.readString(project.resolve("jc-backend/build.gradle.kts"));
        check(backendBuild.contains("ip8SearchRegressionClosure"), "unified regression task registered");
        for (String task : requiredUnifiedTasks()) {
            check(backendBuild.contains("\"" + task + "\""), "root task references " + task);
        }
        check(!backendBuild.contains("ignoreFailures"), "backend build has no ignoreFailures");
        String readinessBuild = Files.readString(project.resolve("jc-search-readiness/build.gradle.kts"));
        check(readinessBuild.contains("searchReadinessRegressionContractTest"), "IP-8 dedicated task exists");
        check(readinessBuild.contains("JavaLanguageVersion.of(21)"), "IP-8 Java 21 toolchain");
        check(readinessBuild.contains("-Xlint:all") && readinessBuild.contains("-Werror"), "IP-8 lint gate");
        check(!readinessBuild.contains("jc-backend") && !readinessBuild.contains("jc-recommendation-core"),
                "readiness module avoids backend and recommendation dependencies");

        String wrapper = Files.readString(project.resolve("jc-backend/gradle/wrapper/gradle-wrapper.properties"));
        check(wrapper.contains("gradle-8.14.5-bin.zip"), "wrapper distribution exact");
        check(wrapper.contains("validateDistributionUrl=true"), "wrapper distribution validation enabled");
        check(Files.isRegularFile(project.resolve("jc-backend/gradle/wrapper/gradle-wrapper.jar")), "wrapper jar exists");
        check(Files.isRegularFile(project.resolve("jc-backend/gradlew")), "gradlew exists");
        check(Files.isExecutable(project.resolve("jc-backend/gradlew")), "gradlew executable");
        check(Files.isRegularFile(project.resolve("jc-backend/gradlew.bat")), "gradlew.bat exists");

        String initializer = Files.readString(project.resolve("jc-backend/src/test/java/com/jc/backend/CanonicalPostgresInitializer.java"));
        check(initializer.contains("postgres:15-alpine"), "Testcontainers PostgreSQL image documented by source");
        check(initializer.contains("JC_TEST_DB_URL"), "external PostgreSQL override supported");
        check(initializer.contains("25_recommendation_p2_evaluation_release.sql"), "canonical SQL 25 included");
        check(initializer.contains("26_recommendation_p2_evaluation_release_smoke_test.sql"), "canonical SQL 26 included");
    }

    private static void backendExploreInventoryContracts() throws IOException {
        Path project = projectRoot();
        String controller = Files.readString(project.resolve("jc-backend/src/main/java/com/jc/backend/post/PostController.java"));
        String service = Files.readString(project.resolve("jc-backend/src/main/java/com/jc/backend/post/PostService.java"));
        String repository = Files.readString(project.resolve("jc-backend/src/main/java/com/jc/backend/post/JourneyPostRepository.java"));
        String pageResponse = Files.readString(project.resolve("jc-backend/src/main/java/com/jc/backend/common/PageResponse.java"));
        String dto = Files.readString(project.resolve("jc-backend/src/main/java/com/jc/backend/post/PostDtos.java"));
        String security = Files.readString(project.resolve("jc-backend/src/main/kotlin/com/jc/backend/config/SecurityConfig.kt"));
        String productionConfig = Files.readString(project.resolve("jc-backend/src/main/resources/application.yml.sample"));
        String testConfig = Files.readString(project.resolve("jc-backend/src/test/resources/application.yml"));

        check(controller.contains("@GetMapping(\"/explore\")"), "explore endpoint exists");
        check(controller.contains("PageResponse<PostDtos.Summary> legacyResponse = postService.explore(keyword, region, pageable);")
                        && controller.contains("return ApiResponse.ok(legacyResponse);"),
                "controller preserves the legacy service result as response authority");
        check(controller.contains("@PageableDefault(size = 20)"), "legacy page size default 20");
        check(controller.contains("exploreSearchShadowBridge.afterExplore(keyword, region, pageable, legacyResponse);")
                        && !controller.contains("SearchShadowDispatchReceiptV1"),
                "controller contains only the controlled backend-local shadow bridge call");
        check(service.contains("public PageResponse<PostDtos.Summary> explore(String keyword, String region, Pageable pageable)"),
                "legacy service method exact");
        check(service.contains("posts.explore(blankToNull(keyword), normalizeRegionQuery(region), pageable)"),
                "legacy request normalization path exact");
        check(!service.contains("SearchShadowHook") && !service.contains("searchShadow"), "service hook not inserted");
        check(repository.contains("Page<JourneyPost> explore("), "legacy repository method exists");
        check(repository.contains("lower(p.title) like lower(concat('%', :keyword, '%'))"), "title matching preserved");
        check(repository.contains("lower(p.content) like lower(concat('%', :keyword, '%'))"), "content matching preserved");
        check(repository.contains("p.moderationStatus = com.jc.backend.post.PostModerationStatus.VISIBLE"),
                "moderation visibility preserved");
        check(repository.contains("p.author.accountStatus = 'active'"), "active author condition preserved");
        check(repository.contains("order by p.publishedAt desc, p.id desc"), "legacy deterministic latest ordering preserved");
        check(pageResponse.contains("long totalElements") && pageResponse.contains("int totalPages") && pageResponse.contains("boolean last"),
                "legacy pagination metadata exact");
        check(dto.contains("public record Summary("), "legacy summary DTO exists");
        check(security.contains("\"/api/v1/explore\""), "explore remains permitAll");
        check(!productionConfig.contains("search.shadow") && !productionConfig.contains("SEARCH_SHADOW"),
                "production config has no shadow activation");
        check(!testConfig.contains("search.shadow") && !testConfig.contains("SEARCH_SHADOW"),
                "backend test config has no hidden activation");

        SearchControlledHookProposalV1 proposal = controlledProposal();
        check(proposal.protectedSourceFiles().stream().allMatch(file -> Files.isRegularFile(project.resolve(file))),
                "proposal target files exist");
        check(proposal.protectedSourceFiles().size() == 1, "proposal names one minimal protected target");
    }

    private static void protectedBaselineContracts() throws IOException {
        Path project = projectRoot();
        Path expectedProtected = project.resolve("verification/ip7/IP7_PROTECTED_BASELINE_EXPECTED_SHA256.txt");
        Path expectedSql = project.resolve("verification/ip7/IP7_SQL_01_26_EXPECTED_SHA256.txt");
        List<String> protectedLines = Files.readAllLines(expectedProtected).stream().filter(line -> !line.isBlank()).toList();
        List<String> sqlLines = Files.readAllLines(expectedSql).stream().filter(line -> !line.isBlank()).toList();
        check(protectedLines.size() == 320, "protected source manifest has 320 paths");
        check(sqlLines.size() == 26, "canonical SQL manifest has 26 paths");
        for (String line : protectedLines) {
            HashEntry entry = parseHashEntry(line);
            Path file = project.resolve(entry.relativePath());
            check(Files.isRegularFile(file), "protected file exists " + entry.relativePath());
            if (entry.relativePath().equals("jc-backend/src/main/java/com/jc/backend/post/PostController.java")) {
                String controller = Files.readString(file);
                check(controller.contains("exploreSearchShadowBridge.afterExplore(keyword, region, pageable, legacyResponse);")
                                && controller.contains("return ApiResponse.ok(legacyResponse);"),
                        "approved IP-9 controller delta preserves legacy authority");
            } else {
                check(sha256(file).equals(entry.sha256()), "protected file exact " + entry.relativePath());
            }
        }
        for (String line : sqlLines) {
            HashEntry entry = parseHashEntry(line);
            Path file = project.resolve(entry.relativePath());
            check(Files.isRegularFile(file), "canonical SQL exists " + entry.relativePath());
            check(sha256(file).equals(entry.sha256()), "canonical SQL exact " + entry.relativePath());
        }
        List<String> numberedSql;
        try (var stream = Files.list(project.resolve("database/journey-connect-db-v2.7"))) {
            numberedSql = stream.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("[0-9]{2}_.+\\.sql"))
                    .sorted()
                    .toList();
        }
        check(numberedSql.size() >= 26, "canonical SQL 01..26 remain present");
        for (String name : numberedSql.subList(26, numberedSql.size())) {
            int migrationNumber = Integer.parseInt(name.substring(0, 2));
            check(migrationNumber >= 27, "post-baseline SQL extensions start at 27");
        }
    }

    private static void architecturePrivacyAndDeterminismContracts() throws IOException {
        Path project = projectRoot();
        Path main = project.resolve("jc-search-readiness/src/main/java");
        List<Path> sources;
        try (var stream = Files.walk(main)) {
            sources = stream.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        check(sources.size() >= 20, "readiness module has explicit domain contracts");
        StringBuilder allBuilder = new StringBuilder();
        for (Path source : sources) allBuilder.append(Files.readString(source)).append('\n');
        String all = allBuilder.toString();
        for (String forbidden : List.of(
                "com.jc.backend", "com.jc.recommendation", "org.springframework", "jakarta.persistence", "java.sql",
                "EntityManager", "Repository", "@Component", "@Service", "@Configuration", "ForkJoinPool.commonPool",
                "new Thread(", "Executors.newCachedThreadPool", "Kafka", "INSERT INTO", "UPDATE ", "DELETE FROM")) {
            check(!all.contains(forbidden), "forbidden readiness dependency absent: " + forbidden);
        }
        check(!all.contains("rawQuery") && !all.contains("normalizedQuery"), "readiness production types omit query text");
        check(!all.contains("search_exposure_v1"), "no exposure writer activated");
        check(!all.contains("production_enabled"), "no production activation enum added");

        SearchActivationPrerequisiteMatrixV1 matrix = defaultMatrix(true, false, false);
        SearchShadowReadinessEvidenceV1 first = readinessEvidence(matrix);
        for (int iteration = 0; iteration < 200; iteration++) {
            SearchShadowReadinessEvidenceV1 repeated = readinessEvidence(defaultMatrix(true, false, false));
            check(repeated.equals(first), "readiness evidence deterministic");
            check(repeated.assessmentFingerprint().equals(first.assessmentFingerprint()), "readiness fingerprint deterministic");
            check(repeated.proposalDecision() == SearchShadowReadinessDecision.READY_FOR_CONTROLLED_HOOK_PROPOSAL,
                    "proposal decision stable");
            check(repeated.activationDecision() == SearchShadowReadinessDecision.HOLD_FOR_OWNER_DECISIONS,
                    "activation hold stable");
        }

        for (String document : List.of(
                "IP-8-SEARCH-SHADOW-ACTIVATION-READINESS-AND-REGRESSION-CLOSURE.md",
                "IP-8-CONTROLLED-HOOK-CHANGE-PROPOSAL.md",
                "IP-8-ACTIVATION-PREREQUISITE-MATRIX.md",
                "IP-8-ROLLBACK-KILL-SWITCH-AND-OBSERVABILITY-CONTRACT.md",
                "IP-8-HANDOFF.md")) {
            check(Files.isRegularFile(project.resolve("docs/platform/intelligence").resolve(document)),
                    "IP-8 document exists " + document);
        }
    }

    private static SearchActivationPrerequisiteMatrixV1 defaultMatrix(
            boolean proposalReady, boolean architectureHold, boolean resolveActivation) {
        List<SearchActivationPrerequisiteV1> items = new ArrayList<>();
        items.add(proposalReady ? resolved("backend_explore_inventory") : unresolvedProposal("backend_explore_inventory"));
        items.add(proposalReady ? resolved("controlled_hook_proposal") : unresolvedProposal("controlled_hook_proposal"));
        items.add(proposalReady ? resolved("disabled_mode_equivalence") : unresolvedProposal("disabled_mode_equivalence"));
        items.add(proposalReady ? resolved("production_hook_not_inserted") : unresolvedProposal("production_hook_not_inserted"));
        items.add(proposalReady ? resolved("unified_regression_task") : unresolvedProposal("unified_regression_task"));
        items.add(new SearchActivationPrerequisiteV1(
                "architecture_change_required",
                architectureHold ? SearchPrerequisiteStatus.UNRESOLVED : SearchPrerequisiteStatus.NOT_REQUIRED_FOR_SHADOW,
                SearchPrerequisiteRequirement.NOT_REQUIRED_FOR_DISABLED_REGRESSION,
                "unassigned",
                architectureHold ? "architecture_review_pending" : "independent_readiness_module"));
        List<String> activation = List.of(
                "actual_retrieval_index_strategy", "runtime_input_provider", "operations_visibility_owner",
                "eligibility_authority", "search_run_writer", "snapshot_writer", "shadow_evidence_writer",
                "search_exposure_writer", "query_retention_owner", "evidence_retention_owner", "data_deletion_policy",
                "executor_concurrency_budget", "queue_capacity_budget", "timeout_budget", "latency_budget",
                "error_budget", "circuit_breaker_threshold", "kill_switch_authority", "activation_authority",
                "rollback_authority", "on_call_owner", "incident_response_path");
        for (String id : activation) {
            items.add(resolveActivation
                    ? new SearchActivationPrerequisiteV1(id, SearchPrerequisiteStatus.RESOLVED,
                            SearchPrerequisiteRequirement.REQUIRED_BEFORE_ACTIVATION, "approved_owner", "approved_evidence")
                    : new SearchActivationPrerequisiteV1(id, SearchPrerequisiteStatus.UNRESOLVED,
                            SearchPrerequisiteRequirement.REQUIRED_BEFORE_ACTIVATION, "unassigned", "owner_decision_pending"));
        }
        for (String id : List.of("production_cursor_key_owner", "key_rotation_policy")) {
            items.add(resolveActivation
                    ? new SearchActivationPrerequisiteV1(id, SearchPrerequisiteStatus.RESOLVED,
                            SearchPrerequisiteRequirement.REQUIRED_BEFORE_CUTOVER, "approved_owner", "approved_evidence")
                    : new SearchActivationPrerequisiteV1(id, SearchPrerequisiteStatus.UNRESOLVED,
                            SearchPrerequisiteRequirement.REQUIRED_BEFORE_CUTOVER, "unassigned", "cutover_decision_pending"));
        }
        return new SearchActivationPrerequisiteMatrixV1(SearchReadinessContractIds.PREREQUISITE_MATRIX, items);
    }

    private static SearchActivationPrerequisiteV1 resolved(String id) {
        return new SearchActivationPrerequisiteV1(id, SearchPrerequisiteStatus.RESOLVED,
                SearchPrerequisiteRequirement.REQUIRED_FOR_CONTROLLED_HOOK_PROPOSAL,
                "intelligence_platform", "ip8_verified");
    }

    private static SearchActivationPrerequisiteV1 unresolvedProposal(String id) {
        return new SearchActivationPrerequisiteV1(id, SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteRequirement.REQUIRED_FOR_CONTROLLED_HOOK_PROPOSAL,
                "unassigned", "verification_pending");
    }

    private static SearchControlledHookProposalV1 controlledProposal() {
        return new SearchControlledHookProposalV1(
                "get:/api/v1/explore",
                "controller_return_boundary_after_legacy_service_success",
                List.of("jc-backend/src/main/java/com/jc/backend/post/PostController.java"),
                false, true, true, true, true, true, true, true);
    }

    private static SearchShadowKillSwitchContractV1 killSwitch() {
        List<SearchKillSwitchStepV1> steps = new ArrayList<>();
        SearchKillSwitchKey[] keys = SearchKillSwitchKey.values();
        for (int index = 0; index < keys.length; index++) {
            steps.add(new SearchKillSwitchStepV1(
                    index + 1, keys[index], SearchPrerequisiteStatus.UNRESOLVED, "unassigned",
                    false, "shadow_skipped_legacy_unchanged"));
        }
        return new SearchShadowKillSwitchContractV1(SearchReadinessContractIds.KILL_SWITCH, steps);
    }

    private static SearchShadowRollbackContractV1 rollback() {
        List<SearchRollbackStepV1> steps = List.of(
                new SearchRollbackStepV1(SearchRollbackLevel.LEVEL_0_SAMPLE_ZERO, "unassigned", false, false, true, "disabled_equivalence_regression"),
                new SearchRollbackStepV1(SearchRollbackLevel.LEVEL_1_MODE_DISABLED, "unassigned", false, false, true, "disabled_equivalence_regression"),
                new SearchRollbackStepV1(SearchRollbackLevel.LEVEL_2_NO_OP_BEAN, "unassigned", true, false, true, "disabled_equivalence_regression"),
                new SearchRollbackStepV1(SearchRollbackLevel.LEVEL_3_REMOVE_HOOK_CALL, "unassigned", true, false, true, "disabled_equivalence_regression"),
                new SearchRollbackStepV1(SearchRollbackLevel.LEVEL_4_REMOVE_MODULE_DEPENDENCY, "unassigned", true, false, true, "disabled_equivalence_regression"));
        return new SearchShadowRollbackContractV1(SearchReadinessContractIds.ROLLBACK, steps);
    }

    private static SearchShadowObservabilityRetentionContractV1 observability() {
        return new SearchShadowObservabilityRetentionContractV1(
                SearchReadinessContractIds.OBSERVABILITY,
                List.of("reference_time", "correlation_fingerprint", "shadow_mode", "sampling_decision",
                        "dispatch_status", "runtime_status", "comparison_status", "mismatch_codes", "severity",
                        "count_metrics", "duration_bucket", "policy_versions", "producer_build_id"),
                List.of("raw_query", "normalized_query_text", "full_request", "full_response", "candidate_payload",
                        "authentication_token", "raw_user_id", "raw_session_id", "precise_location", "private_metadata"),
                SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED,
                SearchPrerequisiteStatus.UNRESOLVED,
                false);
    }

    private static SearchGradleRegressionManifestV1 regressionManifest() {
        return new SearchGradleRegressionManifestV1(
                SearchReadinessContractIds.REGRESSION,
                "8.14.5",
                21,
                requiredUnifiedTasks(),
                requiredBackendTasks(),
                false,
                true,
                "postgres:15-alpine");
    }

    private static List<String> requiredUnifiedTasks() {
        return List.of(
                ":jc-intelligence-contracts:intelligenceContractTest",
                ":jc-search-contracts:searchDomainContractTest",
                ":jc-search-compatibility:searchCompatibilityContractTest",
                ":jc-search-runtime:searchRuntimeContractTest",
                ":jc-search-integration:searchIntegrationContractTest",
                ":jc-search-shadow-wiring:searchShadowWiringContractTest",
                ":jc-search-readiness:searchReadinessRegressionContractTest",
                ":jc-recommendation-core:coreFoundationContractTest",
                ":jc-recommendation-core:coreWave1ContractTest",
                ":jc-recommendation-core:coreWave2ScoringContractTest",
                ":jc-recommendation-core:coreWave3RankingDiversityContractTest",
                ":jc-recommendation-core:coreWave3ExplorationContractTest",
                ":jc-recommendation-core:coreWave4RankingIntegrationContractTest",
                ":jc-recommendation-core:coreWave5ExposureContractTest",
                ":jc-recommendation-core:coreWave6AttributionContractTest",
                ":jc-recommendation-core:coreWave7OfflineEvaluationContractTest",
                ":jc-recommendation-core:javaCoreGoldenFixtureContractTest",
                ":jc-recommendation-core:javaCoreIsolationContractTest",
                ":jc-recommendation-core:p1CoreContractTest",
                ":jc-recommendation-core:p2CoreContractTest");
    }

    private static List<String> requiredBackendTasks() {
        return List.of(
                ":test",
                ":p0Verification",
                ":p1Verification",
                ":p2Verification",
                ":ip1CompatibilityContractTest",
                ":check");
    }

    private static String budgetUnit(SearchBudgetKey key) {
        return switch (key) {
            case MAX_CONCURRENCY, QUEUE_CAPACITY -> "count";
            case TASK_TIMEOUT, END_TO_END_SHADOW_BUDGET, HOOK_DISPATCH_OVERHEAD, EXECUTOR_SUBMISSION_OVERHEAD,
                    QUEUE_WAIT, RUNTIME_DURATION, COMPARISON_DURATION, LOGGING_DURATION, TOTAL_SHADOW_DURATION -> "milliseconds";
            case EXECUTOR_REJECTION_POLICY, QUEUE_FULL_POLICY, LATE_RESULT_POLICY, CANCELLATION_POLICY,
                    CIRCUIT_OPEN_POLICY -> "policy";
        };
    }

    private static SearchShadowReadinessEvidenceV1 readinessEvidence(SearchActivationPrerequisiteMatrixV1 matrix) {
        return new SearchShadowReadinessEvidenceV1(
                sha256("ip8|" + matrix.prerequisites()),
                matrix.proposalDecision(),
                matrix.activationDecision(),
                matrix.activationBlockerCount(),
                matrix.cutoverBlockerCount(),
                true,
                TIME,
                BUILD,
                SearchShadowReadinessAuthorityV1.legacyOnly());
    }

    private static LegacyExploreCompatibilityResult legacyCompatibility() {
        return new LegacyExploreCompatibilityAdapter().adapt(
                legacyRequest(),
                legacyPage(),
                new LegacyExploreCompatibilityContext(
                        "request:ip8", "correlation:ip8", "session:opaque", TIME, TIME, BUILD));
    }

    private static LegacyExploreRequestView legacyRequest() {
        return new LegacyExploreRequestView("서울 여행", "seoul", 0, 20, List.of(), Map.of());
    }

    private static LegacyExplorePageView legacyPage() {
        LegacyExploreAuthorView author = new LegacyExploreAuthorView(10L, "tester", null);
        return new LegacyExplorePageView(
                List.of(
                        new LegacyExploreItemView(2L, "two", "KR-11", "서울", null, 10L, 2L, 1L, author, TIME.minusSeconds(1)),
                        new LegacyExploreItemView(1L, "one", "KR-11", "서울", null, 9L, 1L, 0L, author, TIME.minusSeconds(2))),
                0, 20, 2L, 1, true);
    }

    private static SearchShadowContextV1 shadowContext() {
        return new SearchShadowContextV1("request:ip8", "correlation:ip8", "session:opaque", TIME);
    }

    private static SearchShadowHookRequestV1<PagedLegacyResponse> hookRequest(PagedLegacyResponse response) {
        return new SearchShadowHookRequestV1<>(
                response,
                legacyRequest(),
                legacyPage(),
                new LegacyExploreCompatibilityContext(
                        "request:ip8", "correlation:ip8", "session:opaque", TIME, TIME, BUILD),
                shadowContext());
    }

    private static boolean samePage(PagedLegacyResponse left, PagedLegacyResponse right) {
        return left.page() == right.page() && left.size() == right.size()
                && left.totalElements() == right.totalElements() && left.totalPages() == right.totalPages()
                && left.last() == right.last();
    }

    private static List<Path> fixturePaths() throws IOException {
        Path directory = projectRoot().resolve("jc-search-readiness/src/test/resources/search-readiness");
        try (var stream = Files.list(directory)) {
            return stream.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("jc-search-readiness"))) current = current.getParent();
        if (current == null) throw new IllegalStateException("project root not found");
        return current;
    }

    private static HashEntry parseHashEntry(String line) {
        int separator = line.indexOf("  ");
        if (separator != 64) throw new IllegalArgumentException("invalid SHA-256 manifest line");
        return new HashEntry(line.substring(0, separator), line.substring(separator + 2));
    }

    private static String sha256(Path path) throws IOException {
        return sha256(Files.readAllBytes(path));
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Runnable action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("expected failure");
        } catch (IllegalArgumentException expected) {
            // Expected contract rejection.
        }
    }

    private record PagedLegacyResponse(
            List<String> items, int page, int size, long totalElements, int totalPages, boolean last) {
        private PagedLegacyResponse {
            items = List.copyOf(items);
        }
    }

    private record HashEntry(String sha256, String relativePath) { }

    private static final class CountingExecutor implements SearchShadowExecutor {
        private int submissions;
        @Override public boolean available() { return true; }
        @Override public int queueCapacity() { return 1; }
        @Override public int maxConcurrency() { return 1; }
        @Override public <T> SearchShadowExecutorResultV1<T> submit(SearchShadowTask<T> task, Duration timeout) {
            submissions++;
            return SearchShadowExecutorResultV1.failure(SearchShadowExecutorStatus.REJECTED, "unexpected_submission");
        }
        int submissions() { return submissions; }
    }

    private static final class CountingLogPort implements SearchShadowComparisonLogPort {
        private int calls;
        @Override public boolean available() { return true; }
        @Override public SearchShadowComparisonLogResultV1 log(SearchShadowStructuredRecordV1 record) {
            calls++;
            return new SearchShadowComparisonLogResultV1(SearchShadowLogStatus.ACCEPTED, "memory_only");
        }
        int calls() { return calls; }
    }
}
