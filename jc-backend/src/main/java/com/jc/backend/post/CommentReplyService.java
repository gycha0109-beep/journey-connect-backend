package com.jc.backend.post;

import com.jc.backend.common.DomainException;
import com.jc.backend.common.PageResponse;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** PF7 bounded extension for one-depth comment replies. */
@Service
@DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
public class CommentReplyService {

    private final JourneyPostRepository posts;
    private final CommentRepository comments;
    private final UserRepository users;
    private final PostAccessPolicy accessPolicy;

    public CommentReplyService(
            JourneyPostRepository posts,
            CommentRepository comments,
            UserRepository users,
            PostAccessPolicy accessPolicy) {
        this.posts = posts;
        this.comments = comments;
        this.users = users;
        this.accessPolicy = accessPolicy;
    }

    public PageResponse<PostDtos.CommentView> comments(
            Long postId, Long viewerId, Pageable pageable) {
        readablePost(postId, viewerId);
        return PageResponse.from(comments.findVisibleByPostId(postId, pageable).map(this::commentView));
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public PostDtos.CommentView addComment(
            Long userId, Long postId, String content, Long parentCommentId) {
        JourneyPost post = publishedPost(postId, userId);
        UserAccount author = activeUser(userId);
        Comment parent = parentCommentId == null
                ? null
                : comments.findVisibleTopLevelParent(parentCommentId, postId)
                        .orElseThrow(this::invalidParent);
        Comment comment = comments.save(new Comment(post, author, content.trim(), parent));
        return commentView(comment);
    }

    private JourneyPost readablePost(Long postId, Long viewerId) {
        JourneyPost post = posts.findWithDetailById(postId)
                .orElseThrow(() -> notFound("POST_NOT_FOUND", "게시물"));
        if (post.isDeleted() || !post.isModerationVisible() || !post.getAuthor().isActive()) {
            throw notFound("POST_NOT_FOUND", "게시물");
        }
        boolean owner = viewerId != null && post.getAuthor().getId().equals(viewerId);
        if (post.isDraft()) {
            if (owner) {
                return post;
            }
            throw notFound("POST_NOT_FOUND", "게시물");
        }
        if (owner || accessPolicy.canView(viewerId, postId)) {
            return post;
        }
        throw notFound("POST_NOT_FOUND", "게시물");
    }

    private JourneyPost publishedPost(Long postId, Long viewerId) {
        JourneyPost post = readablePost(postId, viewerId);
        if (!post.isPublished()) {
            throw notFound("POST_NOT_FOUND", "게시물");
        }
        return post;
    }

    private UserAccount activeUser(Long userId) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "사용자"));
        if (!user.isActive()) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "USER_INACTIVE",
                    "비활성 계정은 해당 작업을 수행할 수 없습니다.");
        }
        return user;
    }

    private DomainException invalidParent() {
        return new DomainException(
                HttpStatus.BAD_REQUEST,
                "COMMENT_PARENT_INVALID",
                "답글 대상 댓글이 유효하지 않습니다.");
    }

    private DomainException notFound(String code, String target) {
        return new DomainException(HttpStatus.NOT_FOUND, code, target + "을(를) 찾을 수 없습니다.");
    }

    private PostDtos.CommentView commentView(Comment comment) {
        return new PostDtos.CommentView(
                comment.getId(),
                comment.getContent(),
                new PostDtos.Author(
                        comment.getAuthor().getId(),
                        comment.getAuthor().getNickname(),
                        comment.getAuthor().getProfileImageUrl()),
                comment.getCreatedAt(),
                comment.getParentCommentId());
    }
}
