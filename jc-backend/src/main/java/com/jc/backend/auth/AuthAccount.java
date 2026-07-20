package com.jc.backend.auth;

import com.jc.backend.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/** Credential-bearing app_users mapping used only inside the jc_auth transaction boundary. */
@Entity
@Table(name = "app_users")
public class AuthAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(name = "display_name", nullable = false, length = 40)
    private String displayName;

    @Column(name = "profile_image_url", columnDefinition = "text")
    private String profileImageUrl;

    @Column(nullable = false, length = 500)
    private String bio = "";

    @Column(nullable = false, length = 20, insertable = false, updatable = false)
    private String role = "user";

    @Column(name = "account_status", nullable = false, length = 20, insertable = false, updatable = false)
    private String accountStatus = "active";

    protected AuthAccount() {}

    public AuthAccount(String email, String passwordHash, String nickname) {
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.displayName = nickname.trim();
        this.username = usernameFrom(nickname, email);
    }

    public void updateProfile(String nickname, String bio, String profileImageUrl) {
        if (nickname != null && !nickname.isBlank()) {
            this.displayName = nickname.trim();
        }
        this.bio = bio == null ? "" : bio;
        this.profileImageUrl = profileImageUrl;
    }

    public boolean isActive() {
        return "active".equals(accountStatus);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return displayName;
    }

    public String getBio() {
        return bio;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    private static String usernameFrom(String nickname, String email) {
        String normalized = Normalizer.normalize(nickname, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.length() < 3) {
            String localPart = email.substring(0, Math.max(0, email.indexOf('@')))
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9_]", "_");
            normalized = localPart.length() >= 3 ? localPart : "user";
        }
        if (normalized.length() > 13) {
            normalized = normalized.substring(0, 13);
        }
        return normalized + "_" + emailHashPrefix(email);
    }

    private static String emailHashPrefix(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    email.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
