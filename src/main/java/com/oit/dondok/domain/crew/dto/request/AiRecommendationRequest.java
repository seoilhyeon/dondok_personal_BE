package com.oit.dondok.domain.crew.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiRecommendationRequest(
    @JsonProperty("seed_text")
        @NotBlank(message = "목표 텍스트는 필수입니다.")
        @Size(max = 500, message = "목표 텍스트는 500자 이하여야 합니다.")
        String seedText) {}
