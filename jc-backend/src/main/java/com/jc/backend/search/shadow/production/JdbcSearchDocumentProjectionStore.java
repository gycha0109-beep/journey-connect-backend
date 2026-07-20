package com.jc.backend.search.shadow.production;

import com.jc.intelligence.production.search.v1.SearchDocumentProjectionV1;
import com.jc.intelligence.production.search.v1.SearchProductionContractIds;
import com.jc.intelligence.production.search.v1.SearchProjectionAvailabilityStatus;
import com.jc.intelligence.production.search.v1.SearchProjectionQueryResultV1;
import com.jc.intelligence.production.search.v1.SearchProjectionQueryV1;
import com.jc.intelligence.production.search.v1.SearchProjectionStore;
import com.jc.intelligence.production.search.v1.SearchProjectionWriteResultV1;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Read-only runtime adapter. Projection writes remain owned by the versioned SQL projector functions. */
public final class JdbcSearchDocumentProjectionStore implements SearchProjectionStore {
    private static final RowMapper<SearchDocumentProjectionV1> MAPPER = JdbcSearchDocumentProjectionStore::map;
    private final JdbcTemplate jdbc;
    public JdbcSearchDocumentProjectionStore(JdbcTemplate jdbc) { this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc"); }

    @Override public Optional<SearchDocumentProjectionV1> findBySourcePostId(long sourcePostId) {
        if (sourcePostId < 1) throw new IllegalArgumentException("sourcePostId must be positive");
        List<SearchDocumentProjectionV1> rows = jdbc.query("""
                select document_id, source_post_id, source_version, region_id, region_reference,
                       place_reference, normalized_title_terms, normalized_body_terms,
                       source_updated_at, projected_at, deterministic_content_hash
                from public.search_document_projection_v1
                where source_post_id = ?
                  and projection_schema_version = 'search-document-projection-v1'
                  and eligibility_policy_version = 'search-document-eligibility-v1'
                """, MAPPER, sourcePostId);
        return rows.stream().findFirst();
    }

    @Override public SearchProjectionQueryResultV1 query(SearchProjectionQueryV1 query) {
        try {
            if (!query.projectionSchemaVersion().equals(SearchProductionContractIds.PROJECTION_SCHEMA)) {
                return SearchProjectionQueryResultV1.unavailable(SearchProjectionAvailabilityStatus.UNSUPPORTED_SCHEMA, "projection_schema_unsupported");
            }
            if (!query.eligibilityPolicyVersion().equals(SearchProductionContractIds.ELIGIBILITY_POLICY)) {
                return SearchProjectionQueryResultV1.unavailable(SearchProjectionAvailabilityStatus.UNSUPPORTED_POLICY, "eligibility_policy_unsupported");
            }
            String terms = String.join(",", query.queryTerms());
            List<SearchDocumentProjectionV1> rows = jdbc.query("""
                    select document_id, source_post_id, source_version, region_id, region_reference,
                           place_reference, normalized_title_terms, normalized_body_terms,
                           source_updated_at, projected_at, deterministic_content_hash
                    from public.search_document_projection_v1
                    where projection_schema_version = 'search-document-projection-v1'
                      and eligibility_policy_version = 'search-document-eligibility-v1'
                      and source_updated_at >= ?
                      and (? is null or region_reference = ?)
                      and (? = '' or normalized_title_terms && string_to_array(?, ',')::text[]
                                   or normalized_body_terms && string_to_array(?, ',')::text[])
                    order by source_updated_at desc, source_post_id desc
                    limit ?
                    """, MAPPER,
                    java.sql.Timestamp.from(query.referenceTime().minus(query.maximumStaleness())),
                    query.regionReference(), query.regionReference(), terms, terms, terms,
                    query.maximumCandidateCount());
            if (rows.isEmpty()) {
                Integer matchingCount = jdbc.queryForObject("""
                        select count(*)::integer
                        from public.search_document_projection_v1
                        where projection_schema_version = 'search-document-projection-v1'
                          and eligibility_policy_version = 'search-document-eligibility-v1'
                          and (? is null or region_reference = ?)
                          and (? = '' or normalized_title_terms && string_to_array(?, ',')::text[]
                                       or normalized_body_terms && string_to_array(?, ',')::text[])
                        """, Integer.class,
                        query.regionReference(), query.regionReference(), terms, terms, terms);
                if (matchingCount != null && matchingCount > 0) {
                    return SearchProjectionQueryResultV1.unavailable(
                            SearchProjectionAvailabilityStatus.STALE, "projection_stale");
                }
            }
            return SearchProjectionQueryResultV1.available(rows);
        } catch (DataAccessException exception) {
            return SearchProjectionQueryResultV1.unavailable(SearchProjectionAvailabilityStatus.UNAVAILABLE, "projection_store_unavailable");
        }
    }

    @Override public SearchProjectionWriteResultV1 upsert(SearchDocumentProjectionV1 projection) {
        throw new UnsupportedOperationException("production projection writes use project_search_document_v1");
    }
    @Override public SearchProjectionWriteResultV1 remove(long sourcePostId, String reason) {
        throw new UnsupportedOperationException("production projection removals use project_search_document_v1");
    }
    @Override public int size() {
        Integer count = jdbc.queryForObject("select count(*)::integer from public.search_document_projection_v1", Integer.class);
        return count == null ? 0 : count;
    }

    private static SearchDocumentProjectionV1 map(ResultSet rs, int rowNum) throws SQLException {
        return new SearchDocumentProjectionV1(
                rs.getString("document_id"), rs.getLong("source_post_id"), rs.getLong("source_version"),
                SearchProductionContractIds.PROJECTION_SCHEMA, SearchProductionContractIds.ELIGIBILITY_POLICY,
                rs.getLong("region_id"), rs.getString("region_reference"), rs.getString("place_reference"),
                strings(rs.getArray("normalized_title_terms")), strings(rs.getArray("normalized_body_terms")),
                rs.getTimestamp("source_updated_at").toInstant(), rs.getTimestamp("projected_at").toInstant(),
                rs.getString("deterministic_content_hash"));
    }
    private static List<String> strings(Array array) throws SQLException {
        if (array == null) return List.of();
        Object value = array.getArray();
        if (value instanceof String[] strings) return List.copyOf(Arrays.asList(strings));
        Object[] objects = (Object[]) value;
        return Arrays.stream(objects).map(Object::toString).toList();
    }
}
