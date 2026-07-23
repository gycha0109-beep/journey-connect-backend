package com.jc.data.contract.v1.quality;

import com.jc.data.contract.v1.projection.ExperimentExposureBinding;
import com.jc.data.contract.v1.projection.ExperimentOutcomeInputProjection;
import com.jc.data.contract.v1.projection.ExperimentOutcomeProjectionEngine;
import com.jc.data.contract.v1.projection.IdentityBinding;
import com.jc.data.contract.v1.projection.ProjectionDefinition;
import com.jc.data.contract.v1.projection.ProjectionIdentifiers;
import com.jc.data.contract.v1.projection.ProjectionRecord;
import com.jc.data.contract.v1.projection.ProjectionResult;
import com.jc.data.contract.v1.projection.RecommendationProfileInputProjection;
import com.jc.data.contract.v1.projection.RecommendationProfileProjectionEngine;
import java.util.Comparator;
import java.util.List;

final class QualityRebuildEngine {
    private static final String BUILD = "git:0000000000000000000000000000000000000000";

    private QualityRebuildEngine() { }

    static ProjectionResult<? extends ProjectionRecord> rebuild(DataQualityValidationContext context) {
        ProjectionDefinition definition = context.projectionDefinition();
        ProjectionIdentifiers identifiers = new ProjectionIdentifiers(
                context.snapshot().projectionRunRef(), context.snapshot().snapshotRef());
        if (ProjectionDefinition.PROFILE_NAME.equals(definition.projectionName())) {
            List<IdentityBinding> bindings = context.identityBindings().stream()
                    .map(IdentityBindingEvidence::binding)
                    .sorted(Comparator.comparing(IdentityBinding::sourceIdentityRef)
                            .thenComparing(IdentityBinding::targetSubjectRef))
                    .toList();
            ProjectionResult<RecommendationProfileInputProjection> result =
                    new RecommendationProfileProjectionEngine().project(
                            definition, context.checkpoint(), context.sourceEvents(), bindings,
                            context.snapshot().snapshotAsOf(), identifiers, BUILD,
                            context.definition().validationAsOf());
            return result;
        }
        IdentityBinding identity = context.identityBindings().stream()
                .map(IdentityBindingEvidence::binding)
                .sorted(Comparator.comparing(IdentityBinding::sourceIdentityRef))
                .findFirst().orElse(null);
        ExperimentExposureBinding exposure = context.exposureEvidence().stream()
                .map(P2ExposureEvidence::binding)
                .sorted(Comparator.comparing(ExperimentExposureBinding::exposureRef))
                .findFirst().orElse(null);
        ProjectionResult<ExperimentOutcomeInputProjection> result =
                new ExperimentOutcomeProjectionEngine().project(
                        definition, context.checkpoint(), context.sourceEvents(), identity, exposure,
                        context.snapshot().snapshotAsOf(), identifiers, BUILD,
                        context.definition().validationAsOf());
        return result;
    }
}
