package com.jc.backend.intelligence.search;

import com.jc.backend.common.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class SearchContextCodec {

    public static final String SNAPSHOT_VERSION = "search-snapshot-v1";
    public static final String RESULT_CONTEXT_VERSION = "search-result-context-v1";
    private static final String SNAPSHOT_PREFIX = "sc1.";
    private static final String RESULT_PREFIX = "src1.";
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    private static final Pattern FINGERPRINT = Pattern.compile("^[0-9a-f]{64}$");

    private final String secret;
    private final Duration ttl;

    public SearchContextCodec(
            @Value("${app.recommendation.search.snapshot-secret:${app.security.jwt-secret}}") String secret,
            @Value("${app.recommendation.search.snapshot-ttl-seconds:900}") long ttlSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("search snapshot secret must be at least 32 bytes");
        }
        if (ttlSeconds < 30 || ttlSeconds > 86_400) {
            throw new IllegalArgumentException("search snapshot TTL must be between 30 and 86400 seconds");
        }
        this.secret = secret;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public String encodeSnapshot(
            String runId,
            long userId,
            String queryFingerprint,
            Instant referenceTime,
            int pageSize,
            String snapshotFingerprint,
            String policyVersion,
            Instant issuedAt) {
        requireId(runId, "runId");
        requireFingerprint(queryFingerprint, "queryFingerprint");
        requireFingerprint(snapshotFingerprint, "snapshotFingerprint");
        requireId(policyVersion, "policyVersion");
        if (userId <= 0 || pageSize < 1 || pageSize > 100
                || referenceTime == null || issuedAt == null) {
            throw new IllegalArgumentException("search snapshot binding is invalid");
        }
        Instant expiresAt = issuedAt.plus(ttl);
        String body = String.join("|",
                runId,
                Long.toString(userId),
                queryFingerprint,
                Long.toString(referenceTime.toEpochMilli()),
                Integer.toString(pageSize),
                snapshotFingerprint,
                policyVersion,
                Long.toString(issuedAt.toEpochMilli()),
                Long.toString(expiresAt.toEpochMilli()));
        return encode(SNAPSHOT_PREFIX, "journey-connect:search-snapshot:v1", body);
    }

    public SnapshotContext decodeSnapshot(
            String token,
            long expectedUserId,
            String expectedQueryFingerprint,
            int expectedPageSize,
            Instant now) {
        try {
            String body = decode(
                    token,
                    SNAPSHOT_PREFIX,
                    "journey-connect:search-snapshot:v1",
                    2_048);
            String[] values = body.split("\\|", -1);
            if (values.length != 9) {
                throw snapshotExpired();
            }
            String runId = values[0];
            long userId = Long.parseLong(values[1]);
            String queryFingerprint = values[2];
            Instant referenceTime = Instant.ofEpochMilli(Long.parseLong(values[3]));
            int pageSize = Integer.parseInt(values[4]);
            String snapshotFingerprint = values[5];
            String policyVersion = values[6];
            Instant issuedAt = Instant.ofEpochMilli(Long.parseLong(values[7]));
            Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(values[8]));
            requireId(runId, "runId");
            requireFingerprint(queryFingerprint, "queryFingerprint");
            requireFingerprint(snapshotFingerprint, "snapshotFingerprint");
            requireId(policyVersion, "policyVersion");
            if (userId != expectedUserId
                    || !queryFingerprint.equals(expectedQueryFingerprint)
                    || pageSize != expectedPageSize
                    || pageSize < 1
                    || pageSize > 100
                    || issuedAt.isAfter(now.plusSeconds(5))
                    || !expiresAt.isAfter(now)) {
                throw snapshotExpired();
            }
            return new SnapshotContext(
                    runId,
                    userId,
                    queryFingerprint,
                    referenceTime,
                    pageSize,
                    snapshotFingerprint,
                    policyVersion,
                    issuedAt,
                    expiresAt);
        } catch (DomainException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw snapshotExpired();
        }
    }

    public String encodeResultContext(
            SnapshotContext snapshot,
            List<ResultBinding> bindings,
            Instant issuedAt) {
        if (snapshot == null || bindings == null || bindings.size() > 100 || issuedAt == null) {
            throw new IllegalArgumentException("search result context is invalid");
        }
        String bindingValue = bindings.stream()
                .map(binding -> binding.postId() + ":" + binding.absoluteRank())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String body = String.join("|",
                snapshot.runId(),
                Long.toString(snapshot.userId()),
                snapshot.queryFingerprint(),
                snapshot.snapshotFingerprint(),
                snapshot.policyVersion(),
                Long.toString(issuedAt.toEpochMilli()),
                Long.toString(snapshot.expiresAt().toEpochMilli()),
                bindingValue);
        return encode(RESULT_PREFIX, "journey-connect:search-result-context:v1", body);
    }

    public ResultContext decodeResultContext(
            String token,
            long expectedUserId,
            Instant now) {
        try {
            String body = decode(
                    token,
                    RESULT_PREFIX,
                    "journey-connect:search-result-context:v1",
                    8_192);
            String[] values = body.split("\\|", -1);
            if (values.length != 8) {
                throw resultContextInvalid();
            }
            String runId = values[0];
            long userId = Long.parseLong(values[1]);
            String queryFingerprint = values[2];
            String snapshotFingerprint = values[3];
            String policyVersion = values[4];
            Instant issuedAt = Instant.ofEpochMilli(Long.parseLong(values[5]));
            Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(values[6]));
            requireId(runId, "runId");
            requireFingerprint(queryFingerprint, "queryFingerprint");
            requireFingerprint(snapshotFingerprint, "snapshotFingerprint");
            requireId(policyVersion, "policyVersion");
            if (userId != expectedUserId
                    || issuedAt.isAfter(now.plusSeconds(5))
                    || !expiresAt.isAfter(now)) {
                throw resultContextInvalid();
            }
            List<ResultBinding> bindings = parseBindings(values[7]);
            return new ResultContext(
                    runId,
                    userId,
                    queryFingerprint,
                    snapshotFingerprint,
                    policyVersion,
                    issuedAt,
                    expiresAt,
                    bindings);
        } catch (DomainException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw resultContextInvalid();
        }
    }

    private List<ResultBinding> parseBindings(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        String[] raw = value.split(",", -1);
        if (raw.length > 100) {
            throw resultContextInvalid();
        }
        List<ResultBinding> bindings = new ArrayList<>(raw.length);
        for (String item : raw) {
            String[] parts = item.split(":", -1);
            if (parts.length != 2) {
                throw resultContextInvalid();
            }
            long postId = Long.parseLong(parts[0]);
            int absoluteRank = Integer.parseInt(parts[1]);
            bindings.add(new ResultBinding(postId, absoluteRank));
        }
        return List.copyOf(bindings);
    }

    private String encode(String prefix, String domain, String body) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return prefix + encoded + "." + SearchHashing.hmacSha256(secret, domain, encoded);
    }

    private String decode(String token, String prefix, String domain, int maxLength) {
        if (token == null || !token.startsWith(prefix) || token.length() > maxLength) {
            throw new IllegalArgumentException("invalid token");
        }
        String[] parts = token.substring(prefix.length()).split("\\.", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid token");
        }
        byte[] provided = HexFormat.of().parseHex(parts[1]);
        byte[] expected = HexFormat.of().parseHex(
                SearchHashing.hmacSha256(secret, domain, parts[0]));
        if (!MessageDigest.isEqual(provided, expected)) {
            throw new IllegalArgumentException("invalid signature");
        }
        return new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    }

    private static void requireId(String value, String name) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " format is invalid");
        }
    }

    private static void requireFingerprint(String value, String name) {
        if (value == null || !FINGERPRINT.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " format is invalid");
        }
    }

    private static DomainException snapshotExpired() {
        return new DomainException(
                HttpStatus.CONFLICT,
                "SEARCH_SNAPSHOT_EXPIRED",
                "탐색 결과가 변경되었습니다. 첫 페이지부터 다시 요청해 주세요.");
    }

    private static DomainException resultContextInvalid() {
        return new DomainException(
                HttpStatus.FORBIDDEN,
                "SEARCH_RESULT_CONTEXT_INVALID",
                "탐색 결과와 행동 정보의 연결이 올바르지 않습니다.");
    }

    public record SnapshotContext(
            String runId,
            long userId,
            String queryFingerprint,
            Instant referenceTime,
            int pageSize,
            String snapshotFingerprint,
            String policyVersion,
            Instant issuedAt,
            Instant expiresAt) {}

    public record ResultBinding(long postId, int absoluteRank) {
        public ResultBinding {
            if (postId <= 0 || absoluteRank <= 0) {
                throw new IllegalArgumentException("search result binding is invalid");
            }
        }
    }

    public record ResultContext(
            String runId,
            long userId,
            String queryFingerprint,
            String snapshotFingerprint,
            String policyVersion,
            Instant issuedAt,
            Instant expiresAt,
            List<ResultBinding> bindings) {
        public ResultContext {
            bindings = List.copyOf(bindings);
        }

        public boolean contains(long postId, int absoluteRank) {
            return bindings.stream().anyMatch(binding ->
                    binding.postId() == postId && binding.absoluteRank() == absoluteRank);
        }
    }
}
