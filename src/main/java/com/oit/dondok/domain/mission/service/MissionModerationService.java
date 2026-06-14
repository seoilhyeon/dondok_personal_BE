package com.oit.dondok.domain.mission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.dto.response.MissionModerationResponse;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.entity.ModerationHistory;
import com.oit.dondok.domain.mission.entity.RejectReasonCode;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.mission.repository.ModerationHistoryRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.domain.settlement.service.SettlementNotificationService;
import com.oit.dondok.global.exception.CustomException;
import java.time.Duration;
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
  private static final Duration HOST_REVIEW_GRACE_DURATION = Duration.ofHours(72);

  private final ModerationHistoryRepository moderationHistoryRepository;
  private final SettlementRepository settlementRepository;
  private final MissionLogQueryRepository missionLogQueryRepository;
  private final MissionRuleRepository missionRuleRepository;
  private final ObjectMapper objectMapper;
  private final SettlementNotificationService settlementNotificationService;

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
    validateOperationalState(missionLog);
    validateReviewable(missionLog);
    LocalDateTime decidedAt = LocalDateTime.now(SEOUL);
    validateReviewPeriodNotExpired(missionLog, decidedAt);
    validateSettlementNotStarted(crewId);

    String beforeState = snapshotOf(missionLog);

    missionLog.approveManually(moderator, decidedAt);
    settlementNotificationService.sendExpectedRefundChangedNotifications(
        crewId, missionLog.getCrewParticipant().getCrew().getTitle());

    String afterState = snapshotOf(missionLog);

    ModerationHistory history =
        moderationHistoryRepository.save(
            ModerationHistory.createManualApprove(
                missionLog, beforeState, afterState, moderator, decidedAt));

    return MissionModerationResponse.from(
        missionLog, history, decidedAt.atZone(SEOUL).toOffsetDateTime());
  }

  // 방장이 검수 대기 중인 인증을 수동 거절한다.
  @Transactional
  public MissionModerationResponse reject(
      UUID memberUuid, Long missionLogId, RejectReasonCode rejectReasonCode, String rejectMemo) {
    MissionLog missionLog =
        missionLogQueryRepository
            .findByIdWithCrewForModeration(missionLogId)
            .orElseThrow(() -> new CustomException(MissionErrorCode.MISSION_LOG_NOT_FOUND));

    Long crewId = missionLog.getCrewParticipant().getCrew().getId();
    Member moderator = missionLog.getCrewParticipant().getCrew().getHostMember();

    validateHost(memberUuid, moderator);
    validateOperationalState(missionLog);
    validateReviewable(missionLog);
    LocalDateTime decidedAt = LocalDateTime.now(SEOUL);
    validateReviewPeriodNotExpired(missionLog, decidedAt);
    validateSettlementNotStarted(crewId);

    validateRejectMemo(rejectReasonCode, rejectMemo);

    String beforeState = snapshotOf(missionLog);

    missionLog.rejectManually(moderator, rejectReasonCode, rejectMemo, decidedAt);
    settlementNotificationService.sendExpectedRefundChangedNotifications(
        crewId, missionLog.getCrewParticipant().getCrew().getTitle());

    String afterState = snapshotOf(missionLog);

    ModerationHistory history =
        moderationHistoryRepository.save(
            ModerationHistory.createManualReject(
                missionLog,
                beforeState,
                afterState,
                moderator,
                rejectReasonCode,
                rejectMemo,
                decidedAt));

    return MissionModerationResponse.from(
        missionLog, history, decidedAt.atZone(SEOUL).toOffsetDateTime());
  }

  // OTHER는 메모가 필수이며, 모든 거절 메모는 50자 이하로 제한한다.
  private void validateRejectMemo(RejectReasonCode rejectReasonCode, String rejectMemo) {
    if (rejectReasonCode == RejectReasonCode.OTHER
        && (rejectMemo == null || rejectMemo.isBlank())) {
      throw new CustomException(MissionErrorCode.REJECT_MEMO_REQUIRED);
    }

    if (rejectMemo != null && rejectMemo.length() > 50) {
      throw new CustomException(MissionErrorCode.REJECT_MEMO_TOO_LONG);
    }
  }

  // 요청자가 해당 크루의 방장인지 검증한다.
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
      throw new CustomException(MissionErrorCode.MISSION_MODERATION_SNAPSHOT_SERIALIZATION_FAILED);
    }
  }

  // 정산이 시작된 이후에는 정산 결과와 인증 상태가 어긋날 수 있으므로 검수를 막는다.
  private void validateSettlementNotStarted(Long crewId) {
    if (settlementRepository.findByCrewId(crewId).isPresent()) {
      throw new CustomException(MissionErrorCode.SETTLEMENT_INPUT_FROZEN);
    }
  }

  // 방장 검토 대상이 아닌 크루/참여자 상태는 처리하지 않는다.
  private void validateOperationalState(MissionLog missionLog) {
    CrewParticipant participant = missionLog.getCrewParticipant();
    Crew crew = participant.getCrew();
    if (crew.getStatus() != CrewStatus.ACTIVE
        || participant.getStatus() != CrewParticipantStatus.LOCKED) {
      throw new CustomException(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);
    }
  }

  private void validateReviewPeriodNotExpired(MissionLog missionLog, LocalDateTime now) {
    Long crewId = missionLog.getCrewParticipant().getCrew().getId();
    MissionRule missionRule =
        missionRuleRepository
            .findByCrewId(crewId)
            .orElseThrow(() -> new CustomException(MissionErrorCode.MISSION_RULE_NOT_FOUND));
    LocalDateTime reviewableUntil =
        missionRule
            .getDailySettlementType()
            .autoCertificationAt(missionLog.getServerTime().toLocalDate())
            .plus(HOST_REVIEW_GRACE_DURATION);
    if (now.isAfter(reviewableUntil)) {
      throw new CustomException(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);
    }
  }

  // 방장은 검수 대기 또는 시스템 자동 판정 상태만 수동으로 확정할 수 있다.
  private void validateReviewable(MissionLog missionLog) {
    if (!missionLog.isHostReviewable()) {
      throw new CustomException(MissionErrorCode.MISSION_LOG_NOT_REVIEWABLE);
    }
  }
}
