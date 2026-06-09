package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParticipationApproveResponse(
    Long crewParticipantId,
    Long crewId,
    CrewParticipantStatus status,
    Long depositLockedAmount,
    OffsetDateTime lockedAt) {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  public static ParticipationApproveResponse from(CrewParticipant participant) {
    return new ParticipationApproveResponse(
        participant.getId(),
        participant.getCrew().getId(),
        participant.getStatus(),
        participant.getDepositAmount(),
        toSeoulOffset(participant.getLockedAt()));
  }

  private static OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }
}
