package com.jc.backend.recommendation.application;

import com.jc.backend.common.CursorPageResponse;
import com.jc.backend.common.DomainException;
import com.jc.backend.post.PostDtos;
import com.jc.backend.post.PostService;
import com.jc.backend.recommendation.rca2.Rca2RequestRegistrar;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Chooses legacy, SHADOW, or CANARY feed paths while preserving a fail-open first page. */
@Service
public class RecommendationFeedService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationFeedService.class);

    private final PostService postService;
    private final RecommendationModeDecider modeDecider;
    private final RecommendationShadowService shadowService;
    private final RecommendationCanaryService canaryService;
    private final ObjectProvider<Rca2RequestRegistrar> rca2Registrar;

    public RecommendationFeedService(
            PostService postService,
            RecommendationModeDecider modeDecider,
            RecommendationShadowService shadowService,
            RecommendationCanaryService canaryService,
            ObjectProvider<Rca2RequestRegistrar> rca2Registrar) {
        this.postService = postService;
        this.modeDecider = modeDecider;
        this.shadowService = shadowService;
        this.canaryService = canaryService;
        this.rca2Registrar = rca2Registrar;
    }

    public CursorPageResponse<PostDtos.Summary> feed(
            String cursor, int size, Long userId, String tokenId) {
        long primaryStarted = System.nanoTime();
        if (canaryService.isRecommendationCursor(cursor)) {
            if (!modeDecider.isCanaryMode() || userId == null || userId <= 0) {
                throw expiredCursor();
            }
            try {
                return register(canaryService.nextPage(cursor, userId, tokenId, size), userId, tokenId, primaryStarted);
            } catch (DomainException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                log.warn("Recommendation CANARY continuation failed closed: {}",
                        exception.getClass().getSimpleName(), exception);
                throw expiredCursor();
            }
        }

        if (cursor == null && userId != null && userId > 0
                && modeDecider.shouldServeHomeCanary(userId)) {
            try {
                Optional<CursorPageResponse<PostDtos.Summary>> response =
                        canaryService.firstPage(userId, tokenId, size);
                if (response.isPresent()) {
                    return register(response.get(), userId, tokenId, primaryStarted);
                }
            } catch (RuntimeException exception) {
                log.warn("Recommendation CANARY first page failed open for user {}: {}",
                        userId, exception.getClass().getSimpleName(), exception);
            }
        }

        CursorPageResponse<PostDtos.Summary> legacy = postService.feed(cursor, size);
        shadowService.observeHomeFeed(userId, tokenId, cursor == null);
        return register(legacy, userId, tokenId, primaryStarted);
    }

    private CursorPageResponse<PostDtos.Summary> register(
            CursorPageResponse<PostDtos.Summary> response,
            Long userId,
            String tokenId,
            long startedNanos) {
        Rca2RequestRegistrar registrar = rca2Registrar.getIfAvailable();
        if (registrar != null) {
            long latencyMillis = Math.max(0L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedNanos));
            registrar.registerFeed(response, userId, tokenId, latencyMillis);
        }
        return response;
    }

    private DomainException expiredCursor() {
        return new DomainException(
                HttpStatus.CONFLICT,
                "RECOMMENDATION_CURSOR_EXPIRED",
                "추천 피드가 변경되었습니다. 첫 페이지부터 다시 요청해 주세요.");
    }
}
