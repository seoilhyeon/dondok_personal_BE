package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParticipationCancelResponse(
    Long crewParticipantId, Long crewId, CrewParticipantStatus status, OffsetDateTime cancelledAt) {

  public static ParticipationCancelResponse of(CrewParticipant participant, Long crewId) {
    return new ParticipationCancelResponse(
        participant.getId(),
        crewId,
        participant.getStatus(),
        SeoulDateTimeUtils.toSeoulOffset(participant.getCancelledAt()));
  }
}
