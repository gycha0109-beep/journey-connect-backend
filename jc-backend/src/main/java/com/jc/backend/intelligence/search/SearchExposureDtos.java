package com.jc.backend.intelligence.search;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class SearchExposureDtos {

    private static final String IDENTIFIER_PATTERN =
            "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$";
    private static final String IDEMPOTENCY_PATTERN =
            "^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$";

    private SearchExposureDtos() {}

    public record BatchRequest(
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = IDENTIFIER_PATTERN)
            String pageOccurrenceId,
            @NotBlank
            @Size(max = 8192)
            String resultContextToken,
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = IDENTIFIER_PATTERN)
            String visibilityRuleVersion,
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = IDENTIFIER_PATTERN)
            String producerBuildId,
            @NotEmpty
            @Size(max = SearchExposureContract.MAX_BATCH_SIZE)
            List<@Valid ItemRequest> items) {
        public BatchRequest {
            items = items == null ? null : List.copyOf(items);
        }
    }

    public record ItemRequest(
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = IDENTIFIER_PATTERN)
            String exposureId,
            @NotBlank
            @Size(max = 160)
            @Pattern(regexp = IDEMPOTENCY_PATTERN)
            String idempotencyKey,
            @NotNull
            @Positive
            Long postId,
            @NotNull
            @Positive
            Integer absoluteRank,
            @NotNull
            @Min(1)
            @Max(SearchExposureContract.MAX_PAGE_SIZE)
            Integer pagePosition,
            @NotNull
            @Min(0)
            @Max(SearchExposureContract.MAX_VISIBLE_RATIO_BASIS_POINTS)
            Integer visibleRatioBasisPoints,
            @NotNull
            @Min(0)
            @Max(SearchExposureContract.MAX_DWELL_MILLISECONDS)
            Long dwellMilliseconds,
            @NotNull
            Instant exposedAt) {}

    public record BatchResponse(
            int acceptedCount,
            int duplicateCount,
            String status) {}
}
