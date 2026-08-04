package com.jc.backend.intelligence.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@CanonicalPostgresTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecommendationSearchProfileSourceIntegrationTest {

    @Autowired private RecommendationSearchProfileSource profileSource;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void latestP1SnapshotCarriesBehaviorAndExplicitSignals() {
        UserAccount user = users.save(new UserAccount(
                "search-profile@example.com", "hash", "search-profile"));
        jdbcTemplate.update(
                """
                insert into public.recommendation_p1_profile_snapshot (
                  profile_snapshot_id, user_id, reference_time, profile_policy_version,
                  feature_vocabulary_version, segment, explicit_preference_count,
                  input_event_count, accepted_event_count, ignored_event_count,
                  duplicate_event_count, accepted_behavior_weight, signal_count,
                  signals, fingerprint
                ) values (?, ?, cast(? as timestamptz), 'behavior-profile-policy-v1',
                  'feature-vocabulary-v2', 'emerging', 1, 1, 1, 0, 0, 0.8, 2,
                  cast(? as jsonb), ?)
                """,
                "profile:search-test",
                user.getId(),
                "2026-08-01T00:00:00Z",
                """
                [
                  {"featureId":"theme:cafe","direction":"prefer","strength":0.8,
                   "signedWeight":1.0,"source":"combined"},
                  {"featureId":"theme:history","direction":"avoid","strength":0.4,
                   "signedWeight":-0.5,"source":"behavior"}
                ]
                """,
                "0".repeat(64));

        var profile = profileSource.find(
                user.getId(), Instant.parse("2026-08-04T00:00:00Z"));

        assertThat(profile.featureStrengths())
                .containsEntry("theme:cafe", 0.8d)
                .containsEntry("theme:history", -0.4d);
    }

    @Test
    void explicitPreferencesProvideColdStartFallbackWithoutSnapshot() {
        UserAccount user = users.save(new UserAccount(
                "search-explicit@example.com", "hash", "search-explicit"));
        jdbcTemplate.update(
                """
                insert into public.recommendation_user_preference (
                  user_id, feature_id, preference_kind, strength, active
                ) values (?, 'theme:cafe', 'prefer', 0.7, true)
                """,
                user.getId());

        var profile = profileSource.find(
                user.getId(), Instant.parse("2026-08-04T00:00:00Z"));

        assertThat(profile.featureStrengths())
                .containsExactlyEntriesOf(java.util.Map.of("theme:cafe", 0.7d));
    }
}
