package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParticipationRejectResponse(
    Long crewParticipantId, Long crewId, CrewParticipantStatus status, OffsetDateTime rejectedAt) {

  public static ParticipationRejectResponse from(CrewParticipant participant) {
    return new ParticipationRejectResponse(
        participant.getId(),
        participant.getCrew().getId(),
        participant.getStatus(),
        SeoulDateTimeUtils.toSeoulOffset(participant.getRejectedAt()));
  }
}
