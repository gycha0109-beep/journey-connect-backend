package com.jc.backend.intelligence.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.CanonicalPostgresTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@CanonicalPostgresTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SearchExposureApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SearchContextCodec contextCodec;

    @Test
    void storesDuplicatesRollsBackConflictAndRejectsInvalidatedIdentity() throws Exception {
        long userId = insertUser();
        long firstPostId = insertPublishedPost(userId, "검색 노출 첫 번째");
        long secondPostId = insertPublishedPost(userId, "검색 노출 두 번째");
        Instant issuedAt = Instant.now().minusSeconds(3);

        SearchExposureDtos.BatchRequest initial = request(
                "search-page:api-1",
                resultContext("search:api-run-1", userId, issuedAt,
                        List.of(new SearchContextCodec.ResultBinding(firstPostId, 1))),
                List.of(item("search-api-exp-1", "search-api-idem-1", firstPostId, 1, 1,
                        issuedAt.plusSeconds(1))));

        perform(userId, initial)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.acceptedCount").value(1))
                .andExpect(jsonPath("$.data.duplicateCount").value(0))
                .andExpect(jsonPath("$.data.status").value("stored"));
        perform(userId, initial)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.acceptedCount").value(0))
                .andExpect(jsonPath("$.data.duplicateCount").value(1))
                .andExpect(jsonPath("$.data.status").value("duplicate"));

        assertThat(count("select count(*) from public.search_exposure_event_v1")).isEqualTo(1);
        String subjectRef = jdbcTemplate.queryForObject(
                "select subject_ref from public.search_exposure_event_v1 where exposure_id = ?",
                String.class, "search-api-exp-1");
        assertThat(subjectRef).startsWith("subject:").doesNotContain(Long.toString(userId));
        assertThat(count("""
                select count(*) from information_schema.columns
                where table_schema='public' and table_name='search_exposure_event_v1'
                  and column_name='user_id'
                """)).isZero();

        SearchExposureDtos.BatchRequest conflictingBatch = request(
                "search-page:api-2",
                resultContext("search:api-run-2", userId, issuedAt,
                        List.of(new SearchContextCodec.ResultBinding(secondPostId, 2),
                                new SearchContextCodec.ResultBinding(firstPostId, 1))),
                List.of(
                        item("search-api-exp-new", "search-api-idem-new", secondPostId, 2, 1,
                                issuedAt.plusSeconds(1)),
                        item("search-api-exp-conflict", "search-api-idem-1", firstPostId, 1, 2,
                                issuedAt.plusSeconds(2))));

        perform(userId, conflictingBatch)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(count("select count(*) from public.search_exposure_event_v1 where exposure_id='search-api-exp-new'"))
                .isZero();
        assertThat(count("select count(*) from public.search_exposure_event_v1")).isEqualTo(1);

        jdbcTemplate.execute("select public.invalidate_platform_subject_v1('" + subjectRef
                + "','TEST_ACCOUNT_DELETION','system-coordination')");

        Instant secondIssuedAt = Instant.now().minusSeconds(2);
        SearchExposureDtos.BatchRequest invalidated = request(
                "search-page:api-3",
                resultContext("search:api-run-3", userId, secondIssuedAt,
                        List.of(new SearchContextCodec.ResultBinding(secondPostId, 1))),
                List.of(item("search-api-exp-invalidated", "search-api-idem-invalidated",
                        secondPostId, 1, 1, secondIssuedAt.plusSeconds(1))));
        perform(userId, invalidated)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SEARCH_EXPOSURE_IDENTITY_UNAVAILABLE"));
        assertThat(count("select count(*) from public.search_exposure_event_v1")).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions perform(long userId, SearchExposureDtos.BatchRequest request)
            throws Exception {
        return mockMvc.perform(post("/api/v1/search/exposures")
                .with(jwt().jwt(token -> token.subject(Long.toString(userId))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)));
    }

    private SearchExposureDtos.BatchRequest request(String pageOccurrenceId, String resultContextToken,
            List<SearchExposureDtos.ItemRequest> items) {
        return new SearchExposureDtos.BatchRequest(pageOccurrenceId, resultContextToken,
                SearchExposureContract.CANDIDATE_VISIBILITY_RULE_VERSION, "web-20260805.1", items);
    }

    private SearchExposureDtos.ItemRequest item(String exposureId, String idempotencyKey, long postId,
            int absoluteRank, int pagePosition, Instant exposedAt) {
        return new SearchExposureDtos.ItemRequest(exposureId, idempotencyKey, postId, absoluteRank,
                pagePosition, 6_000, 1_500L, exposedAt);
    }

    private String resultContext(String runId, long userId, Instant issuedAt,
            List<SearchContextCodec.ResultBinding> bindings) {
        String queryFingerprint = SearchHashing.sha256(runId + ":query");
        String snapshotFingerprint = SearchHashing.sha256(runId + ":snapshot");
        int pageSize = Math.max(1, bindings.size());
        String snapshotToken = contextCodec.encodeSnapshot(runId, userId, queryFingerprint, issuedAt,
                pageSize, snapshotFingerprint, SearchRankingPolicy.POLICY_VERSION, issuedAt);
        SearchContextCodec.SnapshotContext snapshot = contextCodec.decodeSnapshot(snapshotToken, userId,
                queryFingerprint, pageSize, issuedAt.plusMillis(100));
        return contextCodec.encodeResultContext(snapshot, bindings, issuedAt.plusMillis(200));
    }

    private long insertUser() {
        return jdbcTemplate.queryForObject("""
                insert into public.app_users(email,password_hash,username,display_name)
                values ('search-exposure-api@example.com','hash','search_exposure_api','Search Exposure API')
                returning id
                """, Long.class);
    }

    private long insertPublishedPost(long userId, String title) {
        Long regionId = jdbcTemplate.queryForObject(
                "select id from public.regions where is_active=true order by id limit 1", Long.class);
        long placeId = jdbcTemplate.queryForObject("""
                insert into public.places(region_id,name_local,name_ko,category,created_by_user_id)
                values (?,?,?,'test',?) returning id
                """, Long.class, regionId, title + " 장소", title + " 장소", userId);
        long postId = jdbcTemplate.queryForObject("""
                insert into public.posts(author_id,main_region_id,title,content,visibility,status)
                values (?,?,?,?,'public','draft') returning id
                """, Long.class, userId, regionId, title, title + " 내용");
        jdbcTemplate.update("insert into public.post_places(post_id,place_id,sort_order) values (?,?,0)",
                postId, placeId);
        jdbcTemplate.update("update public.posts set status='published' where id=?", postId);
        return postId;
    }

    private long count(String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0 : result;
    }
}
