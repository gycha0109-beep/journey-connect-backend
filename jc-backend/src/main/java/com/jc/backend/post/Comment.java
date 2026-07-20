package com.jc.backend.post;

import com.jc.backend.common.BaseTimeEntity;
import com.jc.backend.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "comments")
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private JourneyPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserAccount author;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "moderation_deleted_at", insertable = false, updatable = false)
    private Instant moderationDeletedAt;

    protected Comment() {}

    public Comment(JourneyPost post, UserAccount author, String content) {
        this.post = post;
        this.author = author;
        this.content = content;
    }

    public void deleteByAuthor() {
        if (deletedAt == null) {
            deletedAt = Instant.now();
        }
    }

    public boolean isVisible() {
        return deletedAt == null && moderationDeletedAt == null;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }
}
