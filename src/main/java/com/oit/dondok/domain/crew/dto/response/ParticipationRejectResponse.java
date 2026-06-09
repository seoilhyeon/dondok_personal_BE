package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParticipationRejectResponse(
        Long crewId,
        Long participantId,
        CrewParticipantStatus status,
        OffsetDateTime rejectedAt) {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    public static ParticipationRejectResponse from(CrewParticipant participant) {
        return new ParticipationRejectResponse(
                participant.getCrew().getId(),
                participant.getId(),
                participant.getStatus(),
                participant.getRejectedAt().atZone(SEOUL_ZONE).toOffsetDateTime());
    }
}