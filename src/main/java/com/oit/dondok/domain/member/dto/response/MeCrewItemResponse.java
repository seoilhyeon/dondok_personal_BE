package com.oit.dondok.domain.member.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MeCrewItemResponse(
    Long crewId,
    String title,
    String imageUrl,
    String category,
    CrewStatus status,
    Long depositAmount,
    String myRole,
    String myStatus,
    OffsetDateTime startAt,
    OffsetDateTime endAt) {

  public static MeCrewItemResponse of(
      CrewParticipant participant, UUID memberUuid, String imageUrl) {
    Crew crew = participant.getCrew();
    boolean isHost = crew.getHostMember().getUuid().equals(memberUuid);
    return new MeCrewItemResponse(
        crew.getId(),
        crew.getTitle(),
        imageUrl,
        crew.getCategory(),
        crew.getStatus(),
        participant.getDepositAmount(),
        isHost ? "HOST" : "MEMBER",
        "LOCKED",
        SeoulDateTimeUtils.toSeoulOffset(crew.getStartAt()),
        SeoulDateTimeUtils.toSeoulOffset(crew.getEndAt()));
  }
}
