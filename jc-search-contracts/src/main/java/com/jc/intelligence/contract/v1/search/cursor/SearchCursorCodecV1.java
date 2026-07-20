package com.jc.intelligence.contract.v1.search.cursor;

import com.jc.intelligence.contract.v1.search.serialization.SearchContractJsonCodecV1;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class SearchCursorCodecV1 {
    private static final int MAX_ENCODED_LENGTH = 32768;
    private SearchCursorCodecV1() { }

    public static String encode(SearchCursorV1 cursor) {
        SearchCursorValidatorV1.validateChecksum(cursor);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                SearchContractJsonCodecV1.writeCursor(cursor).getBytes(StandardCharsets.UTF_8));
    }

    public static SearchCursorV1 decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_LENGTH) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_CURSOR_INVALID, "invalid encoded cursor length");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            SearchCursorV1 cursor = SearchContractJsonCodecV1.readCursor(new String(decoded, StandardCharsets.UTF_8));
            SearchCursorValidatorV1.validateChecksum(cursor);
            return cursor;
        } catch (IllegalArgumentException exception) {
            if (exception instanceof com.jc.intelligence.contract.v1.search.validation.SearchContractValidationException validation) {
                throw validation;
            }
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_CURSOR_INVALID, "cursor decoding failed");
        }
    }
}
