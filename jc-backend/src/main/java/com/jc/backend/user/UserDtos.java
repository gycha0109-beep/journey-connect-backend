package com.jc.backend.user;

import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {}

    public record PublicProfile(
            Long id,
            String nickname,
            String bio,
            String profileImageUrl,
            long postCount,
            PublicProfileViewer viewer) {}

    public record PublicProfileViewer(boolean self) {}

    public record UpdateProfileRequest(
            @Size(max = 40) String nickname,
            @Size(max = 300) String bio,
            @Size(max = 500) String profileImageUrl) {}
}
