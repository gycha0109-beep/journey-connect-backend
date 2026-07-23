package com.jc.data.contract.v1.integration;

import java.util.List;

public record CrossTrackIntegrationDefinition(
        CrossTrackIntegrationScope integrationScope,
        String sourceTrack,
        String targetTrack,
        String sourceContract,
        String sourceSchemaVersion,
        String targetContract,
        String targetSchemaVersion,
        String mappingPolicyVersion,
        String integrationPolicyVersion,
        String validatorVersion,
        List<String> requiredChecks) {
    public CrossTrackIntegrationDefinition {
        if (integrationScope == null) throw new NullPointerException("integrationScope");
        sourceTrack = IntegrationSupport.text(sourceTrack, "sourceTrack");
        targetTrack = IntegrationSupport.text(targetTrack, "targetTrack");
        sourceContract = IntegrationSupport.version(sourceContract, "sourceContract");
        sourceSchemaVersion = IntegrationSupport.version(sourceSchemaVersion, "sourceSchemaVersion");
        targetContract = IntegrationSupport.version(targetContract, "targetContract");
        targetSchemaVersion = IntegrationSupport.version(targetSchemaVersion, "targetSchemaVersion");
        mappingPolicyVersion = IntegrationSupport.version(mappingPolicyVersion, "mappingPolicyVersion");
        integrationPolicyVersion = IntegrationSupport.version(integrationPolicyVersion, "integrationPolicyVersion");
        validatorVersion = IntegrationSupport.version(validatorVersion, "validatorVersion");
        requiredChecks = IntegrationSupport.list(requiredChecks, "requiredChecks");
    }
}
