package com.oit.dondok.domain.dashboard.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CrewDashboardParticipantResponse(
    Long crewParticipantId,
    String nickname,
    String shareRatio, // 소수 오해 방지 string decimal. 산출 불가 시 null
    @JsonProperty("is_me") boolean isMe) {}
