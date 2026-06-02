package com.oit.dondok.domain.member.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.member.entity.Member;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProfileUpdateResponse(
    UUID memberUuid,
    String email,
    String nickname,
    String profileImageUrl,
    String statusMessage,
    OffsetDateTime updatedAt) {

  public static ProfileUpdateResponse from(Member member, String profileImageUrl) {
    return new ProfileUpdateResponse(
        member.getUuid(),
        member.getEmail(),
        member.getNickname(),
        profileImageUrl,
        member.getStatusMessage(),
        toSeoulOffset(member.getUpdatedAt()));
  }

  private static OffsetDateTime toSeoulOffset(LocalDateTime dateTime) {
    return dateTime == null ? null : dateTime.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
  }
}
