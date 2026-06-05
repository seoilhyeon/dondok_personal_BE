package com.oit.dondok.domain.mission.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.mission.entity.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MissionLogCreateResponse(
    Long missionLogId,
    Long crewId,
    Long crewParticipantId,
    String imageUrl,
    String imageS3Key,
    String caption,
    String imageHash,
    OffsetDateTime serverTime,
    CertificationStatus certificationStatus,
    MissionFailureReason failureReason,
    ModerationDecisionType decisionType,
    RejectReasonCode rejectReasonCode) {
  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  // imageUrl은 저장값이 아니라 read 시 ImageDeliveryPort로 파생되는 표시 URL이므로 서비스에서 주입받는다.
  public static MissionLogCreateResponse from(MissionLog missionLog, String imageUrl) {
    return new MissionLogCreateResponse(
        missionLog.getId(),
        missionLog.getCrewParticipant().getCrew().getId(),
        missionLog.getCrewParticipant().getId(),
        imageUrl,
        missionLog.getImageS3Key(),
        missionLog.getCaption(),
        missionLog.getImageHash(),
        toSeoulOffset(missionLog.getServerTime()),
        missionLog.getCertificationStatus(),
        missionLog.getFailureReason(),
        missionLog.getDecisionType(),
        missionLog.getRejectReasonCode());
  }

  private static OffsetDateTime toSeoulOffset(LocalDateTime ldt) {
    if (ldt == null) {
      return null;
    }
    return ldt.atZone(SEOUL_ZONE).toOffsetDateTime();
  }

}
