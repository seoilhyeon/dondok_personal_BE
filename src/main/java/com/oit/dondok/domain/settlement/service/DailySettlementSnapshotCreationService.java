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
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
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
  private final DailySettlementSnapshotFailureRecordService
      dailySettlementSnapshotFailureRecordService;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long createSnapshot(
      MissionRule missionRule,
      LocalDate missionDate,
      DailySettlementPhase phase,
      String batchRunKey,
      LocalDateTime frozenAt) {
    try {
      return createSnapshotInternal(missionRule, missionDate, phase, batchRunKey, frozenAt);
    } catch (RuntimeException exception) {
      recordFailure(missionRule, missionDate, phase, batchRunKey, frozenAt, exception);
      throw exception;
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long retrySnapshot(
      MissionRule missionRule,
      LocalDate missionDate,
      DailySettlementPhase phase,
      String batchRunKey,
      LocalDateTime frozenAt) {
    return createSnapshotInternal(missionRule, missionDate, phase, batchRunKey, frozenAt);
  }

  private void recordFailure(
      MissionRule missionRule,
      LocalDate missionDate,
      DailySettlementPhase phase,
      String batchRunKey,
      LocalDateTime frozenAt,
      RuntimeException originalException) {
    try {
      dailySettlementSnapshotFailureRecordService.recordFailure(
          missionRule,
          missionDate,
          phase,
          batchRunKey,
          frozenAt,
          failureMessageOf(originalException));
    } catch (RuntimeException recordingException) {
      originalException.addSuppressed(recordingException);
    }
  }

  private String failureMessageOf(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return exception.getClass().getSimpleName();
    }
    return message;
  }

  private Long createSnapshotInternal(
      MissionRule missionRule,
      LocalDate missionDate,
      DailySettlementPhase phase,
      String batchRunKey,
      LocalDateTime frozenAt) {
    Crew crew = missionRule.getCrew();
    DailySettlementSnapshot existingSnapshot =
        dailySettlementSnapshotRepository
            .findByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
                crew.getId(), missionDate, missionRule.getDailySettlementType(), phase)
            .orElse(null);

    if (existingSnapshot != null) {
      if (existingSnapshot.getStatus() != DailySettlementStatus.RETRYING) {
        return existingSnapshot.getId();
      }
      if (!existingSnapshot.getBatchRunKey().equals(batchRunKey)) {
        return existingSnapshot.getId();
      }
    }

    List<CrewParticipant> participants =
        crewParticipantRepository.findByCrewIdAndStatus(crew.getId(), CrewParticipantStatus.LOCKED);
    SettlementCalculationResult calculationResult =
        calculateDashboardProjection(missionRule, missionDate, phase, frozenAt, participants);

    if (existingSnapshot != null) {
      Long existingSnapshotId = existingSnapshot.getId();
      existingSnapshot =
          dailySettlementSnapshotRepository
              .findRetryOwnerForUpdate(
                  existingSnapshotId, DailySettlementStatus.RETRYING, batchRunKey)
              .orElse(null);
      if (existingSnapshot == null) {
        return existingSnapshotId;
      }
    }

    DailySettlementSnapshot snapshot =
        createOrUpdateSucceededSnapshot(
            existingSnapshot,
            missionRule,
            missionDate,
            phase,
            batchRunKey,
            frozenAt,
            calculationResult);
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

  private DailySettlementSnapshot createOrUpdateSucceededSnapshot(
      DailySettlementSnapshot existingSnapshot,
      MissionRule missionRule,
      LocalDate missionDate,
      DailySettlementPhase phase,
      String batchRunKey,
      LocalDateTime frozenAt,
      SettlementCalculationResult calculationResult) {
    if (existingSnapshot != null) {
      existingSnapshot.markSucceeded(
          batchRunKey,
          frozenAt,
          calculationResult.totalParticipants(),
          calculationResult.totalRecognizedSuccess(),
          calculationResult.totalLockedAmount());
      return existingSnapshot;
    }
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
    List<MissionLog> candidates =
        missionLogRepository.findApprovedLogCandidatesForDailySettlementProjection(
            crew.getId(), crew.getStartAt(), endExclusive);
    return switch (phase) {
      case PROVISIONAL ->
          candidates.stream()
              .filter(log -> isEligibleForProvisionalProjection(missionRule, log, frozenAt))
              .toList();
      case FINALIZED -> {
        // FINALIZED는 생성 시점이 확정 가능 시점 이후라는 전제에서 승인 후보 전체를 반영한다.
        yield candidates;
      }
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
    LocalDate missionDate = missionLog.getServerTime().toLocalDate();
    if (isLastThreeMissionDays(missionRule.getCrew(), missionDate)) {
      LocalDateTime autoCertificationAt =
          missionRule.getDailySettlementType().autoCertificationAt(missionDate);
      return !frozenAt.isBefore(autoCertificationAt);
    }
    LocalDateTime hostReviewableUntil =
        missionRule.getDailySettlementType().hostReviewableUntil(missionDate);
    return frozenAt.isAfter(hostReviewableUntil);
  }

  private boolean isLastThreeMissionDays(Crew crew, LocalDate missionDate) {
    LocalDate endDate = crew.getEndAt().toLocalDate();
    // 마지막 3일은 종료일을 포함한 endDate - 2일부터 endDate까지다.
    return !missionDate.isBefore(endDate.minusDays(2)) && !missionDate.isAfter(endDate);
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
