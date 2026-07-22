package com.jc.data.contract.v1.projection;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record RecommendationProfileInputProjection(
        String recordRef,
        String subjectRef,
        Instant projectionAsOf,
        String sourceCheckpointRef,
        String profileSchemaVersion,
        String projectionPolicyVersion,
        int activityWindowDays,
        Map<String, Long> interactionCounts,
        List<RankedReference> recentRegions,
        List<RankedReference> recentContentRefs,
        List<RankedReference> recentTagRefs,
        Map<String, Long> engagementSignals,
        Map<String, Long> negativeSignals,
        long sourceEventCount,
        String sourceLineageFingerprint,
        String projectionRecordFingerprint) implements ProjectionRecord {

    public RecommendationProfileInputProjection {
        recordRef = ProjectionEngineSupport.requireReference(recordRef, "recordRef");
        subjectRef = ProjectionEngineSupport.requireSubject(subjectRef, "subjectRef");
        Objects.requireNonNull(projectionAsOf, "projectionAsOf");
        sourceCheckpointRef = ProjectionEngineSupport.requireReference(
                sourceCheckpointRef, "sourceCheckpointRef");
        profileSchemaVersion = ProjectionEngineSupport.requireVersion(profileSchemaVersion, "profileSchemaVersion");
        projectionPolicyVersion = ProjectionEngineSupport.requireVersion(
                projectionPolicyVersion, "projectionPolicyVersion");
        if (!List.of(7, 30, 90).contains(activityWindowDays)) {
            throw new IllegalArgumentException("activityWindowDays must be 7, 30 or 90");
        }
        interactionCounts = immutableCounts(interactionCounts, "interactionCounts");
        recentRegions = List.copyOf(Objects.requireNonNull(recentRegions, "recentRegions"));
        recentContentRefs = List.copyOf(Objects.requireNonNull(recentContentRefs, "recentContentRefs"));
        recentTagRefs = List.copyOf(Objects.requireNonNull(recentTagRefs, "recentTagRefs"));
        engagementSignals = immutableCounts(engagementSignals, "engagementSignals");
        negativeSignals = immutableCounts(negativeSignals, "negativeSignals");
        if (sourceEventCount < 0) {
            throw new IllegalArgumentException("sourceEventCount cannot be negative");
        }
        sourceLineageFingerprint = ProjectionEngineSupport.requireFingerprint(
                sourceLineageFingerprint, "sourceLineageFingerprint");
        projectionRecordFingerprint = ProjectionEngineSupport.requireFingerprint(
                projectionRecordFingerprint, "projectionRecordFingerprint");
    }

    @Override
    public String projectionName() {
        return ProjectionDefinition.PROFILE_NAME;
    }

    @Override
    public Map<String, Object> canonicalFields() {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("projectionName", projectionName());
        fields.put("subjectRef", subjectRef);
        fields.put("projectionAsOf", projectionAsOf);
        fields.put("sourceCheckpointRef", sourceCheckpointRef);
        fields.put("profileSchemaVersion", profileSchemaVersion);
        fields.put("projectionPolicyVersion", projectionPolicyVersion);
        fields.put("activityWindowDays", activityWindowDays);
        fields.put("interactionCounts", interactionCounts);
        fields.put("recentRegions", recentRegions.stream().map(RankedReference::canonicalFields).toList());
        fields.put("recentContentRefs", recentContentRefs.stream().map(RankedReference::canonicalFields).toList());
        fields.put("recentTagRefs", recentTagRefs.stream().map(RankedReference::canonicalFields).toList());
        fields.put("engagementSignals", engagementSignals);
        fields.put("negativeSignals", negativeSignals);
        fields.put("sourceEventCount", sourceEventCount);
        fields.put("sourceLineageFingerprint", sourceLineageFingerprint);
        return Map.copyOf(fields);
    }

    private static Map<String, Long> immutableCounts(Map<String, Long> source, String field) {
        Objects.requireNonNull(source, field);
        TreeMap<String, Long> copy = new TreeMap<>();
        for (Map.Entry<String, Long> entry : source.entrySet()) {
            String key = ProjectionEngineSupport.requireToken(entry.getKey(), field + " key", 80);
            Long value = Objects.requireNonNull(entry.getValue(), field + " value");
            if (value.longValue() < 0) {
                throw new IllegalArgumentException(field + " values cannot be negative");
            }
            copy.put(key, value);
        }
        return Map.copyOf(copy);
    }
}
