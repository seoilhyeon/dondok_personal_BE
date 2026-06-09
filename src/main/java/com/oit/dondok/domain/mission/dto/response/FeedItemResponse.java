package com.oit.dondok.domain.mission.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FeedItemResponse(
    Long missionLogId,
    Long crewId,
    String crewName,
    Long crewParticipantId,
    UUID memberUuid, // member.id(Long) 노출 금지 -> uuid만
    String nickname,
    String profileImageUrl,
    String imageUrl,
    String caption,
    OffsetDateTime serverTime,
    OffsetDateTime createAt,
    CertificationStatus certificationStatus,
    BigDecimal shareRatio,
    Map<String, Long> reactionCounts,
    List<String> myReactions) {}
