package com.jc.intelligence.compat.search.explore.v1;

import java.util.List;

public final class LegacyExploreCompatibilityAdapter {
    private final LegacyExploreRequestMapper requestMapper;
    private final LegacyExplorePageMapper pageMapper;
    private final LegacyExploreResultMapper resultMapper;

    public LegacyExploreCompatibilityAdapter() {
        this(new LegacyExploreRequestMapper(), new LegacyExplorePageMapper(), new LegacyExploreResultMapper());
    }

    public LegacyExploreCompatibilityAdapter(
            LegacyExploreRequestMapper requestMapper, LegacyExplorePageMapper pageMapper,
            LegacyExploreResultMapper resultMapper) {
        this.requestMapper = java.util.Objects.requireNonNull(requestMapper, "requestMapper");
        this.pageMapper = java.util.Objects.requireNonNull(pageMapper, "pageMapper");
        this.resultMapper = java.util.Objects.requireNonNull(resultMapper, "resultMapper");
    }

    public LegacyExploreCompatibilityResult adapt(
            LegacyExploreRequestView request, LegacyExplorePageView page, LegacyExploreCompatibilityContext context) {
        String requestFingerprint = safeRequestFingerprint(request);
        String responseFingerprint = safeResponseFingerprint(page);
        final LegacyExploreMappedRequest mappedRequest;
        try {
            mappedRequest = requestMapper.map(request, context);
        } catch (LegacyExploreMappingException exception) {
            return failureResult(exception, context, requestFingerprint, responseFingerprint,
                    page == null || page.items() == null ? 0 : page.items().size(), 0);
        }
        final LegacyExplorePageMetadata metadata;
        try {
            metadata = pageMapper.map(page);
        } catch (LegacyExploreMappingException exception) {
            return failureResult(exception, context, requestFingerprint, responseFingerprint,
                    page == null || page.items() == null ? 0 : page.items().size(), 0);
        }
        try {
            List<LegacyExploreMappedItem> items = resultMapper.map(page, mappedRequest);
            LegacyExploreCompatibilityEvidence evidence = evidence(context, requestFingerprint, responseFingerprint,
                    page.items().size(), items.size(), 0);
            return new LegacyExploreCompatibilityResult(LegacyExploreCompatibilityStatus.SUCCESS, mappedRequest, items,
                    metadata, evidence, null, false, false, false);
        } catch (LegacyExploreMappingException exception) {
            int sourceCount = page.items().size();
            return failureResult(exception, context, requestFingerprint, responseFingerprint, sourceCount, sourceCount);
        }
    }

    private static LegacyExploreCompatibilityResult failureResult(
            LegacyExploreMappingException exception, LegacyExploreCompatibilityContext context,
            String requestFingerprint, String responseFingerprint, int sourceCount, int rejectedCount) {
        LegacyExploreCompatibilityEvidence evidence = context == null || context.mappedAt() == null
                || context.producerBuildId() == null ? null
                : evidence(context, requestFingerprint, responseFingerprint, sourceCount, 0, rejectedCount);
        return new LegacyExploreCompatibilityResult(exception.status(), null, List.of(), null, evidence,
                exception.failure(), false, false, false);
    }

    private static LegacyExploreCompatibilityEvidence evidence(
            LegacyExploreCompatibilityContext context, String requestFingerprint, String responseFingerprint,
            int source, int mapped, int rejected) {
        return new LegacyExploreCompatibilityEvidence(LegacyExploreContractIds.ADAPTER,
                LegacyExploreContractIds.ENDPOINT_ID, requestFingerprint, responseFingerprint,
                LegacyExploreContractIds.MAPPING_POLICY, context.mappedAt(), context.producerBuildId(), source, mapped,
                rejected, LegacyExploreCompatibilityPolicy.BASE_WARNINGS, false, false, false, false);
    }

    private static String safeRequestFingerprint(LegacyExploreRequestView request) {
        try { return request == null ? LegacyExploreFingerprintV1.unavailableFingerprint("request-null")
                : LegacyExploreFingerprintV1.requestFingerprint(request); }
        catch (RuntimeException ignored) { return LegacyExploreFingerprintV1.unavailableFingerprint("request-invalid"); }
    }
    private static String safeResponseFingerprint(LegacyExplorePageView page) {
        try { return page == null ? LegacyExploreFingerprintV1.unavailableFingerprint("response-null")
                : LegacyExploreFingerprintV1.responseFingerprint(page); }
        catch (RuntimeException ignored) { return LegacyExploreFingerprintV1.unavailableFingerprint("response-invalid"); }
    }
}
