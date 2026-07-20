package com.jc.backend.recommendation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.database.DatabasePropagation;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.recommendation.config.RecommendationProperties;
import com.jc.backend.recommendation.persistence.RecommendationHashing;
import com.jc.backend.recommendation.persistence.RecommendationReplayAuditStore;
import com.jc.backend.recommendation.persistence.RecommendationReplayAuditStore.ReplayAuditWrite;
import com.jc.backend.recommendation.persistence.RecommendationReplayStore;
import com.jc.backend.recommendation.persistence.RecommendationReplayStore.ReplayBundle;
import com.jc.backend.recommendation.persistence.RecommendationSnapshotStore.StoredSnapshot;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.SnapshotKind;
import com.jc.recommendation.model.exploration.InsertedExplorationCandidate;
import com.jc.recommendation.model.exploration.PersonalizedExplorationCandidate;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.CollectedRankingV3Result;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.RankingV3ReplayInputSnapshot;
import com.jc.recommendation.offline.RankingV3FullResultCollector;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Reconstructs a recommendation run solely from persisted immutable snapshots. */
@Service
public class RecommendationReplayService {

    private final RecommendationProperties properties;
    private final RecommendationReplayStore replayStore;
    private final RecommendationReplayAuditStore auditStore;
    private final RecommendationCanonicalPayload canonicalPayload;
    private final ObjectMapper objectMapper;
    private final RankingV3FullResultCollector collector = new RankingV3FullResultCollector();

    public RecommendationReplayService(
            RecommendationProperties properties,
            RecommendationReplayStore replayStore,
            RecommendationReplayAuditStore auditStore,
            RecommendationCanonicalPayload canonicalPayload,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.replayStore = replayStore;
        this.auditStore = auditStore;
        this.canonicalPayload = canonicalPayload;
        this.objectMapper = objectMapper;
    }

