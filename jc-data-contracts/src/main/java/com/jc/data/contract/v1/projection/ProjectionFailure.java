package com.jc.data.contract.v1.projection;

import java.util.Objects;

public record ProjectionFailure(ProjectionFailureCode code, String field, String detail) {
    public ProjectionFailure {
        Objects.requireNonNull(code, "code");
        field = ProjectionEngineSupport.requireToken(field, "field", 96);
        detail = ProjectionEngineSupport.requireToken(detail, "detail", 160);
    }
}
