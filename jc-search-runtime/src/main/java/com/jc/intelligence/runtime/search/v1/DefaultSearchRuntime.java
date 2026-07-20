package com.jc.intelligence.runtime.search.v1;

import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.replay.ReplayClass;
import com.jc.intelligence.contract.v1.replay.ReplayEvidenceDescriptorV1;
import com.jc.intelligence.contract.v1.run.IntelligenceRunStatus;
import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.SearchFailureCode;
import com.jc.intelligence.contract.v1.search.SearchFallbackCode;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalCandidateV1;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalRequestV1;
import com.jc.intelligence.contract.v1.search.run.SearchRunV1;
import com.jc.intelligence.runtime.search.v1.pagination.SearchRuntimePageProjector;
import com.jc.intelligence.runtime.search.v1.port.SearchCandidateFilter;
import com.jc.intelligence.runtime.search.v1.port.SearchDependencyDecision;
import com.jc.intelligence.runtime.search.v1.port.SearchEligibilityPort;
import com.jc.intelligence.runtime.search.v1.port.SearchRetrievalPort;
import com.jc.intelligence.runtime.search.v1.port.SearchRetrievalStatus;
import com.jc.intelligence.runtime.search.v1.port.SearchVisibilityPort;
import com.jc.intelligence.runtime.search.v1.ranking.SearchDeterministicOrdering;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankedCandidateV1;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankingPort;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankingRequestV1;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankingStatus;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRerankingPort;
import com.jc.intelligence.runtime.search.v1.snapshot.SearchResultSnapshotBuilder;
import com.jc.intelligence.runtime.search.v1.snapshot.SearchRuntimeFingerprintV1;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class DefaultSearchRuntime implements SearchRuntime {
    private final SearchRetrievalPort retrievalPort;
    private final SearchCandidateFilter candidateFilter;
    private final SearchEligibilityPort eligibilityPort;
    private final SearchVisibilityPort visibilityPort;
    private final SearchRankingPort rankingPort;
    private final SearchRerankingPort rerankingPort;
    private final SearchResultSnapshotBuilder snapshotBuilder;
    private final SearchRuntimePageProjector pageProjector;

    public DefaultSearchRuntime(
            SearchRetrievalPort retrievalPort,
            SearchCandidateFilter candidateFilter,
            SearchEligibilityPort eligibilityPort,
            SearchVisibilityPort visibilityPort,
            SearchRankingPort rankingPort,
            SearchRerankingPort rerankingPort) {
        this.retrievalPort = Objects.requireNonNull(retrievalPort, "retrievalPort");
        this.candidateFilter = Objects.requireNonNull(candidateFilter, "candidateFilter");
        this.eligibilityPort = Objects.requireNonNull(eligibilityPort, "eligibilityPort");
        this.visibilityPort = Objects.requireNonNull(visibilityPort, "visibilityPort");
        this.rankingPort = Objects.requireNonNull(rankingPort, "rankingPort");
        this.rerankingPort = Objects.requireNonNull(rerankingPort, "rerankingPort");
        this.snapshotBuilder = new SearchResultSnapshotBuilder();
        this.pageProjector = new SearchRuntimePageProjector();
    }

    @Override
    public SearchRuntimeResultV1 execute(SearchRuntimeExecutionRequestV1 execution) {
        if (execution == null) {
            throw new IllegalArgumentException("execution is required");
        }
        String inputFingerprint = SearchRuntimeFingerprintV1.request(execution.searchRequest());
        List<SearchRuntimeStageEvidenceV1> stages = new ArrayList<>();
        try {
            stages.add(new SearchRuntimeStageEvidenceV1("input_validation", "passed", 0));
            RetrievalRequestV1 retrievalRequest = new RetrievalRequestV1(
                    SearchContractIds.SEARCH_RETRIEVAL_RANKING, execution.runId(), execution.searchRequest().query(),
                    execution.searchRequest().context().entityScope(), execution.searchRequest().filters(),
                    execution.retrievalStrategyVersion(), execution.retrievalSources(),
                    execution.searchRequest().context().referenceTime(), execution.maximumCandidateCount(), null,
                    execution.producerBuildId());
            var retrieval = retrievalPort.retrieve(retrievalRequest);
            if (retrieval == null) return failure(execution, SearchRuntimeStatus.FAILED,
                    SearchRuntimeFailureCode.RETRIEVAL_FAILED, "retrieval returned no contract result", true,
                    inputFingerprint, 0, 0, 0, 0, 0, stages);
            if (retrieval.status() == SearchRetrievalStatus.UNAVAILABLE) {
                return failure(execution, SearchRuntimeStatus.DEPENDENCY_UNAVAILABLE,
                        SearchRuntimeFailureCode.RETRIEVAL_UNAVAILABLE, "retrieval dependency unavailable", true,
                        inputFingerprint, 0, 0, 0, 0, 0, stages);
            }
            if (retrieval.status() == SearchRetrievalStatus.FAILED) {
                return failure(execution, SearchRuntimeStatus.FAILED,
                        SearchRuntimeFailureCode.RETRIEVAL_FAILED, "retrieval failed", true,
                        inputFingerprint, 0, 0, 0, 0, 0, stages);
            }
            List<RetrievalCandidateV1> candidates = validateCandidates(
                    retrieval.candidates(), execution.maximumCandidateCount(), execution.searchRequest().context().entityScope(),
                    execution.retrievalSources(), execution.retrievalStrategyVersion(), execution.completedAt());
            stages.add(new SearchRuntimeStageEvidenceV1("retrieval", "passed", candidates.size()));

            ArrayList<RetrievalCandidateV1> filtered = new ArrayList<>();
            for (RetrievalCandidateV1 candidate : candidates) {
                com.jc.intelligence.runtime.search.v1.port.SearchFilterDecisionV1 filterDecision;
                try {
                    filterDecision = candidateFilter.evaluate(execution.searchRequest(), candidate);
                } catch (RuntimeException exception) {
                    return failure(execution, SearchRuntimeStatus.FAILED,
                            SearchRuntimeFailureCode.FILTERING_FAILED, "filtering failed", false,
                            inputFingerprint, candidates.size(), filtered.size(), 0, 0, 0, stages);
                }
                if (filterDecision == null) return failure(execution, SearchRuntimeStatus.FAILED,
                        SearchRuntimeFailureCode.FILTERING_FAILED, "filter returned no decision", false,
                        inputFingerprint, candidates.size(), filtered.size(), 0, 0, 0, stages);
                if (filterDecision.included()) filtered.add(candidate);
            }
            stages.add(new SearchRuntimeStageEvidenceV1("filtering", "passed", filtered.size()));

            ArrayList<RetrievalCandidateV1> eligible = new ArrayList<>();
            for (RetrievalCandidateV1 candidate : filtered) {
                SearchDependencyDecision eligibility;
                try {
                    eligibility = eligibilityPort.decide(execution.searchRequest(), candidate);
                } catch (RuntimeException exception) {
                    return failure(execution, SearchRuntimeStatus.DEPENDENCY_UNAVAILABLE,
                            SearchRuntimeFailureCode.ELIGIBILITY_DEPENDENCY_UNAVAILABLE,
                            "eligibility dependency unavailable", true, inputFingerprint,
                            candidates.size(), filtered.size(), eligible.size(), 0, 0, stages);
                }
                if (eligibility == null || eligibility == SearchDependencyDecision.DEPENDENCY_UNAVAILABLE) {
                    return failure(execution, SearchRuntimeStatus.DEPENDENCY_UNAVAILABLE,
                            SearchRuntimeFailureCode.ELIGIBILITY_DEPENDENCY_UNAVAILABLE,
                            "eligibility dependency unavailable", true, inputFingerprint,
                            candidates.size(), filtered.size(), eligible.size(), 0, 0, stages);
                }
                SearchDependencyDecision visibility;
                try {
                    visibility = visibilityPort.decide(execution.searchRequest(), candidate);
                } catch (RuntimeException exception) {
                    return failure(execution, SearchRuntimeStatus.DEPENDENCY_UNAVAILABLE,
                            SearchRuntimeFailureCode.VISIBILITY_DEPENDENCY_UNAVAILABLE,
                            "visibility dependency unavailable", true, inputFingerprint,
                            candidates.size(), filtered.size(), eligible.size(), 0, 0, stages);
                }
                if (visibility == null || visibility == SearchDependencyDecision.DEPENDENCY_UNAVAILABLE) {
                    return failure(execution, SearchRuntimeStatus.DEPENDENCY_UNAVAILABLE,
                            SearchRuntimeFailureCode.VISIBILITY_DEPENDENCY_UNAVAILABLE,
                            "visibility dependency unavailable", true, inputFingerprint,
                            candidates.size(), filtered.size(), eligible.size(), 0, 0, stages);
                }
                if (eligibility == SearchDependencyDecision.ALLOW && visibility == SearchDependencyDecision.ALLOW) {
                    eligible.add(candidate);
                }
            }
            stages.add(new SearchRuntimeStageEvidenceV1("eligibility_visibility", "passed", eligible.size()));

            SearchRankingRequestV1 rankingRequest = new SearchRankingRequestV1(execution.searchRequest(), eligible,
                    execution.searchRequest().rankingPolicyVersion(), execution.searchRequest().context().referenceTime());
            com.jc.intelligence.runtime.search.v1.ranking.SearchRankingResultV1 ranking;
            try {
                ranking = rankingPort.rank(rankingRequest);
            } catch (IllegalArgumentException exception) {
                SearchRuntimeFailureCode code = exception.getMessage() != null && exception.getMessage().contains("finite")
                        ? SearchRuntimeFailureCode.INVALID_SCORE : SearchRuntimeFailureCode.RANKING_FAILED;
                return failure(execution, SearchRuntimeStatus.FAILED, code, "ranking contract failed", false,
                        inputFingerprint, candidates.size(), filtered.size(), eligible.size(), 0, 0, stages);
            } catch (RuntimeException exception) {
                return failure(execution, SearchRuntimeStatus.FAILED,
                        SearchRuntimeFailureCode.RANKING_FAILED, "ranking failed", false,
                        inputFingerprint, candidates.size(), filtered.size(), eligible.size(), 0, 0, stages);
            }
            boolean fallbackUsed = false;
            SearchRuntimeFallbackV1 fallback = null;
            List<SearchRankedCandidateV1> ranked;
            if (ranking == null || ranking.status() == SearchRankingStatus.FAILED) {
                try {
                    ranked = sourceRankFallback(eligible);
                } catch (IllegalArgumentException exception) {
                    return failure(execution, SearchRuntimeStatus.FAILED,
                            SearchRuntimeFailureCode.RANKING_FAILED, "ranking fallback failed", false,
                            inputFingerprint, candidates.size(), filtered.size(), eligible.size(), 0, 0, stages);
                }
                fallbackUsed = true;
                fallback = new SearchRuntimeFallbackV1(SearchRuntimeFallbackCode.SOURCE_RANK_ORDERING,
                        SearchRuntimeFailureCode.RANKING_FAILED, execution.fallbackPolicyVersion());
                stages.add(new SearchRuntimeStageEvidenceV1("ranking", "fallback", ranked.size()));
            } else {
                try {
                    ranked = validateRanked(ranking.candidates(), eligible);
                } catch (IllegalArgumentException exception) {
                    SearchRuntimeFailureCode code = exception.getMessage() != null && exception.getMessage().contains("finite")
                            ? SearchRuntimeFailureCode.INVALID_SCORE : SearchRuntimeFailureCode.RANKING_FAILED;
                    return failure(execution, SearchRuntimeStatus.FAILED, code, "ranking output invalid", false,
                            inputFingerprint, candidates.size(), filtered.size(), eligible.size(), 0, 0, stages);
                }
                stages.add(new SearchRuntimeStageEvidenceV1("ranking", "passed", ranked.size()));
            }

            List<SearchRankedCandidateV1> reranked;
            try {
                reranked = rerankingPort.rerank(rankingRequest, ranked);
            } catch (RuntimeException exception) {
                return failure(execution, SearchRuntimeStatus.FAILED,
                        SearchRuntimeFailureCode.RERANKING_FAILED, "reranking failed", false,
                        inputFingerprint, candidates.size(), filtered.size(), eligible.size(), ranked.size(), 0, stages);
            }
            try {
                reranked = validateRanked(reranked, eligible);
            } catch (IllegalArgumentException exception) {
                return failure(execution, SearchRuntimeStatus.FAILED,
                        SearchRuntimeFailureCode.RERANKING_FAILED, "reranking output invalid", false,
                        inputFingerprint, candidates.size(), filtered.size(), eligible.size(), ranked.size(), 0, stages);
            }
            stages.add(new SearchRuntimeStageEvidenceV1("reranking", "passed", reranked.size()));
            List<SearchRankedCandidateV1> ordered;
            try {
                ordered = SearchDeterministicOrdering.order(reranked);
            } catch (RuntimeException exception) {
                return failure(execution, SearchRuntimeStatus.FAILED,
                        SearchRuntimeFailureCode.ORDERING_FAILED, "deterministic ordering failed", false,
                        inputFingerprint, candidates.size(), filtered.size(), eligible.size(), ranked.size(), 0, stages);
            }
            stages.add(new SearchRuntimeStageEvidenceV1("deterministic_ordering", "passed", ordered.size()));
            com.jc.intelligence.runtime.search.v1.snapshot.SearchResultSnapshotV1 snapshot;
            com.jc.intelligence.runtime.search.v1.pagination.SearchRuntimePageV1 page;
            try {
                snapshot = snapshotBuilder.build(execution, ordered);
                page = pageProjector.firstPage(execution, snapshot, execution.runId());
            } catch (RuntimeException exception) {
                return failure(execution, SearchRuntimeStatus.FAILED,
                        SearchRuntimeFailureCode.SNAPSHOT_FAILED, "snapshot construction failed", false,
                        inputFingerprint, candidates.size(), filtered.size(), eligible.size(), ranked.size(), 0, stages);
            }
            stages.add(new SearchRuntimeStageEvidenceV1("snapshot", "passed", snapshot.items().size()));

            SearchRuntimeStatus runtimeStatus = fallbackUsed ? SearchRuntimeStatus.FALLBACK
                    : ordered.isEmpty() ? SearchRuntimeStatus.NO_RESULTS : SearchRuntimeStatus.SUCCESS;
            IntelligenceRunStatus runStatus = fallbackUsed ? IntelligenceRunStatus.FALLBACK : IntelligenceRunStatus.SUCCEEDED;
            ReplayClass replayClass = execution.exactReplayEligible() ? ReplayClass.EXACT_REPLAY : ReplayClass.EVIDENCE_REPLAY;
            ReplayEvidenceDescriptorV1 replayEvidence = new ReplayEvidenceDescriptorV1(replayClass,
                    execution.exactReplayEligible(), true, true, true, false, false);
            SnapshotRef inputRef = new SnapshotRef("snapshot:ephemeral-search-input-" + inputFingerprint);
            SnapshotRef candidateRef = new SnapshotRef("snapshot:ephemeral-search-candidates-"
                    + SearchRuntimeFingerprintV1.candidates(ranked));
            SearchRunV1 searchRun = new SearchRunV1(SearchContractIds.SEARCH_DOMAIN, execution.runId(), runStatus,
                    execution.searchRequest().requestId(), execution.searchRequest().correlationId(),
                    execution.searchRequest().context().subjectRef(), execution.searchRequest().context().sessionRef(),
                    execution.searchRequest().context().surface(), execution.searchRequest().context().entityScope(),
                    inputRef, candidateRef, snapshot.snapshotId(), execution.searchRequest().queryNormalizationVersion(),
                    execution.retrievalStrategyVersion(), execution.searchRequest().rankingPolicyVersion(),
                    execution.searchRequest().featureDefinitionVersion(), execution.searchRequest().context().referenceTime(),
                    execution.startedAt(), execution.completedAt(), execution.producerBuildId(), replayClass, replayEvidence,
                    fallbackUsed ? SearchFallbackCode.RANKING_FAILED : null, null);
            SearchRuntimeEvidenceV1 evidence = evidence(execution, inputFingerprint, candidates.size(), filtered.size(),
                    eligible.size(), ranked.size(), ordered.size(), fallbackUsed ? SearchRuntimeFallbackCode.SOURCE_RANK_ORDERING : null,
                    null, snapshot.snapshotId(), stages);
            return new SearchRuntimeResultV1(runtimeStatus, snapshot, page, searchRun, searchRun.toIntelligenceRun(),
                    null, fallback, evidence, SearchRuntimeAuthorityV1.foundationOnly());
        } catch (com.jc.intelligence.contract.v1.search.validation.SearchContractValidationException exception) {
            return failure(execution, SearchRuntimeStatus.INVALID_REQUEST,
                    SearchRuntimeFailureCode.CONTRACT_VALIDATION_FAILED, "search contract validation failed", false,
                    inputFingerprint, 0, 0, 0, 0, 0, stages);
        } catch (IllegalArgumentException exception) {
            SearchRuntimeFailureCode code = exception.getMessage() != null && exception.getMessage().contains("duplicate")
                    ? SearchRuntimeFailureCode.DUPLICATE_CANDIDATE : SearchRuntimeFailureCode.INVALID_CANDIDATE;
            return failure(execution, SearchRuntimeStatus.FAILED, code, "runtime candidate contract failed", false,
                    inputFingerprint, 0, 0, 0, 0, 0, stages);
        } catch (RuntimeException exception) {
            return failure(execution, SearchRuntimeStatus.FAILED,
                    SearchRuntimeFailureCode.RETRIEVAL_FAILED, "runtime dependency failed", true,
                    inputFingerprint, 0, 0, 0, 0, 0, stages);
        }
    }

    private static List<RetrievalCandidateV1> validateCandidates(
            List<RetrievalCandidateV1> source,
            int maximumCandidateCount,
            com.jc.intelligence.contract.v1.search.SearchEntityScope scope,
            List<com.jc.intelligence.contract.v1.search.RetrievalSource> allowedSources,
            com.jc.intelligence.contract.v1.version.SchemaVersion expectedStrategyVersion,
            java.time.Instant completedAt) {
        if (source == null) throw new IllegalArgumentException("candidate list is required");
        if (source.size() > maximumCandidateCount) throw new IllegalArgumentException("candidate boundary exceeded");
        HashSet<com.jc.intelligence.contract.v1.identity.EntityRef> refs = new HashSet<>();
        ArrayList<RetrievalCandidateV1> result = new ArrayList<>(source.size());
        for (RetrievalCandidateV1 candidate : source) {
            if (candidate == null) throw new IllegalArgumentException("null candidate");
            if (!scope.accepts(candidate.entityType())) throw new IllegalArgumentException("candidate entity scope mismatch");
            if (!allowedSources.contains(candidate.retrievalSource())) throw new IllegalArgumentException("candidate retrieval source mismatch");
            if (!expectedStrategyVersion.equals(candidate.retrievalStrategyVersion())) {
                throw new IllegalArgumentException("candidate retrieval strategy version mismatch");
            }
            if (candidate.retrievedAt().isAfter(completedAt)) throw new IllegalArgumentException("candidate retrievedAt after completion");
            if (!refs.add(candidate.entityRef())) throw new IllegalArgumentException("duplicate candidate entityRef");
            result.add(candidate);
        }
        return List.copyOf(result);
    }

    private static List<SearchRankedCandidateV1> validateRanked(
            List<SearchRankedCandidateV1> source,
            List<RetrievalCandidateV1> expected) {
        if (source == null || source.size() != expected.size()) {
            throw new IllegalArgumentException("ranking must preserve candidate count");
        }
        HashSet<com.jc.intelligence.contract.v1.identity.EntityRef> refs = new HashSet<>();
        for (SearchRankedCandidateV1 item : source) {
            if (item == null) throw new IllegalArgumentException("null ranked candidate");
            if (!refs.add(item.candidate().entityRef())) throw new IllegalArgumentException("duplicate ranked candidate");
        }
        java.util.Map<com.jc.intelligence.contract.v1.identity.EntityRef, RetrievalCandidateV1> expectedByRef =
                expected.stream().collect(java.util.stream.Collectors.toMap(RetrievalCandidateV1::entityRef, value -> value));
        if (!refs.equals(expectedByRef.keySet())) throw new IllegalArgumentException("ranking changed candidate identity set");
        for (SearchRankedCandidateV1 item : source) {
            if (!item.candidate().equals(expectedByRef.get(item.candidate().entityRef()))) {
                throw new IllegalArgumentException("ranking or reranking changed candidate facts");
            }
        }
        return List.copyOf(source);
    }

    private static List<SearchRankedCandidateV1> sourceRankFallback(List<RetrievalCandidateV1> candidates) {
        ArrayList<SearchRankedCandidateV1> result = new ArrayList<>(candidates.size());
        for (RetrievalCandidateV1 candidate : candidates) {
            if (candidate.sourceRank() == null) {
                throw new IllegalArgumentException("source-rank fallback requires sourceRank");
            }
            result.add(new SearchRankedCandidateV1(candidate, null, null));
        }
        return List.copyOf(result);
    }

    private static SearchRuntimeResultV1 failure(
            SearchRuntimeExecutionRequestV1 execution,
            SearchRuntimeStatus status,
            SearchRuntimeFailureCode code,
            String safeMessage,
            boolean retryable,
            String inputFingerprint,
            int candidateCount,
            int filteredCount,
            int eligibleCount,
            int rankedCount,
            int resultCount,
            List<SearchRuntimeStageEvidenceV1> stages) {
        SearchRuntimeFailureV1 failure = new SearchRuntimeFailureV1(code, safeMessage, null, retryable,
                execution.completedAt());
        SearchRuntimeEvidenceV1 evidence = evidence(execution, inputFingerprint, candidateCount, filteredCount,
                eligibleCount, rankedCount, resultCount, null, code, null, stages);
        return new SearchRuntimeResultV1(status, null, null, null, null, failure, null, evidence,
                SearchRuntimeAuthorityV1.foundationOnly());
    }

    private static SearchRuntimeEvidenceV1 evidence(
            SearchRuntimeExecutionRequestV1 execution,
            String inputFingerprint,
            int candidateCount,
            int filteredCount,
            int eligibleCount,
            int rankedCount,
            int resultCount,
            SearchRuntimeFallbackCode fallbackCode,
            SearchRuntimeFailureCode failureCode,
            SnapshotRef snapshotId,
            List<SearchRuntimeStageEvidenceV1> stages) {
        return new SearchRuntimeEvidenceV1(execution.runtimeVersion(), execution.retrievalStrategyVersion(),
                execution.searchRequest().rankingPolicyVersion(), execution.searchRequest().queryNormalizationVersion(),
                execution.searchRequest().context().referenceTime(), inputFingerprint, candidateCount, filteredCount,
                eligibleCount, rankedCount, resultCount, candidateCount - eligibleCount, fallbackCode, failureCode,
                snapshotId, execution.producerBuildId(), stages);
    }
}
