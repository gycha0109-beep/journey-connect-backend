package com.jc.backend.intelligence.search;

public interface SearchIdentityMappingReadPort {

    ResolvedSubject resolve(long userId);

    record ResolvedSubject(String subjectRef, String identityScheme, String mappingVersion) {}

    final class MappingUnavailableException extends IllegalStateException {
        public MappingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
