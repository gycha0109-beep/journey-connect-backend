package com.jc.backend.intelligence.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationSearchProfileSource {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommendationSearchProfileSource(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public SearchInterestProfile find(long userId, Instant referenceTime) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (referenceTime == null) {
            throw new IllegalArgumentException("referenceTime is required");
        }
        List<String> snapshots = jdbcTemplate.query(
                """
                select signals::text
                from public.recommendation_p1_profile_snapshot
                where user_id = ?
                  and reference_time <= ?
                order by reference_time desc, created_at desc, profile_snapshot_id desc
                limit 1
                """,
                (resultSet, rowNumber) -> resultSet.getString(1),
                userId,
                java.sql.Timestamp.from(referenceTime));
        if (!snapshots.isEmpty()) {
            return parseSnapshot(snapshots.get(0));
        }
        return explicitPreferences(userId);
    }

    private SearchInterestProfile parseSnapshot(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                throw new IllegalStateException("P1 profile signals must be an array");
            }
            Map<String, Double> strengths = new TreeMap<>();
            for (JsonNode signal : root) {
                String featureId = requiredText(signal, "featureId");
                String direction = requiredText(signal, "direction");
                double strength = signal.path("strength").asDouble(Double.NaN);
                if (!Double.isFinite(strength) || strength < 0.0d || strength > 1.0d) {
                    throw new IllegalStateException("P1 profile signal strength is invalid");
                }
                double signed = switch (direction.toLowerCase(Locale.ROOT)) {
                    case "prefer" -> strength;
                    case "avoid" -> -strength;
                    default -> throw new IllegalStateException(
                            "P1 profile signal direction is invalid");
                };
                strengths.put(featureId, signed);
            }
            return new SearchInterestProfile(strengths);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("P1 profile signals JSON is invalid", exception);
        }
    }

    private SearchInterestProfile explicitPreferences(long userId) {
        Map<String, Double> strengths = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                select feature_id, preference_kind, strength
                from public.recommendation_user_preference
                where user_id = ? and active = true
                order by feature_id
                """,
                resultSet -> {
                    String kind = resultSet.getString("preference_kind");
                    double strength = resultSet.getDouble("strength");
                    strengths.put(
                            resultSet.getString("feature_id"),
                            "avoid".equals(kind) ? -strength : strength);
                },
                userId);
        return new SearchInterestProfile(strengths);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("P1 profile signal " + field + " is invalid");
        }
        return value.textValue();
    }

    public record SearchInterestProfile(Map<String, Double> featureStrengths) {
        public SearchInterestProfile {
            featureStrengths = Map.copyOf(featureStrengths);
        }

        public static SearchInterestProfile empty() {
            return new SearchInterestProfile(Map.of());
        }
    }
}
