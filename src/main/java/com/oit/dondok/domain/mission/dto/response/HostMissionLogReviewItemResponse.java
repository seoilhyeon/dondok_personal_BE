package com.oit.dondok.domain.mission.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import com.oit.dondok.domain.mission.entity.RejectReasonCode;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HostMissionLogReviewItemResponse(
    Long missionLogId,
    Long crewId,
    Long crewParticipantId,
    UUID memberUuid,
    String nickname,
    String profileImageUrl,
    String imageUrl,
    String caption,
    OffsetDateTime serverTime,
    OffsetDateTime capturedAt,
    ExifRisk exifRisk,
    @JsonProperty("is_duplicate") boolean isDuplicate,
    String reviewBucket,
    CertificationStatus certificationStatus,
    ModerationDecisionType decisionType,
    RejectReasonCode rejectReasonCode,
    OffsetDateTime hostReviewableUntil) {}
