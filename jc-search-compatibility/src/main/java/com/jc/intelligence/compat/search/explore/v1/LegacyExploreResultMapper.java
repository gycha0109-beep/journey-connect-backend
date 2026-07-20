package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.search.SearchEligibilityState;
import com.jc.intelligence.contract.v1.search.SearchEntityType;
import com.jc.intelligence.contract.v1.search.SearchVisibilityState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LegacyExploreResultMapper {
    public List<LegacyExploreMappedItem> map(
            LegacyExplorePageView page, LegacyExploreMappedRequest request) {
        List<LegacyExploreMappedItem> mapped = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (int index = 0; index < page.items().size(); index++) {
            LegacyExploreItemView item = page.items().get(index);
            validate(item, index);
            if (!ids.add(item.id())) {
                throw failure(LegacyExploreMappingFailureCode.DUPLICATE_ITEM_REFERENCE, "items.id", index,
                        "duplicate legacy item ID");
            }
            List<LegacyExploreExplanationCode> facts = new ArrayList<>();
            if (request.query().normalizedQuery() != null) facts.add(LegacyExploreExplanationCode.LEGACY_QUERY_PREDICATE_APPLIED);
            if (!request.filters().isEmpty()) facts.add(LegacyExploreExplanationCode.LEGACY_REGION_FILTER_APPLIED);
            facts.add(LegacyExploreExplanationCode.LEGACY_PUBLISHED_AT_DESC_ID_DESC_ORDER);
            mapped.add(new LegacyExploreMappedItem(
                    new EntityRef("post:" + item.id()), SearchEntityType.POST, Long.toString(item.id()), index + 1,
                    null, null, null, null, SearchEligibilityState.UNKNOWN, SearchVisibilityState.UNKNOWN, item,
                    new LegacyExploreCompatibilityExplanation(facts, false, false),
                    LegacyExploreCompatibilityPolicy.BASE_WARNINGS));
        }
        return List.copyOf(mapped);
    }

    private static void validate(LegacyExploreItemView item, int index) {
        if (item == null || item.id() == null || item.title() == null || item.regionCode() == null
                || item.regionName() == null || item.author() == null || item.createdAt() == null
                || item.viewCount() == null || item.likeCount() == null || item.bookmarkCount() == null) {
            throw failure(LegacyExploreMappingFailureCode.MISSING_REQUIRED_LEGACY_FIELD, "items", index,
                    "legacy item is missing a required field");
        }
        if (item.id() <= 0 || item.title().isBlank() || item.regionCode().isBlank() || item.regionName().isBlank()
                || item.author().id() == null || item.author().id() <= 0 || item.author().nickname() == null
                || item.author().nickname().isBlank() || item.viewCount() < 0 || item.likeCount() < 0
                || item.bookmarkCount() < 0) {
            throw failure(LegacyExploreMappingFailureCode.LEGACY_PAYLOAD_INCONSISTENCY, "items", index,
                    "legacy item contains invalid values");
        }
    }

    private static LegacyExploreMappingException failure(
            LegacyExploreMappingFailureCode code, String field, int index, String message) {
        return new LegacyExploreMappingException(LegacyExploreCompatibilityStatus.MAPPING_FAILURE,
                new LegacyExploreMappingFailure(code, field, index, message));
    }
}
