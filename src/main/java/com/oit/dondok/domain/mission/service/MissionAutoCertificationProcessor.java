package com.oit.dondok.domain.mission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.entity.ModerationHistory;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.repository.MissionLogQueryRepository;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.mission.repository.ModerationHistoryRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MissionAutoCertificationProcessor {

  private final MissionLogQueryRepository missionLogQueryRepository;
  private final MissionRuleRepository missionRuleRepository;
  private final SettlementRepository settlementRepository;
  private final ModerationHistoryRepository moderationHistoryRepository;
  private final MemberRepository memberRepository;
  private final SystemMemberProvider systemMemberProvider;
  private final AutoCertificationDecider autoCertificationDecider;
  private final ObjectMapper objectMapper;

  // 단일 인증 로그를 잠금 조회한 뒤 자동 승인/반려하고 이력을 남긴다.
  @Transactional
  public void confirmOne(Long missionLogId, LocalDateTime now) {
    MissionLog missionLog =
        missionLogQueryRepository.findByIdWithCrewForAutoCertification(missionLogId).orElse(null);
    if (missionLog == null) {
      return;
    }

    CrewParticipant participant = missionLog.getCrewParticipant();
    Crew crew = participant.getCrew();
    Long crewId = crew.getId();

    // 수동 검수/정산과 동시에 실행될 수 있으므로 처리 직전에 상태를 다시 확인한다.
    if (!missionLog.isPendingReview()
        || crew.getStatus() != CrewStatus.ACTIVE
        || participant.getStatus() != CrewParticipantStatus.LOCKED
        || settlementRepository.findByCrewId(crewId).isPresent()) {
      return;
    }

    MissionRule missionRule =
        missionRuleRepository
            .findByCrewId(crewId)
            .orElseThrow(() -> new CustomException(MissionErrorCode.MISSION_RULE_NOT_FOUND));

    if (!isDue(missionLog, missionRule.getDailySettlementType(), now)) {
      return;
    }

    Member systemModerator =
        memberRepository.getReferenceById(systemMemberProvider.getSystemMemberId());
    String beforeState = snapshotOf(missionLog);
    if (autoCertificationDecider.isApproved(missionLog)) {
      missionLog.approveAutomatically(systemModerator, now);
    } else {
      missionLog.rejectAutomatically(systemModerator, now);
    }

    String afterState = snapshotOf(missionLog);
    moderationHistoryRepository.save(
        createHistory(missionLog, beforeState, afterState, systemModerator, now));
  }

  private boolean isDue(
      MissionLog missionLog, DailySettlementType dailySettlementType, LocalDateTime now) {
    LocalDate missionDate = missionLog.getServerTime().toLocalDate();
    LocalDateTime autoCertificationAt = dailySettlementType.autoCertificationAt(missionDate);
    return !now.isBefore(autoCertificationAt);
  }

  private ModerationHistory createHistory(
      MissionLog missionLog,
      String beforeState,
      String afterState,
      Member systemModerator,
      LocalDateTime changedAt) {
    return switch (missionLog.getDecisionType()) {
      case AUTO_APPROVE ->
          ModerationHistory.createAutoApprove(
              missionLog, beforeState, afterState, systemModerator, changedAt);
      case AUTO_REJECT ->
          ModerationHistory.createAutoReject(
              missionLog, beforeState, afterState, systemModerator, changedAt);
      default -> throw new CustomException(MissionErrorCode.UNEXPECTED_MODERATION_DECISION_TYPE);
    };
  }

  // 검수 전후 상태를 moderation_history에 저장하기 위한 JSON 문자열로 만든다.
  private String snapshotOf(MissionLog missionLog) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("certification_status", missionLog.getCertificationStatus());
    snapshot.put("exif_risk", missionLog.getExifRisk());
    snapshot.put("duplicate_hash", missionLog.isDuplicateHash());
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
}
