package com.jc.recommendation.model.event;

import java.util.LinkedHashMap;
import java.util.Map;

public record UserBehaviorEventMetadata(
        EventSurface surface,
        Integer position,
        String query,
        Long dwellTimeMs,
        Double viewportRatio
) {
    public static UserBehaviorEventMetadata empty() {
        return new UserBehaviorEventMetadata(null, null, null, null, null);
    }

    public Map<String, Object> toCanonicalMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (surface != null) {
            result.put("surface", surface.wireValue());
        }
        if (position != null) {
            result.put("position", position);
        }
        if (query != null) {
            result.put("query", query);
        }
        if (dwellTimeMs != null) {
            result.put("dwellTimeMs", dwellTimeMs);
        }
        if (viewportRatio != null) {
            result.put("viewportRatio", viewportRatio);
        }
        return Map.copyOf(result);
    }
}
