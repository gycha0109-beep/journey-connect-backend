package com.jc.backend.crew;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class CrewTagDtos {

    private CrewTagDtos() {}

    public record ReplaceRequest(
            @NotNull @Size(max = 5) List<@NotBlank @Size(max = 60) String> tagSlugs) {}

    public record TagView(
            Long id,
            String slug,
            String nameKo,
            String nameEn) {}
}
