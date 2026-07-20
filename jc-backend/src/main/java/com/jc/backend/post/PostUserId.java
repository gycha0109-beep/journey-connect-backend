package com.jc.backend.post;

import java.io.Serializable;
import java.util.Objects;

public class PostUserId implements Serializable {
    private Long post;
    private Long user;

    public PostUserId() {}

    public PostUserId(Long post, Long user) {
        this.post = post;
        this.user = user;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PostUserId that)) {
            return false;
        }
        return Objects.equals(post, that.post) && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(post, user);
    }
}
