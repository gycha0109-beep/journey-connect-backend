package com.jc.data.contract.v1.integration;

import java.util.ArrayList;
import java.util.List;

public final class DataSearchIntegrationValidator {
    public List<CrossTrackIntegrationCheck> validate(CrossTrackIntegrationContext context, int startOrder) {
        ArrayList<CrossTrackIntegrationCheck> checks = new ArrayList<>();
        CrossTrackContractMapping mapping = context.contractMapping();
        if (!mapping.targetContractPresent()) {
            return List.of(CheckFactory.skipped(startOrder, "search.contract.present", CrossTrackIntegrationScope.DATA_SEARCH_INPUT,
                    context.sourceSnapshot().sourceContract(), mapping.targetContract(), CrossTrackIntegrationFailure.SEARCH_CONTRACT_MISSING, true));
        }
        checks.add(CheckFactory.check(startOrder, "search.schema.supported", CrossTrackIntegrationScope.DATA_SEARCH_INPUT,
                mapping.sourceSchemaVersion(), mapping.targetSchemaVersion(), "supported", Boolean.toString(mapping.schemaSupported()), mapping.schemaSupported(),
                CrossTrackIntegrationFailure.SEARCH_SCHEMA_UNSUPPORTED, true, false));
        boolean documentId = !context.searchDocumentMappingAttempted()
                || Boolean.TRUE.equals(context.sourceSnapshot().fields().get("stableSearchDocumentIdentity"));
        checks.add(CheckFactory.check(startOrder + 1, "search.document_identity", CrossTrackIntegrationScope.DATA_SEARCH_INPUT,
                context.sourceSnapshot().projectionName(), mapping.targetContract(), "post:<numeric-id> only", Boolean.toString(documentId), documentId,
                CrossTrackIntegrationFailure.SEARCH_DOCUMENT_IDENTITY_MISMATCH, true, false));
        boolean region = Boolean.TRUE.equals(context.sourceSnapshot().fields().get("searchRegionSemanticsCompatible"));
        boolean content = Boolean.TRUE.equals(context.sourceSnapshot().fields().get("searchContentSemanticsCompatible"));
        boolean tags = Boolean.TRUE.equals(context.sourceSnapshot().fields().get("searchTagSemanticsCompatible"));
        checks.add(CheckFactory.check(startOrder + 2, "search.region_semantics", CrossTrackIntegrationScope.DATA_SEARCH_INPUT,
                context.sourceSnapshot().snapshotRef(), mapping.targetContract(), "compatible", Boolean.toString(region), region,
                CrossTrackIntegrationFailure.SEARCH_REGION_SEMANTIC_MISMATCH, true, false));
        checks.add(CheckFactory.check(startOrder + 3, "search.content_semantics", CrossTrackIntegrationScope.DATA_SEARCH_INPUT,
                context.sourceSnapshot().snapshotRef(), mapping.targetContract(), "compatible", Boolean.toString(content), content,
                CrossTrackIntegrationFailure.SEARCH_CONTENT_SEMANTIC_MISMATCH, true, false));
        checks.add(CheckFactory.check(startOrder + 4, "search.tag_semantics", CrossTrackIntegrationScope.DATA_SEARCH_INPUT,
                context.sourceSnapshot().snapshotRef(), mapping.targetContract(), "compatible", Boolean.toString(tags), tags,
                CrossTrackIntegrationFailure.SEARCH_TAG_SEMANTIC_MISMATCH, true, false));
        checks.add(CheckFactory.check(startOrder + 5, "search.index_write", CrossTrackIntegrationScope.DATA_SEARCH_INPUT,
                "DP-7", "Search index", "false", Boolean.toString(context.searchIndexWriteAttempted()), !context.searchIndexWriteAttempted(),
                CrossTrackIntegrationFailure.SEARCH_PRODUCTION_INDEX_WRITE_DETECTED, true, false));
        checks.add(CheckFactory.check(startOrder + 6, "search.cutover", CrossTrackIntegrationScope.DATA_SEARCH_INPUT,
                "DP-7", "Search cutover", "false", Boolean.toString(context.searchCutoverAttempted()), !context.searchCutoverAttempted(),
                CrossTrackIntegrationFailure.SEARCH_CUTOVER_VIOLATION, true, false));
        return List.copyOf(checks);
    }
}
