package com.jc.backend.intelligence.search;

import com.jc.backend.intelligence.search.SearchCtrModels.Attribution;
import com.jc.backend.intelligence.search.SearchCtrModels.BridgedClickOccurrence;
import com.jc.backend.intelligence.search.SearchCtrModels.EvaluationInput;
import com.jc.backend.intelligence.search.SearchCtrModels.EvaluationResult;
import com.jc.backend.intelligence.search.SearchCtrModels.ExposureOccurrence;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class SearchCtrAttributor {

    private static final Comparator<ExposureOccurrence> CANDIDATE_ORDER = Comparator
            .comparing(ExposureOccurrence::exposedAt).reversed()
            .thenComparing(ExposureOccurrence::receivedAt, Comparator.reverseOrder())
            .thenComparing(ExposureOccurrence::exposureId);

    private static final Comparator<BridgedClickOccurrence> CLICK_ORDER = Comparator
            .comparing(BridgedClickOccurrence::occurredAt)
            .thenComparing(BridgedClickOccurrence::clickEventId);

    public EvaluationResult evaluate(EvaluationInput input) {
        if (input == null) {
            throw new IllegalArgumentException("search CTR evaluation input is required");
        }
        List<ExposureOccurrence> exposures = uniqueExposures(input.exposures()).stream()
                .filter(exposure -> !exposure.exposedAt().isBefore(input.window().start())
                        && exposure.exposedAt().isBefore(input.window().end()))
                .sorted(Comparator.comparing(ExposureOccurrence::exposedAt)
                        .thenComparing(ExposureOccurrence::exposureId))
                .toList();
        List<BridgedClickOccurrence> clicks = uniqueClicks(input.clicks()).stream()
                .sorted(CLICK_ORDER)
                .toList();

        List<Attribution> attributions = new ArrayList<>();
        List<String> unattributed = new ArrayList<>();
        Set<String> attributedExposureIds = new LinkedHashSet<>();

        for (BridgedClickOccurrence click : clicks) {
            ExposureOccurrence selected = exposures.stream()
                    .filter(exposure -> matches(exposure, click))
                    .filter(exposure -> !click.occurredAt().isBefore(exposure.exposedAt()))
                    .filter(exposure -> click.occurredAt().isBefore(
                            exposure.exposedAt().plus(SearchCtrContract.ATTRIBUTION_WINDOW)))
                    .sorted(CANDIDATE_ORDER)
                    .findFirst()
                    .orElse(null);
            if (selected == null) {
                unattributed.add(click.clickEventId());
                continue;
            }
            long elapsed = ChronoUnit.MILLIS.between(selected.exposedAt(), click.occurredAt());
            attributions.add(new Attribution(click.clickEventId(), selected.exposureId(), elapsed));
            attributedExposureIds.add(selected.exposureId());
        }

        int denominator = exposures.size();
        int numerator = attributedExposureIds.size();
        Integer ctrBasisPoints = denominator == 0
                ? null
                : (int) (((long) numerator * SearchCtrContract.BASIS_POINT_SCALE) / denominator);
        Instant sourceMaxReceivedAt = maxReceivedAt(exposures, clicks);

        return new EvaluationResult(
                SearchCtrContract.METRIC_ID,
                SearchCtrContract.METRIC_VERSION,
                input.window().start(),
                input.window().end(),
                SearchCtrContract.PROVISIONAL_STATUS,
                denominator,
                numerator,
                ctrBasisPoints,
                input.computedAt(),
                sourceMaxReceivedAt,
                attributedExposureIds.stream().sorted().toList(),
                List.copyOf(attributions),
                List.copyOf(unattributed));
    }

    private static boolean matches(ExposureOccurrence exposure, BridgedClickOccurrence click) {
        return exposure.subjectRef().equals(click.subjectRef())
                && exposure.sessionId().equals(click.sessionId())
                && exposure.searchRunId().equals(click.searchRunId())
                && exposure.postId() == click.postId()
                && exposure.absoluteRank() == click.absoluteRank()
                && exposure.queryFingerprint().equals(click.queryFingerprint())
                && exposure.resultSnapshotRef().equals(click.resultSnapshotRef())
                && exposure.rankingPolicyVersion().equals(click.rankingPolicyVersion());
    }

    private static List<ExposureOccurrence> uniqueExposures(List<ExposureOccurrence> values) {
        Map<String, ExposureOccurrence> unique = new LinkedHashMap<>();
        for (ExposureOccurrence value : values) {
            ExposureOccurrence previous = unique.putIfAbsent(value.exposureId(), value);
            if (previous != null && !previous.equals(value)) {
                throw new IllegalArgumentException("duplicate exposure ID has different content");
            }
        }
        return List.copyOf(unique.values());
    }

    private static List<BridgedClickOccurrence> uniqueClicks(List<BridgedClickOccurrence> values) {
        Map<String, BridgedClickOccurrence> unique = new LinkedHashMap<>();
        for (BridgedClickOccurrence value : values) {
            BridgedClickOccurrence previous = unique.putIfAbsent(value.clickEventId(), value);
            if (previous != null && !previous.equals(value)) {
                throw new IllegalArgumentException("duplicate click event ID has different content");
            }
        }
        return List.copyOf(unique.values());
    }

    private static Instant maxReceivedAt(
            List<ExposureOccurrence> exposures,
            List<BridgedClickOccurrence> clicks) {
        Instant maximum = null;
        for (ExposureOccurrence exposure : exposures) {
            if (maximum == null || exposure.receivedAt().isAfter(maximum)) {
                maximum = exposure.receivedAt();
            }
        }
        for (BridgedClickOccurrence click : clicks) {
            if (maximum == null || click.receivedAt().isAfter(maximum)) {
                maximum = click.receivedAt();
            }
        }
        return maximum;
    }
}
