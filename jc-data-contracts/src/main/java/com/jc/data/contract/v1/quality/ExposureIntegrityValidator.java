package com.jc.data.contract.v1.quality;

import com.jc.data.contract.v1.projection.ExperimentExposureBinding;
import com.jc.data.contract.v1.projection.ExperimentOutcomeInputProjection;
import com.jc.data.contract.v1.projection.ProjectionDefinition;
import com.jc.data.contract.v1.projection.ProjectionRecord;
import java.util.List;

public final class ExposureIntegrityValidator {
    public List<DataQualityCheckResult> validate(DataQualityValidationContext context) {
        if (!ProjectionDefinition.OUTCOME_NAME.equals(context.projectionDefinition().projectionName())) {
            return List.of(QualityChecks.notApplicable("exposure.binding",
                    DataQualityValidationScope.EXPOSURE_INTEGRITY, "profile_projection", true));
        }
        DataQualityFailure failure = null;
        for (ProjectionRecord projectionRecord : context.projectionRecords()) {
            if (!(projectionRecord instanceof ExperimentOutcomeInputProjection record)) {
                failure = DataQualityFailure.EXPOSURE_BINDING_INVALID;
                break;
            }
            List<P2ExposureEvidence> matches = context.exposureEvidence().stream()
                    .filter(evidence -> evidence.binding().exposureRef().equals(record.exposureRef())).toList();
            if (matches.isEmpty()) {
                failure = DataQualityFailure.EXPOSURE_BINDING_MISSING;
                break;
            }
            if (matches.size() > 1) {
                failure = DataQualityFailure.DUPLICATE_EXPOSURE_AMBIGUITY;
                break;
            }
            P2ExposureEvidence evidence = matches.getFirst();
            ExperimentExposureBinding binding = evidence.binding();
            if (!evidence.authoritative() || evidence.generalExposure()
                    || !ExperimentExposureBinding.AUTHORITY.equals(binding.authorityId())) {
                failure = DataQualityFailure.GENERAL_EXPOSURE_USED_AS_P2;
                break;
            }
            if (!binding.targetSubjectRef().equals(record.subjectRef())) {
                failure = DataQualityFailure.EXPOSURE_SUBJECT_MISMATCH;
                break;
            }
            if (!binding.variantRef().equals(record.variantRef())) {
                failure = DataQualityFailure.EXPOSURE_VARIANT_MISMATCH;
                break;
            }
            if (!binding.experimentRef().equals(record.experimentRef())
                    || !binding.experimentVersion().equals(record.experimentVersion())) {
                failure = DataQualityFailure.EXPOSURE_EXPERIMENT_MISMATCH;
                break;
            }
            if (!binding.exposedAt().equals(record.exposedAt())) {
                failure = DataQualityFailure.EXPOSURE_TIME_MISMATCH;
                break;
            }
            if (!evidence.fallbackAuthorityMatched()
                    || binding.fallbackObserved() != record.fallbackObserved()) {
                failure = DataQualityFailure.FALLBACK_AUTHORITY_MISMATCH;
                break;
            }
        }
        return List.of(failure == null
                ? QualityChecks.pass("exposure.binding", DataQualityValidationScope.EXPOSURE_INTEGRITY,
                "authoritative_p2", "authoritative_p2", true)
                : QualityChecks.fail("exposure.binding", DataQualityValidationScope.EXPOSURE_INTEGRITY,
                "authoritative_p2", failure.wireValue(), "1", DataQualitySeverity.BLOCKER, failure, true));
    }
}
