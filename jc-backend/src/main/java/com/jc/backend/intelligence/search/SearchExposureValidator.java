package com.jc.backend.intelligence.search;

import com.jc.backend.common.DomainException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

public final class SearchExposureValidator {

    private static final Pattern IDENTIFIER =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$");
    private static final Pattern SUBJECT_REF =
            Pattern.compile("^subject:[A-Za-z0-9][A-Za-z0-9._:-]{0,119}$");

    private final SearchContextCodec contextCodec;

    public SearchExposureValidator(SearchContextCodec contextCodec) {
        this.contextCodec = contextCodec;
    }

    public SearchExposureCommand validate(
            SearchExposureActor actor,
            SearchExposureDtos.BatchRequest request,
            Instant now,
            SearchExposureValidationPolicy policy) {
        if (actor == null || request == null || now == null || policy == null) {
            throw batchInvalid();
        }
        validateActor(actor);
        requireIdentifier(request.pageOccurrenceId());
        requireIdentifier(request.visibilityRuleVersion());
        requireIdentifier(request.producerBuildId());
        if (request.resultContextToken() == null
                || request.resultContextToken().isBlank()
                || request.resultContextToken().length() > 8_192) {
            throw batchInvalid();
        }
        if (!policy.visibilityRuleVersion().equals(request.visibilityRuleVersion())) {
            throw ruleUnsupported();
        }
        if (request.items() == null
                || request.items().isEmpty()
                || request.items().size() > SearchExposureContract.MAX_BATCH_SIZE) {
            throw batchInvalid();
        }

        SearchContextCodec.ResultContext context;
        try {
            context = contextCodec.decodeResultContext(
                    request.resultContextToken(),
                    actor.userId(),
                    now);
        } catch (DomainException exception) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "SEARCH_EXPOSURE_CONTEXT_INVALID",
                    "탐색 노출 문맥이 올바르지 않거나 만료되었습니다.");
        }

        Map<BindingKey, Integer> expectedPagePositions = expectedPagePositions(context);
        Set<String> exposureIds = new HashSet<>();
        Set<String> idempotencyKeys = new HashSet<>();
        Set<Integer> pagePositions = new HashSet<>();
        Set<BindingKey> itemBindings = new HashSet<>();
        List<SearchExposureCommand.Item> items = new ArrayList<>(request.items().size());

        for (SearchExposureDtos.ItemRequest item : request.items()) {
            if (item == null) {
                throw batchInvalid();
            }
            requireIdentifier(item.exposureId());
            requireIdempotencyKey(item.idempotencyKey());
            if (!exposureIds.add(item.exposureId())
                    || !idempotencyKeys.add(item.idempotencyKey())) {
                throw batchInvalid();
            }
            if (item.postId() == null
                    || item.postId() <= 0
                    || item.absoluteRank() == null
                    || item.absoluteRank() <= 0
                    || item.pagePosition() == null
                    || item.pagePosition() <= 0
                    || item.pagePosition() > SearchExposureContract.MAX_PAGE_SIZE) {
                throw batchInvalid();
            }

            BindingKey binding = new BindingKey(item.postId(), item.absoluteRank());
            Integer expectedPosition = expectedPagePositions.get(binding);
            if (expectedPosition == null || expectedPosition.intValue() != item.pagePosition()) {
                throw bindingInvalid();
            }
            if (!pagePositions.add(item.pagePosition()) || !itemBindings.add(binding)) {
                throw batchInvalid();
            }

            if (item.visibleRatioBasisPoints() == null
                    || item.visibleRatioBasisPoints() < policy.minimumVisibleRatioBasisPoints()
                    || item.visibleRatioBasisPoints()
                            > SearchExposureContract.MAX_VISIBLE_RATIO_BASIS_POINTS
                    || item.dwellMilliseconds() == null
                    || item.dwellMilliseconds() < policy.minimumDwellMilliseconds()
                    || item.dwellMilliseconds() > policy.maximumDwellMilliseconds()) {
                throw ruleUnsupported();
            }
            if (item.exposedAt() == null) {
                throw timeInvalid();
            }
            Instant exposedAt = item.exposedAt().truncatedTo(ChronoUnit.MICROS);
            if (exposedAt.isBefore(context.issuedAt().minus(policy.contextClockSkew()))
                    || exposedAt.isAfter(context.expiresAt().plus(policy.contextClockSkew()))
                    || exposedAt.isAfter(now.plus(policy.maximumFutureSkew()))) {
                throw timeInvalid();
            }

            items.add(new SearchExposureCommand.Item(
                    item.exposureId(),
                    item.idempotencyKey(),
                    item.postId(),
                    item.absoluteRank(),
                    item.pagePosition(),
                    item.visibleRatioBasisPoints(),
                    item.dwellMilliseconds(),
                    exposedAt));
        }

        items.sort(Comparator
                .comparingInt(SearchExposureCommand.Item::pagePosition)
                .thenComparingInt(SearchExposureCommand.Item::absoluteRank)
                .thenComparingLong(SearchExposureCommand.Item::postId)
                .thenComparing(SearchExposureCommand.Item::exposureId));

        return new SearchExposureCommand(
                SearchExposureContract.SCHEMA_VERSION,
                actor.subjectRef(),
                actor.identityScheme(),
                actor.sessionId(),
                context.runId(),
                context.snapshotFingerprint(),
                context.queryFingerprint(),
                context.policyVersion(),
                request.pageOccurrenceId(),
                request.visibilityRuleVersion(),
                request.producerBuildId(),
                items);
    }

    private static void validateActor(SearchExposureActor actor) {
        if (actor.userId() <= 0
                || !SearchExposureContract.IDENTITY_SCHEME.equals(actor.identityScheme())
                || actor.subjectRef() == null
                || !SUBJECT_REF.matcher(actor.subjectRef()).matches()
                || actor.sessionId() == null
                || !IDENTIFIER.matcher(actor.sessionId()).matches()) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "SEARCH_EXPOSURE_ACTOR_INVALID",
                    "탐색 노출 사용자 문맥이 올바르지 않습니다.");
        }
    }

    private static Map<BindingKey, Integer> expectedPagePositions(
            SearchContextCodec.ResultContext context) {
        Map<BindingKey, Integer> positions = new HashMap<>();
        List<SearchContextCodec.ResultBinding> bindings = context.bindings();
        if (bindings.isEmpty() || bindings.size() > SearchExposureContract.MAX_PAGE_SIZE) {
            throw bindingInvalid();
        }
        for (int index = 0; index < bindings.size(); index++) {
            SearchContextCodec.ResultBinding binding = bindings.get(index);
            BindingKey key = new BindingKey(binding.postId(), binding.absoluteRank());
            if (positions.putIfAbsent(key, index + 1) != null) {
                throw bindingInvalid();
            }
        }
        return Map.copyOf(positions);
    }

    private static void requireIdentifier(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw batchInvalid();
        }
    }

    private static void requireIdempotencyKey(String value) {
        if (value == null || !IDEMPOTENCY_KEY.matcher(value).matches()) {
            throw batchInvalid();
        }
    }

    private static DomainException batchInvalid() {
        return new DomainException(
                HttpStatus.BAD_REQUEST,
                "SEARCH_EXPOSURE_BATCH_INVALID",
                "탐색 노출 요청 형식을 확인해 주세요.");
    }

    private static DomainException bindingInvalid() {
        return new DomainException(
                HttpStatus.FORBIDDEN,
                "SEARCH_EXPOSURE_BINDING_INVALID",
                "탐색 결과와 노출 게시물·순위·위치 연결이 올바르지 않습니다.");
    }

    private static DomainException ruleUnsupported() {
        return new DomainException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "SEARCH_EXPOSURE_RULE_UNSUPPORTED",
                "지원하지 않는 탐색 노출 판정 규칙입니다.");
    }

    private static DomainException timeInvalid() {
        return new DomainException(
                HttpStatus.BAD_REQUEST,
                "SEARCH_EXPOSURE_TIME_INVALID",
                "탐색 노출 발생 시각이 허용 범위를 벗어났습니다.");
    }

    private record BindingKey(long postId, int absoluteRank) {}
}
