package com.jc.backend.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserReportDtos {

    private UserReportDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 30) String reasonCategory,
            @Size(max = 1000) String reasonDetail) {}

    public record CreateResult(long reportId, String status) {}
}
