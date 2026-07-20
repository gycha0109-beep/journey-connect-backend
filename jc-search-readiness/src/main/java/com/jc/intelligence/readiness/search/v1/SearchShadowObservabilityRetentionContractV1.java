package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.v1.version.ContractId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record SearchShadowObservabilityRetentionContractV1(
        ContractId contractVersion,
        List<String> allowedFields,
        List<String> prohibitedFields,
        SearchPrerequisiteStatus storageOwnerStatus,
        SearchPrerequisiteStatus accessRoleStatus,
        SearchPrerequisiteStatus retentionDurationStatus,
        SearchPrerequisiteStatus deletionProcessStatus,
        SearchPrerequisiteStatus incidentHoldStatus,
        SearchPrerequisiteStatus aggregationStatus,
        SearchPrerequisiteStatus auditStatus,
        boolean persistentWriterImplemented) {
    private static final Set<String> REQUIRED_PROHIBITED = Set.of(
            "raw_query", "normalized_query_text", "full_request", "full_response", "candidate_payload",
            "authentication_token", "raw_user_id", "raw_session_id", "precise_location", "private_metadata");
    public SearchShadowObservabilityRetentionContractV1 {
        if (!SearchReadinessContractIds.OBSERVABILITY.equals(contractVersion)) throw new IllegalArgumentException("unexpected contractVersion");
        allowedFields = immutableSafe(allowedFields, "allowedFields");
        prohibitedFields = immutableSafe(prohibitedFields, "prohibitedFields");
        if (!prohibitedFields.containsAll(REQUIRED_PROHIBITED)) throw new IllegalArgumentException("required prohibited fields are missing");
        if (allowedFields.stream().anyMatch(prohibitedFields::contains)) throw new IllegalArgumentException("allowed and prohibited fields overlap");
        if (persistentWriterImplemented) throw new IllegalArgumentException("IP-8 cannot implement persistent writer");
        if (storageOwnerStatus == null || accessRoleStatus == null || retentionDurationStatus == null
                || deletionProcessStatus == null || incidentHoldStatus == null || aggregationStatus == null || auditStatus == null) {
            throw new IllegalArgumentException("retention statuses are required");
        }
    }
    private static List<String> immutableSafe(List<String> values, String field) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException(field + " are required");
        ArrayList<String> copy = new ArrayList<>(values);
        if (copy.stream().anyMatch(value -> value == null || !value.matches("[a-z][a-z0-9_]{0,79}"))) {
            throw new IllegalArgumentException(field + " must contain lowercase_snake_case");
        }
        copy.sort(String::compareTo);
        if (copy.stream().distinct().count() != copy.size()) {
            throw new IllegalArgumentException(field + " contain duplicates");
        }
        return List.copyOf(copy);
    }
    public boolean persistentLoggingReady() {
        return storageOwnerStatus == SearchPrerequisiteStatus.RESOLVED
                && accessRoleStatus == SearchPrerequisiteStatus.RESOLVED
                && retentionDurationStatus == SearchPrerequisiteStatus.RESOLVED
                && deletionProcessStatus == SearchPrerequisiteStatus.RESOLVED
                && incidentHoldStatus == SearchPrerequisiteStatus.RESOLVED
                && aggregationStatus == SearchPrerequisiteStatus.RESOLVED
                && auditStatus == SearchPrerequisiteStatus.RESOLVED;
    }
}
