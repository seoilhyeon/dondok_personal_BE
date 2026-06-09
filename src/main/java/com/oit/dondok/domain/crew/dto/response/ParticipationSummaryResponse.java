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
public record ParticipationSummaryResponse(
    Long crewParticipantId,
    UUID memberUuid,
    String nickname,
    String profileImageUrl,
    CrewParticipantStatus status,
    OffsetDateTime appliedAt,
    OffsetDateTime decidedAt) {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  public static ParticipationSummaryResponse from(CrewParticipant participant) {
    return new ParticipationSummaryResponse(
        participant.getId(),
        participant.getMember().getUuid(),
        participant.getMember().getNickname(),
        null,
        participant.getStatus(),
        participant.getPendingAt().atZone(SEOUL_ZONE).toOffsetDateTime(),
        decidedAt(participant));
  }

  private static OffsetDateTime decidedAt(CrewParticipant participant) {
    LocalDateTime decided =
        switch (participant.getStatus()) {
          case LOCKED -> participant.getLockedAt();
          case REJECTED -> participant.getRejectedAt();
          case CANCELLED -> participant.getCancelledAt();
          default -> null;
        };
    return decided == null ? null : decided.atZone(SEOUL_ZONE).toOffsetDateTime();
  }
}
