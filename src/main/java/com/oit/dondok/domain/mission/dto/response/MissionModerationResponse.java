package com.oit.dondok.domain.mission.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import com.oit.dondok.domain.mission.entity.ModerationHistory;
import com.oit.dondok.domain.mission.entity.RejectReasonCode;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MissionModerationResponse(

    // 검수 처리된 미션 인증 로그 ID
    Long missionLogId,

    // 해당 미션 인증이 속한 크로 ID
    Long crewId,

    // 미션 인증을 제줓한 크루 참여자 ID
    Long crewParticipantId,

    // 검수 처리 후 인증 상태
    CertificationStatus certificationStatus,

    // 검수 결정 유형
    ModerationDecisionType decisionType,

    // 거절 사유 코드. 승인인 경우 null
    RejectReasonCode rejectReasonCode,

    // 방장이 검수 결정을 내린 시각
    OffsetDateTime decidedAt,

    // 이번 검수 결정으로 생성된 검수 이력 ID
    Long moderationHistoryId) {

  // 변경된 미션 로그와 새로 추가된 검수 이력으로 응답 DTO 생성
  public static MissionModerationResponse from(
      MissionLog missionLog, ModerationHistory history, OffsetDateTime decidedAt) {
    return new MissionModerationResponse(
        missionLog.getId(),
        missionLog.getCrewParticipant().getCrew().getId(),
        missionLog.getCrewParticipant().getId(),
        missionLog.getCertificationStatus(),
        missionLog.getDecisionType(),
        missionLog.getRejectReasonCode(),
        decidedAt,
        history.getId());
  }
}
