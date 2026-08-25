package com.jc.backend.recommendation.persistence;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Persists the Crew-specific server-delivery exposure authority. */
@Component
public class CrewRecommendationExposureStore {

    private static final String INSERT_EVENT = """
            insert into public.crew_recommendation_exposure_event (
              exposure_id, schema_version, exposure_semantic, user_id, surface,
              served_at, reference_time, contract_version, ranking_policy_version,
              score_policy_version, profile_policy_version, feature_vocabulary_version,
              profile_fingerprint, requested_limit, returned_count,
              canonical_fingerprint, canonical_payload, payload_size_bytes
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (exposure_id) do nothing
            """;

    private static final String INSERT_CANDIDATE = """
            insert into public.crew_recommendation_exposure_candidate (
              exposure_id, absolute_rank, crew_id, score, coverage_mode,
              candidate_fingerprint, canonical_candidate, candidate_size_bytes
            ) values (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public CrewRecommendationExposureStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public StoreResult store(ExposureWrite write) {
        Objects.requireNonNull(write, "write");
        validate(write);

        String canonicalFingerprint = RecommendationHashing.sha256(write.canonicalPayload());
        int inserted = jdbcTemplate.update(
                INSERT_EVENT,
                write.exposureId(),
                write.schemaVersion(),
                write.exposureSemantic(),
                write.userId(),
                write.surface(),
                Timestamp.from(write.servedAt()),
                Timestamp.from(write.referenceTime()),
                write.contractVersion(),
                write.rankingPolicyVersion(),
                write.scorePolicyVersion(),
                write.profilePolicyVersion(),
                write.featureVocabularyVersion(),
                write.profileFingerprint(),
                write.requestedLimit(),
                write.candidates().size(),
                canonicalFingerprint,
                write.canonicalPayload(),
                write.canonicalPayload().length);

        if (inserted == 0) {
            assertExisting(write, canonicalFingerprint);
            return StoreResult.DUPLICATE;
        }

        jdbcTemplate.batchUpdate(INSERT_CANDIDATE, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                CandidateWrite candidate = write.candidates().get(index);
                byte[] canonicalCandidate = candidate.canonicalCandidate();
                statement.setString(1, write.exposureId());
                statement.setInt(2, candidate.rank());
                statement.setLong(3, candidate.crewId());
                statement.setDouble(4, candidate.score());
                statement.setString(5, candidate.coverageMode());
                statement.setString(6, RecommendationHashing.sha256(canonicalCandidate));
                statement.setBytes(7, canonicalCandidate);
                statement.setInt(8, canonicalCandidate.length);
            }

            @Override
            public int getBatchSize() {
                return write.candidates().size();
            }
        });
        return StoreResult.STORED;
    }

    private void assertExisting(ExposureWrite write, String canonicalFingerprint) {
        ExistingExposure existing = jdbcTemplate.queryForObject(
                """
                select exposure_id, schema_version, exposure_semantic, user_id, surface,
                       served_at, reference_time, contract_version, ranking_policy_version,
                       score_policy_version, profile_policy_version, feature_vocabulary_version,
                       profile_fingerprint, requested_limit, returned_count,
                       canonical_fingerprint, canonical_payload
                from public.crew_recommendation_exposure_event
                where exposure_id = ?
                """,
                (resultSet, rowNumber) -> new ExistingExposure(
                        resultSet.getString("exposure_id"),
                        resultSet.getString("schema_version"),
                        resultSet.getString("exposure_semantic"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("surface"),
                        resultSet.getTimestamp("served_at").toInstant(),
                        resultSet.getTimestamp("reference_time").toInstant(),
                        resultSet.getString("contract_version"),
                        resultSet.getString("ranking_policy_version"),
                        resultSet.getString("score_policy_version"),
                        resultSet.getString("profile_policy_version"),
                        resultSet.getString("feature_vocabulary_version"),
                        resultSet.getString("profile_fingerprint"),
                        resultSet.getInt("requested_limit"),
                        resultSet.getInt("returned_count"),
                        resultSet.getString("canonical_fingerprint"),
                        resultSet.getBytes("canonical_payload")),
                write.exposureId());

        List<ExistingCandidate> candidates = jdbcTemplate.query(
                """
                select absolute_rank, crew_id, score, coverage_mode,
                       candidate_fingerprint, canonical_candidate
                from public.crew_recommendation_exposure_candidate
                where exposure_id = ?
                order by absolute_rank
                """,
                (resultSet, rowNumber) -> new ExistingCandidate(
                        resultSet.getInt("absolute_rank"),
                        resultSet.getLong("crew_id"),
                        resultSet.getDouble("score"),
                        resultSet.getString("coverage_mode"),
                        resultSet.getString("candidate_fingerprint"),
                        resultSet.getBytes("canonical_candidate")),
                write.exposureId());

        boolean eventMatches = existing != null
                && existing.exposureId().equals(write.exposureId())
                && existing.schemaVersion().equals(write.schemaVersion())
                && existing.exposureSemantic().equals(write.exposureSemantic())
                && existing.userId() == write.userId()
                && existing.surface().equals(write.surface())
                && truncateToMicros(existing.servedAt()).equals(truncateToMicros(write.servedAt()))
                && truncateToMicros(existing.referenceTime()).equals(truncateToMicros(write.referenceTime()))
                && existing.contractVersion().equals(write.contractVersion())
                && existing.rankingPolicyVersion().equals(write.rankingPolicyVersion())
                && existing.scorePolicyVersion().equals(write.scorePolicyVersion())
                && existing.profilePolicyVersion().equals(write.profilePolicyVersion())
                && existing.featureVocabularyVersion().equals(write.featureVocabularyVersion())
                && existing.profileFingerprint().equals(write.profileFingerprint())
                && existing.requestedLimit() == write.requestedLimit()
                && existing.returnedCount() == write.candidates().size()
                && existing.canonicalFingerprint().equals(canonicalFingerprint)
                && Arrays.equals(existing.canonicalPayload(), write.canonicalPayload());

        if (!eventMatches || candidates.size() != write.candidates().size()) {
            throw conflict(write.exposureId());
        }

        for (int index = 0; index < candidates.size(); index++) {
            ExistingCandidate existingCandidate = candidates.get(index);
            CandidateWrite expected = write.candidates().get(index);
            byte[] canonicalCandidate = expected.canonicalCandidate();
            boolean candidateMatches = existingCandidate.rank() == expected.rank()
                    && existingCandidate.crewId() == expected.crewId()
                    && sameDouble(existingCandidate.score(), expected.score())
                    && existingCandidate.coverageMode().equals(expected.coverageMode())
                    && existingCandidate.candidateFingerprint()
                            .equals(RecommendationHashing.sha256(canonicalCandidate))
                    && Arrays.equals(existingCandidate.canonicalCandidate(), canonicalCandidate);
            if (!candidateMatches) {
                throw conflict(write.exposureId());
            }
        }
    }

    private static void validate(ExposureWrite write) {
        if (write.userId() <= 0) {
            throw new IllegalArgumentException("Crew exposure user ID must be positive.");
        }
        if (write.requestedLimit() < 1 || write.requestedLimit() > 20) {
            throw new IllegalArgumentException("Crew exposure requested limit must be in 1..20.");
        }
        if (write.candidates().size() > write.requestedLimit()) {
            throw new IllegalArgumentException("Crew exposure returned count exceeds requested limit.");
        }
        if (write.canonicalPayload().length == 0) {
            throw new IllegalArgumentException("Crew exposure canonical payload must not be empty.");
        }
        Set<Long> crewIds = new HashSet<>();
        for (int index = 0; index < write.candidates().size(); index++) {
            CandidateWrite candidate = write.candidates().get(index);
            if (candidate.rank() != index + 1) {
                throw new IllegalArgumentException("Crew exposure ranks must be contiguous from 1.");
            }
            if (candidate.crewId() <= 0 || !crewIds.add(candidate.crewId())) {
                throw new IllegalArgumentException("Crew exposure Crew IDs must be positive and unique.");
            }
            if (!Double.isFinite(candidate.score())) {
                throw new IllegalArgumentException("Crew exposure candidate score must be finite.");
            }
            if (!candidate.coverageMode().equals("full_featured")
                    && !candidate.coverageMode().equals("legacy_tagless")) {
                throw new IllegalArgumentException("Crew exposure coverage mode is invalid.");
            }
            if (candidate.canonicalCandidate().length == 0) {
                throw new IllegalArgumentException("Crew exposure candidate evidence must not be empty.");
            }
        }
    }

    private static IllegalStateException conflict(String exposureId) {
        return new IllegalStateException(
                "Crew exposure ID is already bound to different content: " + exposureId);
    }

    private static Instant truncateToMicros(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private static boolean sameDouble(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }

    public enum StoreResult {
        STORED,
        DUPLICATE
    }

    public record ExposureWrite(
            String exposureId,
            String schemaVersion,
            String exposureSemantic,
            long userId,
            String surface,
            Instant servedAt,
            Instant referenceTime,
            String contractVersion,
            String rankingPolicyVersion,
            String scorePolicyVersion,
            String profilePolicyVersion,
            String featureVocabularyVersion,
            String profileFingerprint,
            int requestedLimit,
            byte[] canonicalPayload,
            List<CandidateWrite> candidates) {

        public ExposureWrite {
            Objects.requireNonNull(exposureId, "exposureId");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(exposureSemantic, "exposureSemantic");
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(servedAt, "servedAt");
            Objects.requireNonNull(referenceTime, "referenceTime");
            Objects.requireNonNull(contractVersion, "contractVersion");
            Objects.requireNonNull(rankingPolicyVersion, "rankingPolicyVersion");
            Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
            Objects.requireNonNull(profilePolicyVersion, "profilePolicyVersion");
            Objects.requireNonNull(featureVocabularyVersion, "featureVocabularyVersion");
            Objects.requireNonNull(profileFingerprint, "profileFingerprint");
            canonicalPayload = Objects.requireNonNull(canonicalPayload, "canonicalPayload").clone();
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }

        @Override
        public byte[] canonicalPayload() {
            return canonicalPayload.clone();
        }
    }

    public record CandidateWrite(
            int rank,
            long crewId,
            double score,
            String coverageMode,
            byte[] canonicalCandidate) {

        public CandidateWrite {
            Objects.requireNonNull(coverageMode, "coverageMode");
            canonicalCandidate =
                    Objects.requireNonNull(canonicalCandidate, "canonicalCandidate").clone();
        }

        @Override
        public byte[] canonicalCandidate() {
            return canonicalCandidate.clone();
        }
    }

    private record ExistingExposure(
            String exposureId,
            String schemaVersion,
            String exposureSemantic,
            long userId,
            String surface,
            Instant servedAt,
            Instant referenceTime,
            String contractVersion,
            String rankingPolicyVersion,
            String scorePolicyVersion,
            String profilePolicyVersion,
            String featureVocabularyVersion,
            String profileFingerprint,
            int requestedLimit,
            int returnedCount,
            String canonicalFingerprint,
            byte[] canonicalPayload) {}

    private record ExistingCandidate(
            int rank,
            long crewId,
            double score,
            String coverageMode,
            String candidateFingerprint,
            byte[] canonicalCandidate) {}
}
