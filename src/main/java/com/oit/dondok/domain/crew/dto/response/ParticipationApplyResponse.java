package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParticipationApplyResponse(
    Long crewParticipantId,
    Long crewId,
    UUID memberUuid,
    CrewParticipantStatus status,
    Long depositReservedAmount,
    Long depositLockedAmount,
    OffsetDateTime lockedAt,
    OffsetDateTime pendingAt) {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  public static ParticipationApplyResponse from(
      CrewParticipant participant, Long crewId, UUID memberUuid) {
    return new ParticipationApplyResponse(
        participant.getId(),
        crewId,
        memberUuid,
        participant.getStatus(),
        participant.getDepositAmount(),
        0L,
        null,
        toSeoulOffset(participant.getPendingAt()));
  }

  private static OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
    if (ldt == null) {
      return null;
    }
    return ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }
}
