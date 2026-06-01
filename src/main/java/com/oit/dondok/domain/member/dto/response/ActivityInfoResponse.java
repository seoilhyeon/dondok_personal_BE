package com.oit.dondok.domain.member.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ActivityInfoResponse(
    CrewActivityInfoResponse crew,
    long totalVerificationCount,
    long pendingReviewCount,
    HostOperationActivityInfoResponse hostOperation,
    long unreadNotificationCount) {}
