package com.jc.data.contract.v1.compatibility;

public record CompatibilityRequestV1(
        String sourceContractVersion,
        String schemaVersion,
        String consumerVersion,
        boolean unknownOptionalFields,
        boolean unknownRequiredEnum) {
}
