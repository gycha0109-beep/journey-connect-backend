package com.jc.data.contract.v1.integration;

import java.util.List;
import java.util.Map;

public record CrossTrackContractMapping(
        String sourceContract,
        String sourceSchemaVersion,
        String targetContract,
        String targetSchemaVersion,
        String mappingPolicyVersion,
        boolean targetContractPresent,
        boolean targetAuthorityConfirmed,
        boolean schemaSupported,
        boolean requiredFieldsPresent,
        boolean semanticsCompatible,
        boolean unitsCompatible,
        boolean domainMappingApproved,
        List<String> missingRequiredFields,
        Map<String, String> semanticMappings,
        Map<String, String> unitMappings) {
    public CrossTrackContractMapping {
        sourceContract = IntegrationSupport.version(sourceContract, "sourceContract");
        sourceSchemaVersion = IntegrationSupport.version(sourceSchemaVersion, "sourceSchemaVersion");
        targetContract = IntegrationSupport.version(targetContract, "targetContract");
        targetSchemaVersion = IntegrationSupport.version(targetSchemaVersion, "targetSchemaVersion");
        mappingPolicyVersion = IntegrationSupport.version(mappingPolicyVersion, "mappingPolicyVersion");
        missingRequiredFields = IntegrationSupport.list(missingRequiredFields, "missingRequiredFields");
        semanticMappings = IntegrationSupport.map(semanticMappings, "semanticMappings");
        unitMappings = IntegrationSupport.map(unitMappings, "unitMappings");
    }
}