    @DatabaseTransactional(
            role = DatabaseRole.RECOMMENDATION,
            propagation = DatabasePropagation.REQUIRES_NEW)
    public ReplayAuditResult audit(String runId) {
        Objects.requireNonNull(runId, "runId");
        long started = System.nanoTime();
        ReplayBundle bundle = replayStore.load(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown recommendation run: " + runId));

        Evaluation evaluation = evaluate(bundle);
        long durationMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
        String auditId = auditId(
                runId,
                properties.getReplayEvaluatorVersion(),
                properties.getCoreBuildId());
        var stored = auditStore.store(new ReplayAuditWrite(
                auditId,
                runId,
                properties.getReplayEvaluatorVersion(),
                properties.getCoreBuildId(),
                evaluation.status(),
                evaluation.categories(),
                bundle.rankingInput().contentHash(),
                bundle.rankingResult().contentHash(),
                bundle.run().resultFingerprint(),
                evaluation.actualFingerprint(),
                evaluation.rankedCount(),
                evaluation.terminalCount(),
                durationMs));
        return new ReplayAuditResult(
                stored.auditId(),
                stored.runId(),
                stored.replayStatus().equals("exact_match"),
                stored.replayStatus(),
                stored.mismatchCategories(),
                stored.actualResultFingerprint());
    }

    private Evaluation evaluate(ReplayBundle bundle) {
        List<String> snapshotProblems = validateSnapshots(bundle);
        if (!snapshotProblems.isEmpty()) {
            return new Evaluation("invalid_snapshot", snapshotProblems, null, 0, 0);
        }

        RankingInputEnvelope envelope;
        try {
            envelope = objectMapper.readValue(
                    bundle.rankingInput().canonicalPayload(),
                    RankingInputEnvelope.class);
        } catch (Exception exception) {
            return new Evaluation(
                    "invalid_snapshot",
                    List.of("ranking_input_decode"),
                    null,
                    0,
                    0);
        }

        List<String> bindingProblems = validateBindings(bundle, envelope);
        if (!bindingProblems.isEmpty()) {
            return new Evaluation("invalid_binding", bindingProblems, null, 0, 0);
        }

        CollectedRankingV3Result collected;
        RecommendationCanonicalPayload.Encoded encoded;
        try {
            collected = collector.collect(envelope.input());
            encoded = canonicalPayload.encode(collected);
        } catch (RuntimeException exception) {
            return new Evaluation(
                    "invalid_snapshot",
                    List.of("collector_failure"),
                    null,
                    0,
                    0);
        }

        String actualFingerprint = RecommendationHashing.sha256(encoded.bytes());
        List<String> mismatches = new ArrayList<>();
        if (!actualFingerprint.equals(bundle.run().resultFingerprint())) {
            mismatches.add("result_fingerprint");
        }
        if (!Arrays.equals(encoded.bytes(), bundle.rankingResult().canonicalPayload())) {
            mismatches.add("result_snapshot");
        }
        if (!rankedCandidatesMatch(bundle, collected)) {
            mismatches.add("ranked_candidates");
        }
        if (!terminalCandidatesMatch(bundle, collected)) {
            mismatches.add("terminal_candidates");
        }
        return mismatches.isEmpty()
                ? new Evaluation(
                        "exact_match",
                        List.of(),
                        actualFingerprint,
                        collected.finalCandidates().size(),
                        collected.terminalCandidates().size())
                : new Evaluation(
                        "mismatch",
                        List.copyOf(mismatches),
                        actualFingerprint,
                        collected.finalCandidates().size(),
                        collected.terminalCandidates().size());
    }

    private List<String> validateSnapshots(ReplayBundle bundle) {
        List<String> failures = new ArrayList<>();
        validateSnapshot(bundle.rankingInput(), SnapshotKind.RANKING_INPUT_V1, "ranking_input", failures);
        validateSnapshot(bundle.diversityMetadata(), SnapshotKind.DIVERSITY_METADATA_V1, "diversity_metadata", failures);
        validateSnapshot(bundle.explorationMetadata(), SnapshotKind.EXPLORATION_METADATA_V1, "exploration_metadata", failures);
        validateSnapshot(bundle.rankingResult(), SnapshotKind.RANKING_RESULT_V1, "ranking_result", failures);
        return List.copyOf(failures);
    }

    private void validateSnapshot(
            StoredSnapshot snapshot,
            SnapshotKind kind,
            String category,
            List<String> failures) {
        if (!snapshot.snapshotKind().equals(kind.value())) {
            failures.add(category + "_kind");
            return;
        }
        String hash = RecommendationHashing.snapshotSha256(
                snapshot.snapshotKind(),
                snapshot.schemaVersion(),
                snapshot.canonicalPayload());
        if (!hash.equals(snapshot.contentHash())) {
            failures.add(category + "_hash");
        }
        try {
            JsonNode payload = objectMapper.readTree(snapshot.payloadJson());
            byte[] canonical = canonicalPayload.encode(payload).bytes();
            if (!Arrays.equals(canonical, snapshot.canonicalPayload())) {
                failures.add(category + "_canonical");
            }
        } catch (Exception exception) {
            failures.add(category + "_json");
        }
    }

    private List<String> validateBindings(ReplayBundle bundle, RankingInputEnvelope envelope) {
        var run = bundle.run();
        var input = envelope.input();
        List<String> failures = new ArrayList<>();
        try {
            if (!run.referenceTime().equals(Instant.parse(envelope.referenceTime()))) {
                failures.add("reference_time");
            }
        } catch (RuntimeException exception) {
            failures.add("reference_time");
        }
        if (!input.userId().equals(Long.toString(run.userId()))) {
            failures.add("user_id");
        }
        if (!input.contextId().equals(run.contextId())) {
            failures.add("context_id");
        }
        if (!input.rankingSnapshotId().equals(run.rankingSnapshotId())) {
            failures.add("ranking_snapshot_id");
        }
        if (!input.metadataSnapshotId().equals(run.metadataSnapshotId())) {
            failures.add("metadata_snapshot_id");
        }
        if (!input.explorationSnapshotId().equals(run.explorationSnapshotId())) {
            failures.add("exploration_snapshot_id");
        }
        if (!input.scorePolicyVersion().equals(run.scorePolicyVersion())) {
            failures.add("score_policy_version");
        }
        if (!input.explorationSeed().equals(run.explorationSeed())) {
            failures.add("exploration_seed");
        }
        if (!canonicalEquals(input.candidateMetadata(), bundle.diversityMetadata())) {
            failures.add("diversity_metadata_binding");
        }
        if (!canonicalEquals(input.explorationMetadata(), bundle.explorationMetadata())) {
            failures.add("exploration_metadata_binding");
        }
        if (!input.policy().policyVersion().equals(run.rankingPolicyVersion())) {
            failures.add("ranking_policy_version");
        }
        if (!input.policy().baseIntegrationPolicyVersion().equals(run.baseIntegrationPolicyVersion())) {
            failures.add("base_integration_policy_version");
        }
        if (!input.policy().baseRankingPolicyVersion().equals(run.baseRankingPolicyVersion())) {
            failures.add("base_ranking_policy_version");
        }
        if (!input.diversityPolicy().policyVersion().equals(run.diversityPolicyVersion())) {
            failures.add("diversity_policy_version");
        }
        if (!input.explorationPolicy().policyVersion().equals(run.explorationPolicyVersion())) {
            failures.add("exploration_policy_version");
        }
        if (!componentPolicyVersionsMatch(input, run.componentPolicyVersionsJson())) {
            failures.add("component_policy_versions");
        }
        return List.copyOf(failures);
    }

    private boolean rankedCandidatesMatch(ReplayBundle bundle, CollectedRankingV3Result collected) {
        if (bundle.rankedCandidates().size() != collected.finalCandidates().size()) {
            return false;
        }
        for (int index = 0; index < collected.finalCandidates().size(); index++) {
            var expected = collected.finalCandidates().get(index);
            var stored = bundle.rankedCandidates().get(index);
            if (stored.absoluteRank() != expected.absoluteRank()
                    || stored.sourceEntityId() != numericEntityId(expected.entityId())
                    || !stored.origin().equals(expected.origin().wireValue())
                    || !stored.scorePolicyVersion().equals(scorePolicyVersion(expected))
                    || !jsonEquals(stored.provenanceJson(), expectedProvenance(expected))) {
                return false;
            }
            if (expected instanceof PersonalizedExplorationCandidate personalized) {
                if (!equalDouble(stored.score(), personalized.score())
                        || stored.scoreIsNegativeZero() != isNegativeZero(personalized.score())
                        || !Objects.equals(stored.baseAbsoluteRank(), personalized.baseAbsoluteRank())
                        || !Objects.equals(stored.diversifiedAbsoluteRank(), personalized.diversifiedAbsoluteRank())) {
                    return false;
                }
            } else if (expected instanceof InsertedExplorationCandidate inserted) {
                if (!equalDouble(stored.explorationQualityScore(), inserted.explorationQualityScore())
                        || !Objects.equals(stored.recentExposureCount(), inserted.recentExposureCount())
                        || !Objects.equals(stored.seededTieBreakKey(), inserted.seededTieBreakKey())
                        || !Objects.equals(stored.explorationPoolRank(), inserted.explorationPoolRank())
                        || !Objects.equals(stored.targetInsertionRank(), inserted.targetInsertionRank())) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean terminalCandidatesMatch(ReplayBundle bundle, CollectedRankingV3Result collected) {
        if (bundle.terminalCandidates().size() != collected.terminalCandidates().size()) {
            return false;
        }
        var expected = collected.terminalCandidates().stream()
                .sorted(Comparator.comparing(item -> numericEntityId(item.entityId())))
                .toList();
        for (int index = 0; index < expected.size(); index++) {
            var item = expected.get(index);
            var stored = bundle.terminalCandidates().get(index);
            if (stored.sourceEntityId() != numericEntityId(item.entityId())
                    || !stored.scoreStatus().equals(item.status().wireValue())
                    || !Objects.equals(
                            stored.notApplicableReason(),
                            item.notApplicableReason() == null ? null : item.notApplicableReason().wireValue())
                    || !Objects.equals(
                            stored.hardExclusionReason(),
                            item.hardExclusionReason() == null ? null : item.hardExclusionReason().wireValue())
                    || !stored.scorePolicyVersion().equals(item.scorePolicyVersion())
                    || !jsonEquals(stored.auditPayloadJson(), expectedTerminalAudit(item))) {
                return false;
            }
        }
        return true;
    }

    private boolean canonicalEquals(Object expected, StoredSnapshot stored) {
        return Arrays.equals(canonicalPayload.encode(expected).bytes(), stored.canonicalPayload());
    }

    private boolean componentPolicyVersionsMatch(
            RankingV3ReplayInputSnapshot input,
            String storedJson) {
        Map<String, String> expected = Map.of(
                "context", input.componentPolicyVersions().contextMatch(),
                "interest", input.componentPolicyVersions().interestMatch(),
                "freshness", input.componentPolicyVersions().freshness(),
                "popularity", input.componentPolicyVersions().popularity());
        return jsonEquals(storedJson, expected);
    }

    private boolean jsonEquals(String storedJson, Object expected) {
        try {
            JsonNode stored = objectMapper.readTree(storedJson);
            return Arrays.equals(
                    canonicalPayload.encode(stored).bytes(),
                    canonicalPayload.encode(expected).bytes());
        } catch (Exception exception) {
            return false;
        }
    }

    private static String scorePolicyVersion(
            com.jc.recommendation.model.exploration.ExplorationFinalCandidate candidate) {
        if (candidate instanceof PersonalizedExplorationCandidate personalized) {
            return personalized.scorePolicyVersion();
        }
        if (candidate instanceof InsertedExplorationCandidate inserted) {
            return inserted.scorePolicyVersion();
        }
        throw new IllegalStateException("Unknown replay candidate " + candidate.getClass().getName());
    }

    private static Map<String, Object> expectedTerminalAudit(
            com.jc.recommendation.model.ranking.TerminalCandidateAudit item) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("entityType", item.entityType());
        audit.put("status", item.status());
        audit.put("notApplicableReason", item.notApplicableReason());
        audit.put("hardExclusionReason", item.hardExclusionReason());
        return audit;
    }

    private static Map<String, Object> expectedProvenance(
            com.jc.recommendation.model.exploration.ExplorationFinalCandidate candidate) {
        Map<String, Object> provenance = new LinkedHashMap<>();
        if (candidate instanceof PersonalizedExplorationCandidate personalized) {
            provenance.put("compositionMode", personalized.compositionMode());
            provenance.put("selectionReason", personalized.selectionReason());
            provenance.put("appliedRelaxations", personalized.appliedRelaxations());
            provenance.put("violatedDimensionsAtSelection", personalized.violatedDimensionsAtSelection());
            provenance.put("displacement", personalized.displacement());
            provenance.put("promotionDistance", personalized.promotionDistance());
            provenance.put("demotionDistance", personalized.demotionDistance());
            return provenance;
        }
        if (candidate instanceof InsertedExplorationCandidate inserted) {
            provenance.put("sourceStatus", inserted.sourceStatus());
            provenance.put("sourceNotApplicableReason", inserted.sourceNotApplicableReason());
            provenance.put("qualityEvidence", inserted.qualityEvidence());
            provenance.put("availableWeightTotal", inserted.availableWeightTotal());
            provenance.put("freshnessRawScore", inserted.freshnessRawScore());
            provenance.put("popularityRawScore", inserted.popularityRawScore());
            provenance.put("explorationPolicyVersion", inserted.explorationPolicyVersion());
            return provenance;
        }
        throw new IllegalStateException("Unknown replay candidate " + candidate.getClass().getName());
    }

    private static long numericEntityId(String value) {
        return Long.parseLong(value);
    }

    private static boolean equalDouble(Double left, double right) {
        return left != null && Double.doubleToRawLongBits(left) == Double.doubleToRawLongBits(right);
    }

    private static boolean isNegativeZero(double value) {
        return Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(-0.0d);
    }

    private static String auditId(String runId, String evaluatorVersion, String buildId) {
        return "replay:" + RecommendationHashing.sha256(
                (runId + "\u0000" + evaluatorVersion + "\u0000" + buildId)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private record RankingInputEnvelope(
            String referenceTime,
            RankingV3ReplayInputSnapshot input) {
        private RankingInputEnvelope {
            Objects.requireNonNull(referenceTime, "referenceTime");
            Objects.requireNonNull(input, "input");
        }
    }

    private record Evaluation(
            String status,
            List<String> categories,
            String actualFingerprint,
            int rankedCount,
            int terminalCount) {
    }

    public record ReplayAuditResult(
            String auditId,
            String runId,
            boolean exactMatch,
            String status,
            List<String> mismatchCategories,
            String actualResultFingerprint) {
        public ReplayAuditResult {
            mismatchCategories = List.copyOf(mismatchCategories);
        }
    }
}
