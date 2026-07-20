package com.jc.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.database.DatabaseRequestIdentity;
import com.jc.backend.recommendation.api.RecommendationPreferenceDtos;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@CanonicalPostgresTest
@Tag("p1-verification")
class RecommendationPreferenceIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private DatabaseRequestIdentity requestIdentity;
    @Autowired private RecommendationPreferenceService preferenceService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void authenticatedUserCanReplaceColdStartPreferencesWithoutCrossUserMutation() {
        UserAccount first = users.save(new UserAccount(
                "p1-preference-first@example.com", "hash", "p1-preference-first"));
        UserAccount second = users.save(new UserAccount(
                "p1-preference-second@example.com", "hash", "p1-preference-second"));

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(first.getId())) {
            var response = preferenceService.replace(first.getId(), List.of(
                    preference("theme:food", RecommendationPreferenceDtos.PreferenceKind.PREFER, 1.0d),
                    preference("activity:running", RecommendationPreferenceDtos.PreferenceKind.PREFER, 0.7d)));
            assertThat(response.preferences()).extracting(
                    RecommendationPreferenceDtos.PreferenceResponse::featureId)
                    .containsExactly("activity:running", "theme:food");

            var replaced = preferenceService.replace(first.getId(), List.of(
                    preference("theme:nature", RecommendationPreferenceDtos.PreferenceKind.AVOID, 0.8d)));
            assertThat(replaced.preferences()).hasSize(1);
            assertThat(replaced.preferences().getFirst().featureId()).isEqualTo("theme:nature");
            assertThat(replaced.preferences().getFirst().preferenceKind())
                    .isEqualTo(RecommendationPreferenceDtos.PreferenceKind.AVOID);
        }

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(second.getId())) {
            assertThatThrownBy(() -> preferenceService.replace(first.getId(), List.of(
                    preference("theme:cafe", RecommendationPreferenceDtos.PreferenceKind.PREFER, 1.0d))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("binding");
        }

        Integer firstCount = jdbcTemplate.queryForObject(
                "select count(*)::integer from public.recommendation_user_preference where user_id = ?",
                Integer.class,
                first.getId());
        Integer secondCount = jdbcTemplate.queryForObject(
                "select count(*)::integer from public.recommendation_user_preference where user_id = ?",
                Integer.class,
                second.getId());
        assertThat(firstCount).isEqualTo(1);
        assertThat(secondCount).isZero();

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(first.getId())) {
            assertThatThrownBy(() -> preferenceService.replace(first.getId(), List.of(
                    preference("theme:not_registered", RecommendationPreferenceDtos.PreferenceKind.PREFER, 1.0d))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown");
        }
    }

    private static RecommendationPreferenceDtos.PreferenceRequest preference(
            String featureId,
            RecommendationPreferenceDtos.PreferenceKind kind,
            double strength) {
        return new RecommendationPreferenceDtos.PreferenceRequest(featureId, kind, strength);
    }
}
