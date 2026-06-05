package com.oit.dondok.domain.mission.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MissionLogCreateRequest(
    @NotNull(message = "crew_id는 필수입니다.") @JsonProperty("crew_id") Long crewId,
    @JsonProperty("image_s3_key") String imageS3Key,
    @NotBlank(message = "caption은 필수입니다.")
        @Size(min = 5, max = 100, message = "caption은 5자 이상 100자 이하여야 합니다.")
        @JsonProperty("caption")
        String caption) {}
