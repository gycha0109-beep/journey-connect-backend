package com.jc.data.contract.v1.quality;

import com.jc.data.contract.v1.projection.IdentityBinding;
import com.jc.data.contract.v1.projection.ProjectionLineage;
import com.jc.data.contract.v1.projection.ProjectionRecord;
import com.jc.data.contract.v1.projection.ProjectionSourceEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IdentityIntegrityValidator {
    public List<DataQualityCheckResult> validate(DataQualityValidationContext context) {
        Map<String, ProjectionSourceEvent> sources = new HashMap<>();
        for (ProjectionSourceEvent source : context.sourceEvents()) sources.put(source.sourceEventRef(), source);
        Map<String, ProjectionRecord> records = new HashMap<>();
        for (ProjectionRecord record : context.projectionRecords()) records.put(record.recordRef(), record);
        Map<String, List<IdentityBindingEvidence>> bindings = new HashMap<>();
        for (IdentityBindingEvidence evidence : context.identityBindings()) {
            bindings.computeIfAbsent(evidence.binding().sourceIdentityRef(), ignored -> new ArrayList<>()).add(evidence);
        }
        DataQualityFailure failure = null;
        Set<String> checkedUsers = new HashSet<>();
        for (ProjectionLineage lineage : context.lineage()) {
            ProjectionSourceEvent source = sources.get(lineage.sourceEventRef());
            ProjectionRecord record = records.get(lineage.projectionRecordRef());
            if (source == null || record == null) continue;
            if (!record.subjectRef().startsWith("subject:")) {
                failure = DataQualityFailure.IDENTITY_NAMESPACE_CONFLICT;
                break;
            }
            if (source.identityRef().startsWith("subject:")) {
                if (!source.identityRef().equals(record.subjectRef())) {
                    failure = DataQualityFailure.IDENTITY_PROJECTION_SUBJECT_MISMATCH;
                    break;
                }
                continue;
            }
            checkedUsers.add(source.identityRef());
            List<IdentityBindingEvidence> matches = bindings.getOrDefault(source.identityRef(), List.of());
            if (matches.isEmpty()) {
                failure = DataQualityFailure.IDENTITY_BINDING_MISSING;
                break;
            }
            Set<String> targets = matches.stream().map(e -> e.binding().targetSubjectRef())
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> bindingContracts = matches.stream().map(IdentityIntegrityValidator::bindingContract)
                    .collect(java.util.stream.Collectors.toSet());
            if (targets.size() != 1 || bindingContracts.size() != 1) {
                failure = DataQualityFailure.IDENTITY_NAMESPACE_CONFLICT;
                break;
            }
            IdentityBindingEvidence evidence = matches.getFirst();
            IdentityBinding binding = evidence.binding();
            if (!binding.bindingFingerprint().equals(evidence.authoritativeFingerprint())) {
                failure = DataQualityFailure.IDENTITY_BINDING_FINGERPRINT_MISMATCH;
                break;
            }
            if (!evidence.sourceCheckpointRef().equals(context.checkpoint().checkpointRef())) {
                failure = DataQualityFailure.IDENTITY_BINDING_INVALID;
                break;
            }
            if (!("journey-connect".equals(binding.bindingScope())
                    || context.projectionDefinition().targetContractVersion().equals(binding.bindingScope()))) {
                failure = DataQualityFailure.IDENTITY_BINDING_SCOPE_MISMATCH;
                break;
            }
            if (!binding.targetSubjectRef().equals(record.subjectRef())
                    || !evidence.projectionSubjectRef().equals(record.subjectRef())) {
                failure = DataQualityFailure.IDENTITY_PROJECTION_SUBJECT_MISMATCH;
                break;
            }
        }
        if (failure == null && !checkedUsers.isEmpty()) {
            for (String user : checkedUsers) {
                if (!bindings.containsKey(user)) failure = DataQualityFailure.IDENTITY_BINDING_MISSING;
            }
        }
        return List.of(failure == null
                ? QualityChecks.pass("identity.binding", DataQualityValidationScope.IDENTITY_INTEGRITY,
                "valid", "valid", true)
                : QualityChecks.fail("identity.binding", DataQualityValidationScope.IDENTITY_INTEGRITY,
                "valid", failure.wireValue(), "1", DataQualitySeverity.BLOCKER, failure, true));
    }
    private static String bindingContract(IdentityBindingEvidence evidence) {
        IdentityBinding binding = evidence.binding();
        return String.join("|", binding.sourceIdentityRef(), binding.targetSubjectRef(), binding.bindingVersion(),
                binding.bindingSource(), binding.bindingFingerprint(), binding.bindingScope(),
                evidence.authoritativeFingerprint(), evidence.sourceCheckpointRef(), evidence.projectionSubjectRef());
    }
}
