package com.jc.backend.intelligence.search;

import com.jc.backend.common.DomainException;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SearchExposureService {

    private final SearchIdentityMappingReadPort identityMappingPort;
    private final SearchExposureValidator validator;
    private final SearchExposureCanonicalizer canonicalizer;
    private final SearchExposurePersistencePort persistencePort;

    public SearchExposureService(
            SearchIdentityMappingReadPort identityMappingPort,
            SearchExposureValidator validator,
            SearchExposureCanonicalizer canonicalizer,
            SearchExposurePersistencePort persistencePort) {
        this.identityMappingPort = identityMappingPort;
        this.validator = validator;
        this.canonicalizer = canonicalizer;
        this.persistencePort = persistencePort;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public SearchExposureDtos.BatchResponse record(
            long userId,
            String tokenId,
            SearchExposureDtos.BatchRequest request) {
        SearchIdentityMappingReadPort.ResolvedSubject resolved;
        try {
            resolved = identityMappingPort.resolve(userId);
        } catch (SearchIdentityMappingReadPort.MappingUnavailableException exception) {
            throw new DomainException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SEARCH_EXPOSURE_IDENTITY_UNAVAILABLE",
                    "탐색 노출 사용자 식별 정보를 확인할 수 없습니다.");
        }

        SearchExposureActor actor = new SearchExposureActor(
                userId,
                resolved.subjectRef(),
                resolved.identityScheme(),
                resolved.mappingVersion(),
                searchSessionId(userId, tokenId));
        SearchExposureCommand command = validator.validate(
                actor,
                request,
                Instant.now(),
                SearchExposureValidationPolicy.candidateV1());
        SearchExposureCanonicalizer.CanonicalBatch batch = canonicalizer.encode(command);

        SearchExposurePersistencePort.StoreBatchResult result;
        try {
            result = persistencePort.store(batch);
        } catch (SearchExposurePersistencePort.IdempotencyConflictException exception) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_CONFLICT",
                    "같은 멱등키가 다른 탐색 노출에 이미 사용되었습니다.");
        }
        if (result.status() == SearchExposurePersistencePort.Status.DISABLED_PENDING_APPROVAL) {
            throw new DomainException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SEARCH_EXPOSURE_PERSISTENCE_DISABLED",
                    "탐색 노출 저장 기능이 활성화되지 않았습니다.");
        }
        return new SearchExposureDtos.BatchResponse(
                result.storedCount(),
                result.duplicateCount(),
                result.status() == SearchExposurePersistencePort.Status.DUPLICATE
                        ? "duplicate" : "stored");
    }

    private static String searchSessionId(long userId, String tokenId) {
        if (tokenId != null && tokenId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            return tokenId;
        }
        return "search-jwt:" + SearchHashing.sha256(userId + ":" + String.valueOf(tokenId))
                .substring(0, 32);
    }
}
