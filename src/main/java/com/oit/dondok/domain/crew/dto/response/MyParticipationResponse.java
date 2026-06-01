package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MyParticipationResponse(
    Long crewParticipantId,
    CrewParticipantStatus status,
    Long depositLockedAmount,
    OffsetDateTime lockedAt) {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  public static MyParticipationResponse from(CrewParticipant participant) {
    return new MyParticipationResponse(
        participant.getId(),
        participant.getStatus(),
        participant.getDepositAmount(),
        toSeoulOffset(participant.getLockedAt()));
  }

  private static OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
    if (ldt == null) {
      return null;
    }
    return ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }
}
