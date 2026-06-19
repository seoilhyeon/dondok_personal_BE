package com.oit.dondok.domain.mission.repository;

import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import com.oit.dondok.domain.mission.entity.RejectReasonCode;
import java.time.LocalDateTime;
import java.util.UUID;

// 피드 QueryDSL projection. image/profile은 S3 Key로 받아 서비스에서 ImageDeliveryPort로 URL 파생한다.
public record FeedItemRow(
    Long missionLogId,
    Long crewId,
    String crewTitle, // 응답 crew_name
    Long crewParticipantId,
    UUID memberUuid,
    String nickname,
    String profileImageS3Key,
    String imageS3Key,
    String caption,
    LocalDateTime serverTime,
    LocalDateTime exifTakenAt,
    ExifRisk exifRisk,
    boolean duplicateHash,
    CertificationStatus certificationStatus,
    RejectReasonCode rejectReasonCode,
    ModerationDecisionType decisionType) {}
