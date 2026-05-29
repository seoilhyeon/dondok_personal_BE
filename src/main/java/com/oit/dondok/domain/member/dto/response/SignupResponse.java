package com.oit.dondok.domain.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oit.dondok.domain.member.entity.MemberStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record SignupResponse(
    @JsonProperty("member_uuid") UUID memberUuid,
    String email,
    String nickname,
    MemberStatus status,
    @JsonProperty("created_at") LocalDateTime createdAt) {

  public static SignupResponse of(
      UUID memberUuid,
      String email,
      String nickname,
      MemberStatus status,
      LocalDateTime createdAt) {
    return new SignupResponse(memberUuid, email, nickname, status, createdAt);
  }
}
