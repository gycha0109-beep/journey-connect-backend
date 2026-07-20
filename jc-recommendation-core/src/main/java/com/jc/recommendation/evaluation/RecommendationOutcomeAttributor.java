package com.jc.recommendation.evaluation;

import com.jc.recommendation.exposure.RecommendationExposureEventResolver;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesInput;
import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesResult;
import com.jc.recommendation.model.evaluation.RecommendationAttributionAuditCategory;
import com.jc.recommendation.model.evaluation.RecommendationOutcomeAttribution;
import com.jc.recommendation.model.evaluation.RecommendationOutcomeAttributionAudit;
import com.jc.recommendation.model.evaluation.ResolveRecommendationBehaviorEventsResult;
import com.jc.recommendation.model.evaluation.ResolvedRecommendationBehaviorEvent;
import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.model.event.UserBehaviorEvent;
import com.jc.recommendation.model.event.UserBehaviorEventMetadata;
import com.jc.recommendation.model.exposure.RecommendationExposureEventV1;
import com.jc.recommendation.model.exposure.ResolveRecommendationExposureEventsResult;
import com.jc.recommendation.policy.FoundationPolicies;
import com.jc.recommendation.policy.OfflineEvaluationPolicies;
import com.jc.recommendation.support.StrictUtcMilliseconds;
import com.jc.recommendation.support.Utf16CodeUnitComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecommendationOutcomeAttributor {
    private static final List<RecommendationAttributionAuditCategory> AUDIT_ORDER = List.of(
            RecommendationAttributionAuditCategory.MISSING_RECOMMENDATION_RUN,
            RecommendationAttributionAuditCategory.ANONYMOUS_USER,
            RecommendationAttributionAuditCategory.RUN_USER_SESSION_MISMATCH,
            RecommendationAttributionAuditCategory.UNSUPPORTED_EVENT_TYPE,
            RecommendationAttributionAuditCategory.MISSING_ENTITY_ID,
            RecommendationAttributionAuditCategory.UNMATCHED_ENTITY,
            RecommendationAttributionAuditCategory.AMBIGUOUS_ENTITY_IDENTITY,
            RecommendationAttributionAuditCategory.AFTER_CUTOFF,
            RecommendationAttributionAuditCategory.BEFORE_EXPOSURE,
            RecommendationAttributionAuditCategory.OUTSIDE_ATTRIBUTION_WINDOW,
            RecommendationAttributionAuditCategory.ATTRIBUTED
    );

    public AttributeRecommendationOutcomesResult attribute(AttributeRecommendationOutcomesInput input) {
        if (input == null) {
            invalid("input must be a plain object");
        }
        nonblank(input.caseId(), "caseId");
        long cutoffEpoch = parse(input.evaluationCutoffAt(), "evaluationCutoffAt");
        List<RecommendationExposureEventV1> exposures = validateExposureResult(input.exposureResult());
        List<ResolvedRecommendationBehaviorEvent> behaviors = validateBehaviorResult(input.behaviorResult());
        if (exposures.isEmpty()) {
            invalid("at least one exposure is required");
        }
        for (RecommendationExposureEventV1 exposure : exposures) {
            if (parse(exposure.servedAt(), "servedAt") > cutoffEpoch) {
                invalid("exposure after cutoff");
            }
        }
        Set<String> runIds = new HashSet<>();
        Set<String> replayKeys = new HashSet<>();
        for (RecommendationExposureEventV1 exposure : exposures) {
            runIds.add(exposure.recommendationRunId());
            replayKeys.add(exposure.replayKey());
        }
        if (runIds.size() != 1) {
            invalid("attribution is case-scoped to exactly one recommendation run");
        }
        if (replayKeys.size() != 1) {
            invalid("attribution requires one replay key");
        }
        String recommendationRunId = runIds.iterator().next();
        String replayKey = replayKeys.iterator().next();
        RecommendationExposureEventV1 firstExposure = exposures.getFirst();

        Map<String, Set<RecommendationEntityType>> byEntityId = new HashMap<>();
        for (RecommendationExposureEventV1 exposure : exposures) {
            for (var candidate : exposure.candidates()) {
                byEntityId.computeIfAbsent(candidate.entityId(), ignored -> new HashSet<>())
                        .add(candidate.entityType());
            }
        }

        List<RecommendationOutcomeAttributionAudit> audits = new ArrayList<>();
        List<RecommendationOutcomeAttribution> attributions = new ArrayList<>();
        for (ResolvedRecommendationBehaviorEvent behavior : behaviors) {
            RecommendationAttributionAuditCategory category;
            RecommendationEntityType entityType = null;
            RecommendationExposureEventV1 selected = null;
            if (behavior.recommendationRunId() == null) {
                category = RecommendationAttributionAuditCategory.MISSING_RECOMMENDATION_RUN;
            } else if (behavior.userId() == null) {
                category = RecommendationAttributionAuditCategory.ANONYMOUS_USER;
            } else if (!behavior.recommendationRunId().equals(recommendationRunId)
                    || !behavior.userId().equals(firstExposure.userId())
                    || !behavior.sessionId().equals(firstExposure.sessionId())) {
                category = RecommendationAttributionAuditCategory.RUN_USER_SESSION_MISMATCH;
            } else if (behavior.eventType() == EventType.IMPRESSION || behavior.eventType() == EventType.SEARCH) {
                category = RecommendationAttributionAuditCategory.UNSUPPORTED_EVENT_TYPE;
            } else if (behavior.entityId() == null) {
                category = RecommendationAttributionAuditCategory.MISSING_ENTITY_ID;
            } else {
                Set<RecommendationEntityType> identities = byEntityId.getOrDefault(
                        behavior.entityId(), Set.of());
                if (identities.isEmpty()) {
                    category = RecommendationAttributionAuditCategory.UNMATCHED_ENTITY;
                } else if (identities.size() > 1) {
                    category = RecommendationAttributionAuditCategory.AMBIGUOUS_ENTITY_IDENTITY;
                } else {
                    entityType = identities.iterator().next();
                    long behaviorEpoch = parse(behavior.occurredAt(), "occurredAt");
                    if (behaviorEpoch > cutoffEpoch) {
                        category = RecommendationAttributionAuditCategory.AFTER_CUTOFF;
                    } else {
                        List<RecommendationExposureEventV1> preceding = new ArrayList<>();
                        for (RecommendationExposureEventV1 exposure : exposures) {
                            if (includesIdentity(exposure, entityType, behavior.entityId())
                                    && parse(exposure.servedAt(), "servedAt") <= behaviorEpoch) {
                                preceding.add(exposure);
                            }
                        }
                        preceding.sort(Comparator
                                .comparingLong((RecommendationExposureEventV1 event) ->
                                        parse(event.servedAt(), "servedAt")).reversed()
                                .thenComparing(RecommendationExposureEventV1::servedAt,
                                        Utf16CodeUnitComparator.ASCENDING.reversed())
                                .thenComparing(RecommendationExposureEventV1::eventId,
                                        Utf16CodeUnitComparator.ASCENDING.reversed()));
                        if (preceding.isEmpty()) {
                            category = RecommendationAttributionAuditCategory.BEFORE_EXPOSURE;
                        } else {
                            selected = preceding.getFirst();
                            Long window = OfflineEvaluationPolicies.V1.attributionWindowMsByEventType()
                                    .get(behavior.eventType());
                            if (window == null) {
                                invalid("unsupported attribution event type");
                            }
                            long effectiveEnd = Math.min(cutoffEpoch,
                                    parse(selected.servedAt(), "servedAt") + window);
                            category = behaviorEpoch <= effectiveEnd
                                    ? RecommendationAttributionAuditCategory.ATTRIBUTED
                                    : RecommendationAttributionAuditCategory.OUTSIDE_ATTRIBUTION_WINDOW;
                        }
                    }
                }
            }
            audits.add(new RecommendationOutcomeAttributionAudit(
                    behavior.eventId(), category, behavior.recommendationRunId(), behavior.entityId(), entityType,
                    category == RecommendationAttributionAuditCategory.ATTRIBUTED ? selected.eventId() : null
            ));
            if (category == RecommendationAttributionAuditCategory.ATTRIBUTED) {
                Double value = FoundationPolicies.EVENT_WEIGHT_V1.weights().get(behavior.eventType());
                long elapsed = parse(behavior.occurredAt(), "occurredAt")
                        - parse(selected.servedAt(), "servedAt");
                long window = OfflineEvaluationPolicies.V1.attributionWindowMsByEventType()
                        .get(behavior.eventType());
                attributions.add(new RecommendationOutcomeAttribution(
                        behavior.eventId(), behavior.eventType(), recommendationRunId, selected.eventId(),
                        entityType, behavior.entityId(), selected.servedAt(), behavior.occurredAt(), elapsed, window,
                        value, value != null && value > 0.0d, value != null && value < 0.0d,
                        behavior.eventType() == EventType.REPORT
                ));
            }
        }

        EnumMap<RecommendationAttributionAuditCategory, Integer> auditCounts =
                new EnumMap<>(RecommendationAttributionAuditCategory.class);
        for (RecommendationAttributionAuditCategory category : AUDIT_ORDER) {
            auditCounts.put(category, 0);
        }
        for (RecommendationOutcomeAttributionAudit audit : audits) {
            auditCounts.merge(audit.category(), 1, Integer::sum);
        }
        int numericCount = 0;
        double associated = 0.0d;
        int clickCount = 0;
        int positiveCount = 0;
        int negativeCount = 0;
        int severeCount = 0;
        for (RecommendationOutcomeAttribution attribution : attributions) {
            if (attribution.associatedOutcomeValue() != null) {
                numericCount++;
                associated += attribution.associatedOutcomeValue();
            }
            if (attribution.behaviorEventType() == EventType.CLICK) clickCount++;
            if (attribution.isPositive()) positiveCount++;
            if (attribution.isNegative()) negativeCount++;
            if (attribution.isSevereReport()) severeCount++;
        }
        associated = associated == 0.0d ? 0.0d : associated;
        AttributeRecommendationOutcomesResult result = new AttributeRecommendationOutcomesResult(
                input.caseId(), recommendationRunId, replayKey, behaviors.size(), attributions.size(), numericCount,
                associated, clickCount, positiveCount, negativeCount, severeCount,
                auditCounts.get(RecommendationAttributionAuditCategory.AMBIGUOUS_ENTITY_IDENTITY),
                auditCounts.get(RecommendationAttributionAuditCategory.UNMATCHED_ENTITY),
                auditCounts.get(RecommendationAttributionAuditCategory.RUN_USER_SESSION_MISMATCH),
                auditCounts, attributions, audits
        );
        if (audits.size() != behaviors.size()
                || auditCounts.get(RecommendationAttributionAuditCategory.ATTRIBUTED) != attributions.size()
                || numericCount != result.attributedNumericEventCount()) {
            invalid("output invariant");
        }
        return result;
    }

    private static List<RecommendationExposureEventV1> validateExposureResult(
            ResolveRecommendationExposureEventsResult result
    ) {
        if (result.inputCount() != result.resolvedCount() + result.duplicateCount()
                || result.resolvedEvents().size() != result.resolvedCount()
                || result.duplicateAudits().size() != result.duplicateCount()) {
            invalid("exposure count invariant");
        }
        var reread = new RecommendationExposureEventResolver().resolve(result.resolvedEvents());
        if (reread.duplicateCount() != 0 || reread.resolvedCount() != result.resolvedCount()) {
            invalid("resolved exposure contract");
        }
        return result.resolvedEvents();
    }

    private static List<ResolvedRecommendationBehaviorEvent> validateBehaviorResult(
            ResolveRecommendationBehaviorEventsResult result
    ) {
        if (result.inputCount() != result.resolvedCount() + result.duplicateCount()
                || result.resolvedEvents().size() != result.resolvedCount()
                || result.duplicateAudits().size() != result.duplicateCount()) {
            invalid("behavior count invariant");
        }
        List<UserBehaviorEvent> raw = result.resolvedEvents().stream().map(event -> new UserBehaviorEvent(
                event.eventId(), event.idempotencyKey(), event.userId(), event.sessionId(), event.eventType(),
                event.entityId(), event.recommendationRunId(), UserBehaviorEventMetadata.empty(),
                event.occurredAt()
        )).toList();
        var reread = new RecommendationBehaviorEventResolver().resolve(raw);
        if (reread.duplicateCount() != 0 || reread.resolvedCount() != result.resolvedCount()) {
            invalid("resolved behavior contract");
        }
        return result.resolvedEvents();
    }

    private static boolean includesIdentity(
            RecommendationExposureEventV1 event,
            RecommendationEntityType type,
            String entityId
    ) {
        return event.candidates().stream().anyMatch(candidate -> candidate.entityType() == type
                && candidate.entityId().equals(entityId));
    }

    private static long parse(String value, String label) {
        try {
            return StrictUtcMilliseconds.parseEpochMilli(value, label);
        } catch (IllegalArgumentException exception) {
            invalid(label + " must be strict UTC");
            return 0L;
        }
    }

    private static void nonblank(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            invalid(label + " must be nonblank");
        }
    }

    private static void invalid(String detail) {
        throw new IllegalArgumentException("INVALID_OUTCOME_ATTRIBUTION_INPUT: " + detail);
    }
}
