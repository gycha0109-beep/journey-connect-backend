package com.jc.data.contract.v1.command;

import com.jc.data.contract.support.ImmutableContractValuesV1;
import java.util.Map;

public record ClientEventCommandV1(
        String requestedEventType,
        String entityCandidateRef,
        String occurredAt,
        String sessionToken,
        String idempotencyKey,
        Map<String, Object> context) {
    public ClientEventCommandV1 {
        context = context == null ? Map.of() : ImmutableContractValuesV1.copyMap(context);
    }
}
