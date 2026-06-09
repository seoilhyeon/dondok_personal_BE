package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MyParticipationResponse(
    Long crewParticipantId,
    CrewParticipantStatus status,
    Long depositLockedAmount,
    OffsetDateTime lockedAt) {

  public static MyParticipationResponse from(CrewParticipant participant) {
    return new MyParticipationResponse(
        participant.getId(),
        participant.getStatus(),
        participant.getDepositAmount(),
        SeoulDateTimeUtils.toSeoulOffset(participant.getLockedAt()));
  }
}
