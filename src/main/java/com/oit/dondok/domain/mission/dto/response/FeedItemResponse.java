package com.oit.dondok.domain.mission.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import com.oit.dondok.domain.mission.entity.RejectReasonCode;
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
    OffsetDateTime exifTakenAt,
    ExifRisk exifRisk,
    @JsonProperty("is_duplicate") boolean isDuplicate,
    CertificationStatus certificationStatus,
    Map<String, Long> reactionCounts,
    List<String> myReactions,
    RejectReasonCode rejectReasonCode,
    ModerationDecisionType decisionType) {}
