package com.oit.dondok.domain.mission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.dto.response.MissionModerationResponse;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.ModerationHistory;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.mission.repository.ModerationHistoryRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MissionModerationService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final ModerationHistoryRepository moderationHistoryRepository;
  private final SettlementRepository settlementRepository;
  private final MissionLogQueryRepository missionLogQueryRepository;
  private final ObjectMapper objectMapper;

  /*
  검수 대기 중인 미션 인증을 방장 수동 승인으로 처리.
  승인 성공 시 MissionLog는 SUCCESS/MANUAL_APPROVE 상태가 된다.
  ModerationHistory에는 승인 전후 스냅샷이 추가된다.
   */
  @Transactional
  public MissionModerationResponse approve(UUID memberUuid, Long missionLogId) {
    MissionLog missionLog =
        missionLogQueryRepository
            .findByIdWithCrewForModeration(missionLogId)
            .orElseThrow(() -> new CustomException(MissionErrorCode.MISSION_LOG_NOT_FOUND));

    Long crewId = missionLog.getCrewParticipant().getCrew().getId();
    Member moderator = missionLog.getCrewParticipant().getCrew().getHostMember();

    validateHost(memberUuid, moderator);
    validateReviewable(missionLog);
    validateSettlementNotStarted(crewId);

    LocalDateTime decidedAt = LocalDateTime.now(SEOUL);
    String beforeState = snapshotOf(missionLog);

    missionLog.approveManually(moderator, decidedAt);

    String afterState = snapshotOf(missionLog);

    ModerationHistory history =
        moderationHistoryRepository.save(
            ModerationHistory.createManualApprove(
                missionLog, beforeState, afterState, moderator, decidedAt));

    return MissionModerationResponse.from(
        missionLog, history, decidedAt.atZone(SEOUL).toOffsetDateTime());
  }

  // 요청자가 해당 크루의 방장인지 검증
  private void validateHost(UUID memberUuid, Member host) {
    if (!host.getUuid().equals(memberUuid)) {
      throw new CustomException(MissionErrorCode.FORBIDDEN_NOT_HOST);
    }
  }

  // 검수 결정 전후 상태를 검사 이력에 남기기 위한 JSON 스냅샷으로 변환
  private String snapshotOf(MissionLog missionLog) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("certification_status", missionLog.getCertificationStatus());
    snapshot.put("failure_reason", missionLog.getFailureReason());
    snapshot.put("decision_type", missionLog.getDecisionType());
    snapshot.put("reject_reason_code", missionLog.getRejectReasonCode());
    snapshot.put("reject_memo", missionLog.getRejectMemo());
    snapshot.put(
        "moderator_id",
        missionLog.getModerator() == null ? null : missionLog.getModerator().getId());
    snapshot.put("moderator_decided_at", missionLog.getModeratorDecidedAt());

    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize moderation snapshot.", exception);
    }
  }

  // 정산이 시작된 이후에는 정산 결과와 인증 상태가 어긋날 수 있으므로 검수를 막는다.
  private void validateSettlementNotStarted(Long crewId) {
    if (settlementRepository.findByCrewId(crewId).isPresent()) {
      throw new CustomException(MissionErrorCode.SETTLEMENT_INPUT_FROZEN);
    }
  }

  // 검수 대기 상태가 아닌 인증 로그는 중복 승인이나 상태 재변경을 막는다.
  private void validateReviewable(MissionLog missionLog) {
    if (!missionLog.isPendingReview()) {
      throw new CustomException(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);
    }
  }
}
