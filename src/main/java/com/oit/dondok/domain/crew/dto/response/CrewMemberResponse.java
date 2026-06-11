package com.oit.dondok.domain.crew.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CrewMemberResponse(
    Long crewParticipantId,
    UUID memberUuid,
    String nickname,
    String profileImageUrl,
    String role,
    String status,
    OffsetDateTime joinedAt) {

  public static CrewMemberResponse from(
      CrewParticipant participant, UUID hostUuid, String profileImageUrl) {
    String role = participant.getMember().getUuid().equals(hostUuid) ? "HOST" : "MEMBER";
    return new CrewMemberResponse(
        participant.getId(),
        participant.getMember().getUuid(),
        participant.getMember().getNickname(),
        profileImageUrl,
        role,
        "LOCKED",
        SeoulDateTimeUtils.toSeoulOffset(participant.getLockedAt()));
  }
}
