package com.jc.backend.intelligence.compat.recommendation;

import static com.jc.intelligence.contract.v1.compatibility.CompatibilityClassification.ADAPTER_COMPATIBLE;
import static com.jc.intelligence.contract.v1.compatibility.CompatibilityClassification.EXACT_COMPATIBLE;
import static com.jc.intelligence.contract.v1.compatibility.CompatibilityClassification.FUTURE_VERSION_MIGRATION_REQUIRED;
import static com.jc.intelligence.contract.v1.compatibility.CompatibilityClassification.INTENTIONALLY_DOMAIN_SPECIFIC;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecommendationCompatibilityClassifierV1 {
    private static final List<RecommendationCompatibilityEntryV1> ENTRIES = List.of(
            entry("recommendation_run", ADAPTER_COMPATIBLE, "intelligence-run-v1",
                    "existing row remains authoritative; read-only adapter"),
            entry("ranking_input_v1", ADAPTER_COMPATIBLE, "intelligence-input-snapshot-v1",
                    "existing bytes and hash are not rewritten"),
            entry("diversity_metadata_v1", ADAPTER_COMPATIBLE, "candidate snapshot dependency",
                    "recommendation-specific metadata remains domain-owned"),
            entry("exploration_metadata_v1", ADAPTER_COMPATIBLE, "candidate snapshot dependency",
                    "recommendation-specific metadata remains domain-owned"),
            entry("ranking_result_v1", ADAPTER_COMPATIBLE, "intelligence-output-snapshot-v1",
                    "existing ordered ranking and fingerprint remain unchanged"),
            entry("exposure_event_v1", ADAPTER_COMPATIBLE, "exposure evidence snapshot role",
                    "general exposure authority remains separate"),
            entry("candidate_terminal_partition", INTENTIONALLY_DOMAIN_SPECIFIC,
                    "recommendation candidate extension", "ranked/terminal partition preserved"),
            entry("recommendation_exposure_event", INTENTIONALLY_DOMAIN_SPECIFIC,
                    "recommendation_general_exposure_v1", "not a P2 experiment denominator"),
            entry("recommendation_behavior_event", INTENTIONALLY_DOMAIN_SPECIFIC,
                    "recommendation behavior fact", "impression is not general/P2 exposure"),
            entry("recommendation_p1_profile_snapshot", ADAPTER_COMPATIBLE,
                    "profile/feature evidence reference", "existing feature IDs and fingerprint preserved"),
            entry("recommendation_p1_policy_assignment", INTENTIONALLY_DOMAIN_SPECIFIC,
                    "P1 policy evidence", "not merged into common run fields"),
            entry("recommendation_p1_comparison", INTENTIONALLY_DOMAIN_SPECIFIC,
                    "P1 comparison evidence", "not merged into common run fields"),
            entry("recommendation_p2_experiment_assignment", ADAPTER_COMPATIBLE,
                    "Reliability assignment reference", "physical writer remains recommendation P2"),
            entry("recommendation_p2_experiment_exposure", EXACT_COMPATIBLE,
                    "recommendation_p2_experiment_exposure_v1",
                    "authoritative P2 exposure and denominator source"),
            entry("recommendation_p2_dataset_evaluation_gates", INTENTIONALLY_DOMAIN_SPECIFIC,
                    "Reliability evidence", "metric and Gate A-E semantics preserved"),
            entry("recommendation_p0_numeric_post_id", FUTURE_VERSION_MIGRATION_REQUIRED,
                    "entityRef=post:<id>", "boundary-only adapter; legacy bytes remain unchanged"));

    private static final Map<String, RecommendationCompatibilityEntryV1> BY_SOURCE;

    static {
        LinkedHashMap<String, RecommendationCompatibilityEntryV1> values = new LinkedHashMap<>();
        for (RecommendationCompatibilityEntryV1 entry : ENTRIES) {
            if (values.put(entry.sourceObject(), entry) != null) {
                throw new IllegalStateException("Duplicate compatibility entry: " + entry.sourceObject());
            }
        }
        BY_SOURCE = java.util.Collections.unmodifiableMap(values);
    }

    public RecommendationCompatibilityEntryV1 classify(String sourceObject) {
        RecommendationCompatibilityEntryV1 entry = BY_SOURCE.get(sourceObject);
        if (entry == null) {
            throw new IllegalArgumentException("Unregistered recommendation compatibility source: " + sourceObject);
        }
        return entry;
    }

    public List<RecommendationCompatibilityEntryV1> entries() {
        return ENTRIES;
    }

    private static RecommendationCompatibilityEntryV1 entry(
            String sourceObject,
            com.jc.intelligence.contract.v1.compatibility.CompatibilityClassification classification,
            String commonContractOrMeaning,
            String authorityNote) {
        return new RecommendationCompatibilityEntryV1(
                sourceObject, classification, commonContractOrMeaning, authorityNote);
    }
}
