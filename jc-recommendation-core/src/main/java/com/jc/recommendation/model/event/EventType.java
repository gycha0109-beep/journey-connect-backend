package com.jc.recommendation.model.event;

public enum EventType {
    IMPRESSION("impression"),
    VIEW("view"),
    CLICK("click"),
    LIKE("like"),
    UNLIKE("unlike"),
    SAVE("save"),
    UNSAVE("unsave"),
    SHARE("share"),
    FOLLOW("follow"),
    UNFOLLOW("unfollow"),
    HIDE("hide"),
    REPORT("report"),
    SEARCH("search"),
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
}
