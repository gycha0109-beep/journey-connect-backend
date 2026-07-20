package com.jc.backend.recommendation.persistence;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.SnapshotKind;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Canonical snapshot bytes and their domain-separated hash are stored atomically. */
@Component
public class RecommendationSnapshotStore {

    private static final String INSERT = """
            insert into public.recommendation_snapshot (
              snapshot_id, snapshot_kind, schema_version, canonicalization_version,
              hash_algorithm, content_hash, canonical_payload, payload_json, payload_size_bytes
            ) values (?, ?, ?, ?, 'sha256', ?, ?, cast(? as jsonb), ?)
            on conflict do nothing
            """;

    private final JdbcTemplate jdbcTemplate;

    public RecommendationSnapshotStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public StoredSnapshot store(SnapshotWrite write) {
        Objects.requireNonNull(write, "write");
        validate(write);
        String hash = RecommendationHashing.snapshotSha256(
                write.kind().value(), write.schemaVersion(), write.canonicalPayload());
        jdbcTemplate.update(
                INSERT,
                write.snapshotId(),
                write.kind().value(),
                write.schemaVersion(),
                write.canonicalizationVersion(),
                hash,
                write.canonicalPayload(),
                write.payloadJson(),
                write.canonicalPayload().length);
        Optional<StoredSnapshot> byId = find(write.snapshotId());
        if (byId.isPresent()) {
            assertEquivalent(write, hash, byId.get());
            return byId.get();
        }
        StoredSnapshot stored = findByContent(
                        write.kind(),
                        write.schemaVersion(),
                        write.canonicalizationVersion(),
                        hash)
                .orElseThrow(() -> new IllegalStateException(
                        "Snapshot was not persisted: " + write.snapshotId()));
        assertEquivalent(write, hash, stored);
        return stored;
    }

    private void validate(SnapshotWrite write) {
        if (!write.snapshotId().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("snapshotId format is invalid");
        }
        if (write.schemaVersion().isBlank() || write.canonicalizationVersion().isBlank()) {
            throw new IllegalArgumentException("snapshot versions must not be blank");
        }
        if (write.canonicalPayload().length > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("snapshot payload exceeds 16 MiB");
        }
    }

    public Optional<StoredSnapshot> find(String snapshotId) {
        return jdbcTemplate.query(
                """
                select snapshot_id, snapshot_kind, schema_version, canonicalization_version,
                       content_hash, canonical_payload, payload_json::text
                from public.recommendation_snapshot
                where snapshot_id = ?
                """,
                resultSet -> resultSet.next()
                        ? Optional.of(new StoredSnapshot(
                                resultSet.getString("snapshot_id"),
                                resultSet.getString("snapshot_kind"),
                                resultSet.getString("schema_version"),
                                resultSet.getString("canonicalization_version"),
                                resultSet.getString("content_hash"),
                                resultSet.getBytes("canonical_payload"),
                                resultSet.getString("payload_json")))
                        : Optional.empty(),
                snapshotId);
    }

    private Optional<StoredSnapshot> findByContent(
            SnapshotKind kind,
            String schemaVersion,
            String canonicalizationVersion,
            String contentHash) {
        return jdbcTemplate.query(
                """
                select snapshot_id, snapshot_kind, schema_version, canonicalization_version,
                       content_hash, canonical_payload, payload_json::text
                from public.recommendation_snapshot
                where snapshot_kind = ?
                  and schema_version = ?
                  and canonicalization_version = ?
                  and hash_algorithm = 'sha256'
                  and content_hash = ?
                """,
                resultSet -> resultSet.next()
                        ? Optional.of(new StoredSnapshot(
                                resultSet.getString("snapshot_id"),
                                resultSet.getString("snapshot_kind"),
                                resultSet.getString("schema_version"),
                                resultSet.getString("canonicalization_version"),
                                resultSet.getString("content_hash"),
                                resultSet.getBytes("canonical_payload"),
                                resultSet.getString("payload_json")))
                        : Optional.empty(),
                kind.value(),
                schemaVersion,
                canonicalizationVersion,
                contentHash);
    }

    private void assertEquivalent(SnapshotWrite write, String hash, StoredSnapshot stored) {
        if (!stored.snapshotKind().equals(write.kind().value())
                || !stored.schemaVersion().equals(write.schemaVersion())
                || !stored.canonicalizationVersion().equals(write.canonicalizationVersion())
                || !stored.contentHash().equals(hash)
                || !Arrays.equals(stored.canonicalPayload(), write.canonicalPayload())) {
            throw new IllegalStateException(
                    "Snapshot ID is already bound to different content: " + write.snapshotId());
        }
    }

    public record SnapshotWrite(
            String snapshotId,
            SnapshotKind kind,
            String schemaVersion,
            String canonicalizationVersion,
            byte[] canonicalPayload,
            String payloadJson) {

        public SnapshotWrite {
            Objects.requireNonNull(snapshotId, "snapshotId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(canonicalizationVersion, "canonicalizationVersion");
            canonicalPayload = Objects.requireNonNull(canonicalPayload, "canonicalPayload").clone();
            Objects.requireNonNull(payloadJson, "payloadJson");
        }

        @Override
        public byte[] canonicalPayload() {
            return canonicalPayload.clone();
        }
    }

    public record StoredSnapshot(
            String snapshotId,
            String snapshotKind,
            String schemaVersion,
            String canonicalizationVersion,
            String contentHash,
            byte[] canonicalPayload,
            String payloadJson) {

        public StoredSnapshot {
            canonicalPayload = canonicalPayload.clone();
        }

        @Override
        public byte[] canonicalPayload() {
            return canonicalPayload.clone();
        }
    }
}
