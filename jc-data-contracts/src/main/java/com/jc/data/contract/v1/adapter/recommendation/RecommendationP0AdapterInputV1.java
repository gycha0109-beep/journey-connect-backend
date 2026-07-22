package com.jc.data.contract.v1.adapter.recommendation;

import com.jc.data.contract.v1.version.Versions;

public record RecommendationP0AdapterInputV1(
        RecommendationP0BehaviorEventSourceV1 source,
        RecommendationP0IdentityBindingV1 identityBinding,
        RecommendationP0ExposureBindingV1 exposureBinding,
        Versions.ProducerBuildId producerBuildId) {
}
