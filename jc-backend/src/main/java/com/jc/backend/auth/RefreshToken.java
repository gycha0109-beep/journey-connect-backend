package com.jc.backend.auth;

import com.jc.backend.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Refresh Token의 저장 상태와 회전/폐기 정책을 표현하는 엔티티입니다.
 *
 * <p>원문 토큰은 저장하지 않고 해시만 보관하며, revokedAt이 설정된 토큰은 더 이상 사용할 수 없습니다.
 */
@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"),
        indexes = {
            @Index(name = "idx_refresh_token_user", columnList = "user_id"),
            @Index(name = "idx_refresh_token_expires", columnList = "expires_at")
        })
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AuthAccount user;

    /** 원문 토큰은 저장하지 않고 SHA-256 해시만 저장합니다. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {}

    public RefreshToken(AuthAccount user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * 이미 사용된 토큰이거나 로그아웃된 토큰은 재사용 불가 상태로 표시합니다.
     * 같은 토큰의 중복 사용을 막기 위해 기존 값이 없을 때만 폐기 시각을 기록합니다.
     */
    public void revoke(Instant revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public AuthAccount getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
