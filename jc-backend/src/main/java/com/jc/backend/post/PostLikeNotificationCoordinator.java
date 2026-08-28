package com.jc.backend.post;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.notification.NotificationService;
import com.jc.backend.recommendation.application.RecommendationPostInteractionService;
import com.jc.backend.recommendation.application.RecommendationPostInteractionService.TrackingContext;
import com.jc.backend.recommendation.persistence.RecommendationPostInteractionStore.Action;
import com.jc.backend.recommendation.persistence.RecommendationPostInteractionStore.Result;
import org.springframework.stereotype.Service;

/** Coordinates canonical LIKE state/event persistence with the PF10 inbox write. */
@Service
public final class PostLikeNotificationCoordinator {

    private final RecommendationPostInteractionService interactions;
    private final NotificationService notifications;

    public PostLikeNotificationCoordinator(
            RecommendationPostInteractionService interactions,
            NotificationService notifications) {
        this.interactions = interactions;
        this.notifications = notifications;
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void like(long userId, String tokenId, long postId, TrackingContext tracking) {
        Result result = interactions.applyWithResult(
                userId,
                tokenId,
                postId,
                Action.LIKE,
                tracking);
        if (result == Result.APPLIED) {
            notifications.postLiked(userId, postId);
        }
    }
}
