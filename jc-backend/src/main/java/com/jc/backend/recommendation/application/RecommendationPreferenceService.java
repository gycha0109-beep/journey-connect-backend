package com.jc.backend.recommendation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.database.DatabaseRequestIdentity;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.recommendation.api.RecommendationPreferenceDtos;
import com.jc.backend.recommendation.p1.RecommendationP1ProfileSource;
import com.jc.recommendation.model.feature.PreferenceKind;
import com.jc.recommendation.p1.profile.ExplicitPreference;
import com.jc.recommendation.p1.profile.P1FeatureVocabulary;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Replaces one authenticated user's explicit P1 preferences through a user-bound DB function. */
@Service
public class RecommendationPreferenceService {
    private final DatabaseRequestIdentity requestIdentity;
    private final RecommendationP1ProfileSource profileSource;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommendationPreferenceService(
            DatabaseRequestIdentity requestIdentity,
            RecommendationP1ProfileSource profileSource,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.requestIdentity = requestIdentity;
        this.profileSource = profileSource;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public RecommendationPreferenceDtos.PreferenceListResponse find(long userId) {
        requireBoundUser(userId);
        return response(profileSource.findExplicitPreferences(userId));
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public RecommendationPreferenceDtos.PreferenceListResponse replace(
            long userId,
            List<RecommendationPreferenceDtos.PreferenceRequest> requests) {
        requireBoundUser(userId);
        List<Map<String, Object>> payload = validate(requests);
        Integer replaced = jdbcTemplate.queryForObject(
                "select public.replace_recommendation_user_preferences(cast(? as jsonb))",
                Integer.class,
                json(payload));
        if (replaced == null || replaced != payload.size()) {
            throw new IllegalStateException("recommendation preference replacement count mismatch");
        }
        return response(profileSource.findExplicitPreferences(userId));
    }

    private List<Map<String, Object>> validate(
            List<RecommendationPreferenceDtos.PreferenceRequest> requests) {
        if (requests == null || requests.size() > 64) {
            throw new IllegalArgumentException("recommendation preference count must be 0..64");
        }
        Set<String> featureIds = new HashSet<>();
        List<Map<String, Object>> payload = new ArrayList<>();
        for (RecommendationPreferenceDtos.PreferenceRequest request : requests) {
            if (request == null || request.featureId() == null
                    || request.preferenceKind() == null
                    || !P1FeatureVocabulary.isRegistered(request.featureId())) {
                throw new IllegalArgumentException("unknown recommendation preference feature");
            }
            if (!Double.isFinite(request.strength())
                    || request.strength() < 0.0d
                    || request.strength() > 1.0d) {
                throw new IllegalArgumentException("recommendation preference strength is invalid");
            }
            if (!featureIds.add(request.featureId())) {
                throw new IllegalArgumentException("duplicate recommendation preference feature");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("featureId", request.featureId());
            item.put("preferenceKind", request.preferenceKind().name().toLowerCase(java.util.Locale.ROOT));
            item.put("strength", request.strength());
            payload.add(Map.copyOf(item));
        }
        return List.copyOf(payload);
    }

    private RecommendationPreferenceDtos.PreferenceListResponse response(
            List<ExplicitPreference> preferences) {
        return new RecommendationPreferenceDtos.PreferenceListResponse(preferences.stream()
                .map(preference -> new RecommendationPreferenceDtos.PreferenceResponse(
                        preference.featureId(),
                        preference.direction() == PreferenceKind.PREFER
                                ? RecommendationPreferenceDtos.PreferenceKind.PREFER
                                : RecommendationPreferenceDtos.PreferenceKind.AVOID,
                        preference.strength()))
                .toList());
    }

    private void requireBoundUser(long userId) {
        OptionalLong current = requestIdentity.currentUserId();
        if (userId <= 0 || current.isEmpty() || current.getAsLong() != userId) {
            throw new IllegalStateException("recommendation preference user binding is invalid");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("recommendation preference JSON is invalid", exception);
        }
    }
}
