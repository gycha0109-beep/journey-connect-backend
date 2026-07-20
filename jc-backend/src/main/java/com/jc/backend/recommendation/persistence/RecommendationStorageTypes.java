package com.jc.backend.recommendation.persistence;

public final class RecommendationStorageTypes {

    private RecommendationStorageTypes() {}

    public enum SnapshotKind {
        RANKING_INPUT_V1("ranking_input_v1"),
        DIVERSITY_METADATA_V1("diversity_metadata_v1"),
        EXPLORATION_METADATA_V1("exploration_metadata_v1"),
        RANKING_RESULT_V1("ranking_result_v1"),
        EXPOSURE_EVENT_V1("exposure_event_v1");

        private final String value;

        SnapshotKind(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum RunMode {
        SHADOW("shadow"), CANARY("canary"), LIVE("live");

        private final String value;

        RunMode(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum RunStatus {
        SUCCEEDED("succeeded"), FALLBACK("fallback"), FAILED("failed");

        private final String value;

        RunStatus(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum Surface {
        HOME("home"), SEARCH("search"), DETAIL("detail"), PROFILE("profile"), CREW("crew");

        private final String value;

        Surface(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
