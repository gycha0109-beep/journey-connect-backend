package com.jc.backend.intelligence.search;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SearchExposureStore implements SearchExposurePersistencePort {

    private static final String INSERT = """
            insert into public.search_exposure_event_v1 (
              exposure_id, idempotency_key, schema_version, payload_fingerprint,
              canonical_payload, payload_size_bytes, batch_fingerprint,
              search_run_id, result_snapshot_ref, subject_ref, identity_scheme,
              identity_mapping_version, session_id, surface, query_fingerprint,
              ranking_policy_version, page_occurrence_id, result_entity_type,
              result_entity_id, absolute_rank, page_position, visibility_rule_version,
              visible_ratio_basis_points, dwell_milliseconds, exposed_at,
              retention_policy_version, retention_until, producer_build_id
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict do nothing
            """;

    private final JdbcTemplate jdbcTemplate;

    public SearchExposureStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public StoreBatchResult store(SearchExposureCanonicalizer.CanonicalBatch batch) {
        Objects.requireNonNull(batch, "batch");
        if (batch.items().isEmpty()) {
            throw new IllegalArgumentException("canonical batch must contain items");
        }
        int stored = 0;
        int duplicate = 0;
        SearchExposureCommand command = batch.command();
        for (SearchExposureCanonicalizer.CanonicalItem canonicalItem : batch.items()) {
            SearchExposureCommand.Item item = canonicalItem.item();
            lock("exposure", item.exposureId());
            lock("idempotency", item.idempotencyKey());
            lock("occurrence", occurrenceKey(command, item));
            int inserted = jdbcTemplate.update(
                    INSERT,
                    item.exposureId(),
                    item.idempotencyKey(),
                    command.schemaVersion(),
                    canonicalItem.fingerprint(),
                    canonicalItem.bytes(),
                    canonicalItem.bytes().length,
                    batch.fingerprint(),
                    command.searchRunId(),
                    command.resultSnapshotRef(),
                    command.subjectRef(),
                    command.identityScheme(),
                    command.identityMappingVersion(),
                    command.sessionId(),
                    SearchExposureContract.SURFACE,
                    command.queryFingerprint(),
                    command.rankingPolicyVersion(),
                    command.pageOccurrenceId(),
                    SearchExposureContract.RESULT_ENTITY_TYPE,
                    item.postId(),
                    item.absoluteRank(),
                    item.pagePosition(),
                    command.visibilityRuleVersion(),
                    item.visibleRatioBasisPoints(),
                    item.dwellMilliseconds(),
                    Timestamp.from(item.exposedAt()),
                    command.retentionPolicyVersion(),
                    Timestamp.from(item.retentionUntil()),
                    command.producerBuildId());
            if (inserted == 1) {
                stored++;
            } else {
                assertExisting(batch, canonicalItem);
                duplicate++;
            }
        }
        return new StoreBatchResult(
                stored == 0 ? Status.DUPLICATE : Status.STORED,
                stored,
                duplicate);
    }

    private void assertExisting(
            SearchExposureCanonicalizer.CanonicalBatch batch,
            SearchExposureCanonicalizer.CanonicalItem canonicalItem) {
        SearchExposureCommand command = batch.command();
        SearchExposureCommand.Item item = canonicalItem.item();
        List<ExistingExposure> matches = jdbcTemplate.query(
                """
                select exposure_id, idempotency_key, schema_version, payload_fingerprint,
                       canonical_payload, batch_fingerprint, search_run_id,
                       result_snapshot_ref, subject_ref, identity_scheme,
                       identity_mapping_version, session_id, query_fingerprint,
                       ranking_policy_version, page_occurrence_id, result_entity_id,
                       absolute_rank, page_position, visibility_rule_version,
                       visible_ratio_basis_points, dwell_milliseconds, exposed_at,
                       retention_policy_version, retention_until, producer_build_id
                from public.search_exposure_event_v1
                where exposure_id = ?
                   or idempotency_key = ?
                   or (subject_ref = ? and session_id = ? and search_run_id = ?
                       and page_occurrence_id = ? and result_entity_id = ?
                       and absolute_rank = ? and visibility_rule_version = ?)
                order by case when exposure_id = ? then 0
                              when idempotency_key = ? then 1 else 2 end
                """,
                SearchExposureStore::existing,
                item.exposureId(),
                item.idempotencyKey(),
                command.subjectRef(),
                command.sessionId(),
                command.searchRunId(),
                command.pageOccurrenceId(),
                item.postId(),
                item.absoluteRank(),
                command.visibilityRuleVersion(),
                item.exposureId(),
                item.idempotencyKey());
        ExistingExposure existing = matches.isEmpty() ? null : matches.get(0);
        if (existing == null
                || !existing.exposureId().equals(item.exposureId())
                || !existing.idempotencyKey().equals(item.idempotencyKey())
                || !existing.schemaVersion().equals(command.schemaVersion())
                || !existing.payloadFingerprint().equals(canonicalItem.fingerprint())
                || !Arrays.equals(existing.canonicalPayload(), canonicalItem.bytes())
                || !existing.batchFingerprint().equals(batch.fingerprint())
                || !existing.searchRunId().equals(command.searchRunId())
                || !existing.resultSnapshotRef().equals(command.resultSnapshotRef())
                || !existing.subjectRef().equals(command.subjectRef())
                || !existing.identityScheme().equals(command.identityScheme())
                || !existing.identityMappingVersion().equals(command.identityMappingVersion())
                || !existing.sessionId().equals(command.sessionId())
                || !existing.queryFingerprint().equals(command.queryFingerprint())
                || !existing.rankingPolicyVersion().equals(command.rankingPolicyVersion())
                || !existing.pageOccurrenceId().equals(command.pageOccurrenceId())
                || existing.resultEntityId() != item.postId()
                || existing.absoluteRank() != item.absoluteRank()
                || existing.pagePosition() != item.pagePosition()
                || !existing.visibilityRuleVersion().equals(command.visibilityRuleVersion())
                || existing.visibleRatioBasisPoints() != item.visibleRatioBasisPoints()
                || existing.dwellMilliseconds() != item.dwellMilliseconds()
                || !truncate(existing.exposedAt()).equals(truncate(item.exposedAt()))
                || !existing.retentionPolicyVersion().equals(command.retentionPolicyVersion())
                || !truncate(existing.retentionUntil()).equals(truncate(item.retentionUntil()))
                || !existing.producerBuildId().equals(command.producerBuildId())) {
            throw new IdempotencyConflictException(item.idempotencyKey());
        }
    }

    private void lock(String namespace, String value) {
        jdbcTemplate.query(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                preparedStatement -> preparedStatement.setString(
                        1, "search_exposure_event_v1:" + namespace + ":" + value),
                resultSet -> { });
    }

    private static String occurrenceKey(
            SearchExposureCommand command,
            SearchExposureCommand.Item item) {
        return String.join(":",
                command.subjectRef(),
                command.sessionId(),
                command.searchRunId(),
                command.pageOccurrenceId(),
                Long.toString(item.postId()),
                Integer.toString(item.absoluteRank()),
                command.visibilityRuleVersion());
    }

    private static ExistingExposure existing(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new ExistingExposure(
                resultSet.getString("exposure_id"),
                resultSet.getString("idempotency_key"),
                resultSet.getString("schema_version"),
                resultSet.getString("payload_fingerprint"),
                resultSet.getBytes("canonical_payload"),
                resultSet.getString("batch_fingerprint"),
                resultSet.getString("search_run_id"),
                resultSet.getString("result_snapshot_ref"),
                resultSet.getString("subject_ref"),
                resultSet.getString("identity_scheme"),
                resultSet.getString("identity_mapping_version"),
                resultSet.getString("session_id"),
                resultSet.getString("query_fingerprint"),
                resultSet.getString("ranking_policy_version"),
                resultSet.getString("page_occurrence_id"),
                resultSet.getLong("result_entity_id"),
                resultSet.getInt("absolute_rank"),
                resultSet.getInt("page_position"),
                resultSet.getString("visibility_rule_version"),
                resultSet.getInt("visible_ratio_basis_points"),
                resultSet.getLong("dwell_milliseconds"),
                resultSet.getTimestamp("exposed_at").toInstant(),
                resultSet.getString("retention_policy_version"),
                resultSet.getTimestamp("retention_until").toInstant(),
                resultSet.getString("producer_build_id"));
    }

    private static Instant truncate(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private record ExistingExposure(
            String exposureId,
            String idempotencyKey,
            String schemaVersion,
            String payloadFingerprint,
            byte[] canonicalPayload,
            String batchFingerprint,
            String searchRunId,
            String resultSnapshotRef,
            String subjectRef,
            String identityScheme,
            String identityMappingVersion,
            String sessionId,
            String queryFingerprint,
            String rankingPolicyVersion,
            String pageOccurrenceId,
            long resultEntityId,
            int absoluteRank,
            int pagePosition,
            String visibilityRuleVersion,
            int visibleRatioBasisPoints,
            long dwellMilliseconds,
            Instant exposedAt,
            String retentionPolicyVersion,
            Instant retentionUntil,
            String producerBuildId) {}
}
