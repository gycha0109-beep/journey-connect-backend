package com.jc.backend.recommendation.application;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.recommendation.config.RecommendationProperties;
import com.jc.backend.recommendation.persistence.RecommendationReplayAuditStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

/** Read-only gate: it reports readiness evidence and never changes recommendation mode. */
@Service
public class RecommendationCanaryReadinessService {

    private final RecommendationProperties properties;
    private final RecommendationReplayAuditStore auditStore;

    public RecommendationCanaryReadinessService(
            RecommendationProperties properties,
            RecommendationReplayAuditStore auditStore) {
        this.properties = properties;
        this.auditStore = auditStore;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public ReadinessResult evaluate() {
        var evidence = auditStore.findRecentShadowEvidence(
                properties.getReadinessLookbackRuns(),
                properties.getReplayEvaluatorVersion(),
                properties.getCoreBuildId());
        List<String> blockers = new ArrayList<>();
        if (evidence.size() < properties.getReadinessMinimumShadowRuns()) {
            blockers.add("insufficient_shadow_runs");
        }
        if (evidence.stream().anyMatch(item -> item.replayStatus() == null)) {
            blockers.add("missing_replay_audit");
        }
        if (evidence.stream().anyMatch(item -> item.replayStatus() != null
                && !item.replayStatus().equals("exact_match"))) {
            blockers.add("non_exact_replay");
        }
        if (evidence.stream().anyMatch(item -> !item.runStatus().equals("succeeded"))) {
            blockers.add("failed_or_fallback_run");
        }
        long p95 = percentile95(evidence.stream()
                .map(RecommendationReplayAuditStore.ReadinessEvidence::durationMs)
                .toList());
        if (!evidence.isEmpty() && p95 > properties.getReadinessMaxP95DurationMs()) {
            blockers.add("p95_latency_exceeded");
        }
        return new ReadinessResult(blockers.isEmpty(), evidence.size(), p95, List.copyOf(blockers));
    }

    private static long percentile95(List<Long> values) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> ordered = new ArrayList<>(values);
        Collections.sort(ordered);
        int index = Math.max(0, (int) Math.ceil(ordered.size() * 0.95d) - 1);
        return ordered.get(index);
    }

    public record ReadinessResult(
            boolean ready,
            int evaluatedShadowRuns,
            long p95DurationMs,
            List<String> blockers) {
        public ReadinessResult {
            blockers = List.copyOf(blockers);
        }
    }
}
