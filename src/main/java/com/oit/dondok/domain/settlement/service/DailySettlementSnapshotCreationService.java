package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import com.oit.dondok.domain.mission.repository.MissionLogRepository;
import com.oit.dondok.domain.settlement.entity.DailySettlementParticipantSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.repository.DailySettlementParticipantSnapshotRepository;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationInput;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationResult;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantInput;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailySettlementSnapshotCreationService {

  private final CrewParticipantRepository crewParticipantRepository;
  private final MissionLogRepository missionLogRepository;
  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  private final DailySettlementParticipantSnapshotRepository
      dailySettlementParticipantSnapshotRepository;
  private final SettlementCalculatorService settlementCalculatorService;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long createSnapshot(
      MissionRule missionRule,
      LocalDate missionDate,
      DailySettlementPhase phase,
      String batchRunKey,
      LocalDateTime frozenAt) {
    Crew crew = missionRule.getCrew();
    Long existingSnapshotId =
        dailySettlementSnapshotRepository
            .findByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
                crew.getId(), missionDate, missionRule.getDailySettlementType(), phase)
            .map(DailySettlementSnapshot::getId)
            .orElse(null);

    if (existingSnapshotId != null) {
      return existingSnapshotId;
    }

    List<CrewParticipant> participants =
        crewParticipantRepository.findByCrewIdAndStatus(crew.getId(), CrewParticipantStatus.LOCKED);
    SettlementCalculationResult calculationResult =
        calculateDashboardProjection(missionRule, missionDate, phase, frozenAt, participants);

    DailySettlementSnapshot snapshot =
        createSucceededSnapshot(
            missionRule, missionDate, phase, batchRunKey, frozenAt, calculationResult);
    DailySettlementSnapshot savedSnapshot = dailySettlementSnapshotRepository.save(snapshot);

    Map<Long, SettlementParticipantResult> resultsByParticipantKey =
        calculationResult.participants().stream()
            .collect(
                Collectors.toMap(
                    SettlementParticipantResult::participantKey,
                    result -> result,
                    (first, second) -> {
                      throw new SettlementBatchRunFailure(
                          SettlementFailureCode.CALCULATION_FAILED,
                          "일일 정산 계산 결과에 중복 참여자 키가 있습니다. participantKey=" + first.participantKey());
                    },
                    LinkedHashMap::new));

    List<DailySettlementParticipantSnapshot> participantSnapshots =
        participants.stream()
            .map(
                participant -> {
                  SettlementParticipantResult result =
                      resultsByParticipantKey.get(participant.getId());
                  if (result == null) {
                    throw new SettlementBatchRunFailure(
                        SettlementFailureCode.CALCULATION_FAILED,
                        "일일 정산 계산 결과에서 참여자를 찾을 수 없습니다. participantId=" + participant.getId());
                  }
                  return DailySettlementParticipantSnapshot.create(
                      savedSnapshot,
                      participant,
                      result.recognizedSuccessCount(),
                      result.shareRatio(),
                      result.refundAmount());
                })
            .toList();
    dailySettlementParticipantSnapshotRepository.saveAll(participantSnapshots);
    return savedSnapshot.getId();
  }

  private DailySettlementSnapshot createSucceededSnapshot(
      MissionRule missionRule,
      LocalDate missionDate,
      DailySettlementPhase phase,
      String batchRunKey,
      LocalDateTime frozenAt,
      SettlementCalculationResult calculationResult) {
    return switch (phase) {
      case PROVISIONAL ->
          DailySettlementSnapshot.provisional(
              missionRule.getCrew(),
              missionDate,
              missionRule.getDailySettlementType(),
              missionRule.getFrequencyType(),
              batchRunKey,
              frozenAt,
              calculationResult.totalParticipants(),
              calculationResult.totalRecognizedSuccess(),
              calculationResult.totalLockedAmount());
      case FINALIZED ->
          DailySettlementSnapshot.finalized(
              missionRule.getCrew(),
              missionDate,
              missionRule.getDailySettlementType(),
              missionRule.getFrequencyType(),
              batchRunKey,
              frozenAt,
              calculationResult.totalParticipants(),
              calculationResult.totalRecognizedSuccess(),
              calculationResult.totalLockedAmount());
    };
  }

  private SettlementCalculationResult calculateDashboardProjection(
      MissionRule missionRule,
      LocalDate missionDate,
      DailySettlementPhase phase,
      LocalDateTime frozenAt,
      List<CrewParticipant> participants) {
    Crew crew = missionRule.getCrew();
    LocalDateTime endExclusive = missionDate.plusDays(1).atStartOfDay();
    Map<Long, List<MissionLog>> successLogsByParticipantId =
        findApprovedLogs(missionRule, phase, endExclusive, frozenAt).stream()
            .collect(
                Collectors.groupingBy(
                    missionLog -> missionLog.getCrewParticipant().getId(), Collectors.toList()));

    return settlementCalculatorService.calculate(
        new SettlementCalculationInput(
            RemainderPolicy.HOST_REMAINDER,
            participants.stream()
                .map(
                    participant ->
                        toParticipantInput(
                            crew,
                            participant,
                            successLogsByParticipantId.getOrDefault(
                                participant.getId(), List.of())))
                .toList()));
  }

  private List<MissionLog> findApprovedLogs(
      MissionRule missionRule,
      DailySettlementPhase phase,
      LocalDateTime endExclusive,
      LocalDateTime frozenAt) {
    Crew crew = missionRule.getCrew();
    return switch (phase) {
      case PROVISIONAL ->
          missionLogRepository
              .findProvisionalApprovedLogCandidatesForDailySettlementProjection(
                  crew.getId(), crew.getStartAt(), endExclusive)
              .stream()
              .filter(log -> isEligibleForProvisionalProjection(missionRule, log, frozenAt))
              .toList();
      case FINALIZED ->
          missionLogRepository.findFinalizedApprovedLogsForDailySettlementProjection(
              crew.getId(), crew.getStartAt(), endExclusive);
    };
  }

  private boolean isEligibleForProvisionalProjection(
      MissionRule missionRule, MissionLog missionLog, LocalDateTime frozenAt) {
    if (missionLog.getDecisionType() == ModerationDecisionType.MANUAL_APPROVE) {
      LocalDateTime moderatorDecidedAt = missionLog.getModeratorDecidedAt();
      return moderatorDecidedAt != null && !moderatorDecidedAt.isAfter(frozenAt);
    }
    if (missionLog.getDecisionType() != ModerationDecisionType.AUTO_APPROVE) {
      return false;
    }
    LocalDateTime hostReviewableUntil =
        missionRule
            .getDailySettlementType()
            .hostReviewableUntil(missionLog.getServerTime().toLocalDate());
    return frozenAt.isAfter(hostReviewableUntil);
  }

  private SettlementParticipantInput toParticipantInput(
      Crew crew, CrewParticipant participant, List<MissionLog> successLogs) {
    int successCountRaw = successLogs.size();
    int recognizedSuccessCount =
        (int) successLogs.stream().map(log -> log.getServerTime().toLocalDate()).distinct().count();
    boolean host = participant.getMember().getId().equals(crew.getHostMember().getId());
    return new SettlementParticipantInput(
        participant.getId(),
        host,
        participant.getDepositAmount(),
        successCountRaw,
        recognizedSuccessCount,
        recognizedSuccessCount,
        successCountRaw - recognizedSuccessCount);
  }
}
