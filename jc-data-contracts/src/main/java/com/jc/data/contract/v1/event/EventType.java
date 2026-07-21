package com.jc.data.contract.v1.event;

import java.util.Arrays;
import java.util.Optional;

public enum EventType {
    POST_IMPRESSION("post_impression"),
    POST_VIEW("post_view"),
    POST_DWELL("post_dwell"),
    POST_LIKE("post_like"),
    POST_UNLIKE("post_unlike"),
    POST_BOOKMARK("post_bookmark"),
    POST_UNBOOKMARK("post_unbookmark"),
    POST_SHARE("post_share"),
    POST_HIDE("post_hide"),
    POST_REPORT("post_report"),
    SEARCH_SUBMIT("search_submit"),
    SEARCH_RESULT_IMPRESSION("search_result_impression"),
    SEARCH_RESULT_CLICK("search_result_click"),
    RECOMMENDATION_IMPRESSION("recommendation_impression"),
    RECOMMENDATION_CLICK("recommendation_click"),
    PROFILE_VIEW("profile_view"),
    FOLLOW("follow"),
    UNFOLLOW("unfollow"),
    TAG_CLICK("tag_click"),
    CREW_JOIN("crew_join"),
    CREW_LEAVE("crew_leave");

    private final String wireValue;

    EventType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static Optional<EventType> fromWire(String wireValue) {
        return Arrays.stream(values()).filter(value -> value.wireValue.equals(wireValue)).findFirst();
    }
}
