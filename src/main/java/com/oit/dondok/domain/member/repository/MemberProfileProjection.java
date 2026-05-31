package com.oit.dondok.domain.member.repository;

import com.oit.dondok.domain.member.entity.MemberStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MemberProfileProjection(
    UUID memberUuid,
    String email,
    String nickname,
    String profileImageS3Key,
    String statusMessage,
    long hostedCrewCount,
    MemberStatus status,
    OffsetDateTime createdAt) {

  public boolean isHostEver() {
    return hostedCrewCount > 0;
  }
}
