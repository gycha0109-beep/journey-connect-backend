package com.jc.backend.recommendation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.recommendation.canonical.CanonicalJson;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Converts Java records into deterministic canonical UTF-8 JSON bytes. */
@Component
public final class RecommendationCanonicalPayload {

    private final ObjectMapper objectMapper;

    public RecommendationCanonicalPayload(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Encoded encode(Object value) {
        JsonNode tree = objectMapper.valueToTree(value);
        String json = CanonicalJson.stringify(normalize(tree));
        return new Encoded(json.getBytes(StandardCharsets.UTF_8), json);
    }

    private Object normalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> result = new TreeMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), normalize(entry.getValue())));
            return result;
        }
        if (node.isArray()) {
            List<Object> result = new ArrayList<>(node.size());
            node.forEach(item -> result.add(normalize(item)));
            return List.copyOf(result);
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            double value = node.doubleValue();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Canonical recommendation payload contains non-finite number");
            }
            return value;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        throw new IllegalArgumentException(
                "Unsupported recommendation payload node: " + node.getNodeType());
    }

    public record Encoded(byte[] bytes, String json) {
        public Encoded {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
