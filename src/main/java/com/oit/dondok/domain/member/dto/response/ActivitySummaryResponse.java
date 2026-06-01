package com.oit.dondok.domain.member.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ActivitySummaryResponse(
    UUID memberUuid,
    ActivityInfoResponse activityInfo,
    ActivityStatsResponse activityStats,
    OffsetDateTime generatedAt) {}
