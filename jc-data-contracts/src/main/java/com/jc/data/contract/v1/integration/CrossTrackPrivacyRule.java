package com.jc.data.contract.v1.integration;

public record CrossTrackPrivacyRule(
        String sourcePrivacyClass,
        String targetPrivacyClass,
        boolean rawPayloadPresent,
        boolean piiPresent,
        boolean rawTextPresent,
        boolean preciseLocationPresent,
        boolean aggregateOnly,
        boolean lineagePurposeBound,
        boolean reidentificationRisk) {
    public CrossTrackPrivacyRule {
        sourcePrivacyClass = IntegrationSupport.text(sourcePrivacyClass, "sourcePrivacyClass");
        targetPrivacyClass = IntegrationSupport.text(targetPrivacyClass, "targetPrivacyClass");
    }
}
