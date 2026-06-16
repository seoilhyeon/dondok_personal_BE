package com.oit.dondok.domain.member.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MemberPublicProfileResponse(
    UUID memberUuid,
    String nickname,
    String profileImageUrl,
    String statusMessage,
    OffsetDateTime joinedAt,
    PublicActivityInfoResponse activityInfo,
    ActivityStatsResponse activityStats) {}
