package com.jc.backend.post;

import com.jc.backend.database.DatabasePropagation;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.user.UserRepository;
import org.springframework.stereotype.Component;

/**
 * 좋아요·북마크 INSERT를 별도 트랜잭션에서 실행합니다.
 *
 * <p>"존재 확인 후 저장"만으로는 동시에 들어온 두 요청을 완전히 막을 수 없습니다. 따라서 빠른 멱등 처리를
 * 위해 먼저 존재 여부를 확인하고, 최종적으로는 DB 유니크 제약조건을 경쟁 상태의 방어선으로 사용합니다.
 * 별도 트랜잭션으로 분리해야 제약조건 위반이 발생한 트랜잭션만 롤백되고 API 요청 전체가 500으로 끝나지 않습니다.
 */
@Component
public class PostInteractionWriter {

    private final JourneyPostRepository posts;
    private final PostLikeRepository likes;
    private final BookmarkRepository bookmarks;
    private final UserRepository users;

    public PostInteractionWriter(
            JourneyPostRepository posts,
            PostLikeRepository likes,
            BookmarkRepository bookmarks,
            UserRepository users) {
        this.posts = posts;
        this.likes = likes;
        this.bookmarks = bookmarks;
        this.users = users;
    }

    @DatabaseTransactional(role = DatabaseRole.APP, propagation = DatabasePropagation.REQUIRES_NEW)
    public void addLike(Long postId, Long userId) {
        if (likes.existsByPostIdAndUserId(postId, userId)) {
            return;
        }
        likes.saveAndFlush(new PostLike(
                posts.getReferenceById(postId),
                users.getReferenceById(userId)));
    }

    @DatabaseTransactional(role = DatabaseRole.APP, propagation = DatabasePropagation.REQUIRES_NEW)
    public void addBookmark(Long postId, Long userId) {
        if (bookmarks.existsByPostIdAndUserId(postId, userId)) {
            return;
        }
        bookmarks.saveAndFlush(new Bookmark(
                posts.getReferenceById(postId),
                users.getReferenceById(userId)));
    }

}
