package com.jc.backend.user;

import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {}

    public record UpdateProfileRequest(
            @Size(max = 40) String nickname,
            @Size(max = 300) String bio,
            @Size(max = 500) String profileImageUrl) {}
}
