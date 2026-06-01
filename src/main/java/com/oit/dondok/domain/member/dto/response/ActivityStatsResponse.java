package com.oit.dondok.domain.member.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ActivityStatsResponse(
    long totalRecognizedSuccessCount,
    String highestShareRatio,
    Long highestShareRatioCrewId,
    String highestShareRatioCrewTitle,
    String averageSuccessRate) {}
