package com.jc.recommendation.offline;

import com.jc.recommendation.evaluation.RecommendationBehaviorEventResolver;
import com.jc.recommendation.evaluation.RecommendationOutcomeAttributor;
import com.jc.recommendation.exposure.RecommendationExposureEventResolver;
import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesInput;
import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.CaseEvaluationResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.CompareInput;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.PolicyComparisonResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.PolicyVector;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.RecommendationOfflineEvaluationCase;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayStatus;

public final class RecommendationOfflineEvaluationEngine {
    private final RecommendationReplayEvaluator replayEvaluator = new RecommendationReplayEvaluator();
    private final RecommendationExposureEventResolver exposureResolver = new RecommendationExposureEventResolver();
    private final RecommendationBehaviorEventResolver behaviorResolver = new RecommendationBehaviorEventResolver();
    private final RecommendationOutcomeAttributor outcomeAttributor = new RecommendationOutcomeAttributor();
    private final RecommendationPolicyComparator policyComparator = new RecommendationPolicyComparator();

    public CaseEvaluationResult evaluateCase(
            RecommendationOfflineEvaluationCase evaluationCase,
            PolicyVector treatmentPolicyVector
    ) {
        ReplayResult replay = replayEvaluator.evaluate(evaluationCase);
        if (replay.status() != ReplayStatus.EXACT_MATCH
                && replay.status() != ReplayStatus.PARTIAL_OBSERVATION) {
            return new CaseEvaluationResult(replay, null, null);
        }
        AttributeRecommendationOutcomesResult attribution = outcomeAttributor.attribute(
                new AttributeRecommendationOutcomesInput(
                        evaluationCase.caseId(),
                        exposureResolver.resolve(evaluationCase.exposureEvents()),
                        behaviorResolver.resolve(evaluationCase.behaviorEvents()),
                        evaluationCase.evaluationCutoffAt()));
        PolicyComparisonResult comparison = policyComparator.compare(
                new CompareInput(evaluationCase, replay, treatmentPolicyVector, attribution));
        return new CaseEvaluationResult(replay, attribution, comparison);
    }
}
