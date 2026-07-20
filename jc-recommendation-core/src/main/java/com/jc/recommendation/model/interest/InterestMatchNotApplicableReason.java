package com.jc.recommendation.model.interest;

public enum InterestMatchNotApplicableReason {
    NO_USER_INTEREST_SIGNALS("no_user_interest_signals"),
    NO_USABLE_ENTITY_FEATURES("no_usable_entity_features");

    private final String wireValue;

    InterestMatchNotApplicableReason(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
