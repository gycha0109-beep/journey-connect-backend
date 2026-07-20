package com.jc.backend.search.shadow.production;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit operator-invoked projector entry point. It is not scheduled and is not called by legacy write transactions. */
public final class JdbcSearchProjectionRebuildService {
    private final JdbcTemplate jdbc;
    public JdbcSearchProjectionRebuildService(JdbcTemplate jdbc) { this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc"); }
    public String projectOne(long sourcePostId) {
        if (sourcePostId < 1) throw new IllegalArgumentException("sourcePostId must be positive");
        return jdbc.queryForObject("select public.project_search_document_v1(?)", String.class, sourcePostId);
    }
    public Map<String, Object> rebuildAll() {
        String json = jdbc.queryForObject("select public.rebuild_search_document_projection_v1()::text", String.class);
        return Map.of("safeResult", json == null ? "{}" : json);
    }
}
