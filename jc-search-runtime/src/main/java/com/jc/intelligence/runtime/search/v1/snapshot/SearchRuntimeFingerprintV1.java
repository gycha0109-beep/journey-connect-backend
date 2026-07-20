package com.jc.intelligence.runtime.search.v1.snapshot;

import com.jc.intelligence.contract.v1.search.query.SearchFilterCanonicalizerV1;
import com.jc.intelligence.contract.v1.search.query.SearchRequestV1;
import com.jc.intelligence.contract.v1.search.serialization.SearchContractJsonCodecV1;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankedCandidateV1;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class SearchRuntimeFingerprintV1 {
    private SearchRuntimeFingerprintV1() { }

    public static String request(SearchRequestV1 request) {
        return sha256(SearchContractJsonCodecV1.writeRequest(request));
    }

    public static String filters(SearchRequestV1 request) {
        return SearchFilterCanonicalizerV1.fingerprint(request.filters());
    }

    public static String candidates(List<SearchRankedCandidateV1> candidates) {
        StringBuilder builder = new StringBuilder();
        for (SearchRankedCandidateV1 item : candidates) {
            builder.append(item.candidate().entityRef().value()).append('|')
                    .append(item.rankingScore() == null ? "null" : Double.toHexString(item.rankingScore())).append('|')
                    .append(item.explicitOrderingKey() == null ? "null" : item.explicitOrderingKey()).append('|')
                    .append(item.candidate().sourceRank() == null ? "null" : item.candidate().sourceRank()).append('\n');
        }
        return sha256(builder.toString());
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
