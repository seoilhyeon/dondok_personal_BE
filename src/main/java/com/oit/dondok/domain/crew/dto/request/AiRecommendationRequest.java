package com.oit.dondok.domain.crew.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record AiRecommendationRequest(
    @JsonProperty("seed_text") @NotBlank(message = "목표 텍스트는 필수입니다.") String seedText) {}
