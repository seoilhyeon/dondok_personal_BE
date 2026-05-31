package com.oit.dondok.domain.member.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.member.repository.MemberProfileProjection;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProfileResponse(
    UUID memberUuid,
    String email,
    String nickname,
    String profileImageUrl,
    String statusMessage,
    boolean isHostEver,
    long hostedCrewCount,
    MemberStatus status,
    OffsetDateTime createdAt) {

  public static ProfileResponse from(MemberProfileProjection profile, String profileImageUrl) {
    return new ProfileResponse(
        profile.memberUuid(),
        profile.email(),
        profile.nickname(),
        profileImageUrl,
        profile.statusMessage(),
        profile.isHostEver(),
        profile.hostedCrewCount(),
        profile.status(),
        profile.createdAt());
  }
}
