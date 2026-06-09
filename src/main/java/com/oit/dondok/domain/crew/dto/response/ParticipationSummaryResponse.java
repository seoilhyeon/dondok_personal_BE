package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParticipationSummaryResponse(
    Long participantId,
    String memberNickname,
    CrewParticipantStatus status,
    OffsetDateTime pendingAt) {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  public static ParticipationSummaryResponse from(CrewParticipant participant) {
    return new ParticipationSummaryResponse(
        participant.getId(),
        participant.getMember().getNickname(),
        participant.getStatus(),
        participant.getPendingAt().atZone(SEOUL_ZONE).toOffsetDateTime());
  }
}
