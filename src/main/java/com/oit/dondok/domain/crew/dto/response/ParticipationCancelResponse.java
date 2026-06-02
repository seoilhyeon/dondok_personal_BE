package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParticipationCancelResponse(
    Long crewParticipantId, Long crewId, CrewParticipantStatus status, OffsetDateTime cancelledAt) {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  public static ParticipationCancelResponse of(CrewParticipant participant, Long crewId) {
    return new ParticipationCancelResponse(
        participant.getId(),
        crewId,
        participant.getStatus(),
        toSeoulOffset(participant.getCancelledAt()));
  }

  private static OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
    if (ldt == null) {
      return null;
    }
    return ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }
}
