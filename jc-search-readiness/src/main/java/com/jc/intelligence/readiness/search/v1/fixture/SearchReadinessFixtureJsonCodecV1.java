package com.jc.intelligence.readiness.search.v1.fixture;

import com.jc.intelligence.contract.support.StrictContractJsonParserV1;
import java.util.Map;

public final class SearchReadinessFixtureJsonCodecV1 {
    private SearchReadinessFixtureJsonCodecV1() { }

    public static SearchReadinessFixtureCaseV1 read(String json) {
        Object parsed = StrictContractJsonParserV1.parse(json);
        if (!(parsed instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("fixture must be an object");
        }
        return new SearchReadinessFixtureCaseV1(
                text(values, "scenario"),
                bool(values, "proposalReady"),
                integer(values, "unresolvedActivationCount"),
                text(values, "expectedProposalDecision"),
                text(values, "expectedActivationDecision"),
                bool(values, "disabledEquivalenceExpected"));
    }

    public static String write(SearchReadinessFixtureCaseV1 value) {
        return "{\n"
                + "  \"scenario\": \"" + value.scenario() + "\",\n"
                + "  \"proposalReady\": " + value.proposalReady() + ",\n"
                + "  \"unresolvedActivationCount\": " + value.unresolvedActivationCount() + ",\n"
                + "  \"expectedProposalDecision\": \"" + value.expectedProposalDecision() + "\",\n"
                + "  \"expectedActivationDecision\": \"" + value.expectedActivationDecision() + "\",\n"
                + "  \"disabledEquivalenceExpected\": " + value.disabledEquivalenceExpected() + "\n"
                + "}\n";
    }

    private static String text(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(key + " must be text");
        }
        return text;
    }

    private static boolean bool(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException(key + " must be boolean");
        }
        return flag;
    }

    private static int integer(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Long number) || number < 0 || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " must be a non-negative integer");
        }
        return number.intValue();
    }
}
