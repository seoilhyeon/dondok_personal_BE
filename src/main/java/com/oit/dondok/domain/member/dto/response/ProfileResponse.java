package com.oit.dondok.domain.member.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.member.entity.MemberStatus;
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
    OffsetDateTime createdAt) {}
