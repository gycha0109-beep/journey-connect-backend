package com.jc.data.contract.v1.canonical;

import com.jc.data.contract.support.ImmutableContractValuesV1;
import com.jc.data.contract.v1.version.Versions;
import java.util.Map;

public record CanonicalizationRequestV1(
        Versions.CanonicalizationVersion canonicalizationVersion,
        Map<String, Object> approvedFields) {
    public CanonicalizationRequestV1 {
        approvedFields = approvedFields == null ? Map.of() : ImmutableContractValuesV1.copyMap(approvedFields);
    }
}
