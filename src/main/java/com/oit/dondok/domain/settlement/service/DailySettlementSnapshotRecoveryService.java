package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySettlementSnapshotRecoveryService {

  private final MissionRuleRepository missionRuleRepository;
  private final FinalSettlementMissionDateResolver missionDateResolver;
  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  private final DailySettlementSnapshotRecoveryClaimService
      dailySettlementSnapshotRecoveryClaimService;
  private final DailySettlementSnapshotCreationService dailySettlementSnapshotCreationService;
  private final DailySettlementSnapshotFailureRecordService
      dailySettlementSnapshotFailureRecordService;

  @Transactional
  public void recoverExhaustedFinalizedSnapshots(
      Collection<Long> crewIds,
      DailySettlementType dailySettlementType,
      String batchRunKey,
      LocalDateTime now) {
    Objects.requireNonNull(crewIds, "crewIds는 필수입니다.");
    Objects.requireNonNull(dailySettlementType, "일일 정산 타입은 필수입니다.");
    Objects.requireNonNull(batchRunKey, "배치 실행 키는 필수입니다.");
    Objects.requireNonNull(now, "배치 실행 시각은 필수입니다.");

    for (Long crewId : crewIds) {
      recoverOneCrew(crewId, dailySettlementType, batchRunKey, now);
    }
  }

  private void recoverOneCrew(
      Long crewId, DailySettlementType dailySettlementType, String batchRunKey, LocalDateTime now) {
    try {
      MissionRule missionRule = missionRuleRepository.findWithCrewByCrewId(crewId).orElse(null);
      if (missionRule == null) {
        log.warn("FINALIZED 일일 정산 스냅샷 recovery 대상 미션 규칙을 찾을 수 없습니다. crewId={}", crewId);
        return;
      }
      if (missionRule.getDailySettlementType() != dailySettlementType) {
        log.debug(
            "FINALIZED 일일 정산 스냅샷 recovery 타입이 일치하지 않아 건너뜁니다. crewId={}, requestedType={}, actualType={}",
            crewId,
            dailySettlementType,
            missionRule.getDailySettlementType());
        return;
      }

      Crew crew = missionRule.getCrew();
      List<LocalDate> missionDates = missionDateResolver.resolveMissionDates(crew, missionRule);
      if (missionDates.isEmpty()) {
        return;
      }

      List<DailySettlementSnapshot> recoveryTargets =
          dailySettlementSnapshotRepository
              .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndRetryCountGreaterThanEqualAndMissionDateIn(
                  crewId,
                  dailySettlementType,
                  DailySettlementPhase.FINALIZED,
                  DailySettlementStatus.FAILED,
                  DailySettlementSnapshot.MAX_RETRY_COUNT,
                  missionDates);
      for (DailySettlementSnapshot snapshot : recoveryTargets) {
        recoverOneSnapshot(missionRule, snapshot, batchRunKey, now);
      }
    } catch (RuntimeException exception) {
      log.warn(
          "FINALIZED 일일 정산 스냅샷 recovery 중 크루 단위 처리를 건너뜁니다. crewId={}, reason={}",
          crewId,
          failureMessageOf(exception),
          exception);
    }
  }

  private void recoverOneSnapshot(
      MissionRule missionRule,
      DailySettlementSnapshot snapshot,
      String batchRunKey,
      LocalDateTime now) {
    String recoveryBatchRunKey = batchRunKey + "-finalized-recovery-" + snapshot.getId();
    if (!dailySettlementSnapshotRecoveryClaimService.claimExhaustedFinalized(
        snapshot.getId(), recoveryBatchRunKey, now)) {
      return;
    }
    try {
      Long recoveredSnapshotId =
          dailySettlementSnapshotCreationService.retrySnapshot(
              missionRule,
              snapshot.getMissionDate(),
              DailySettlementPhase.FINALIZED,
              recoveryBatchRunKey,
              now);
      log.info(
          "retry exhausted FINALIZED 일일 정산 스냅샷을 recovery 했습니다. crewId={}, missionDate={}, snapshotId={}",
          missionRule.getCrew().getId(),
          snapshot.getMissionDate(),
          recoveredSnapshotId);
    } catch (RuntimeException exception) {
      tryRecordRecoveryFailure(snapshot, recoveryBatchRunKey, now, failureMessageOf(exception));
      log.warn(
          "retry exhausted FINALIZED 일일 정산 스냅샷 recovery에 실패했습니다. crewId={}, missionDate={}, snapshotId={}, reason={}",
          missionRule.getCrew().getId(),
          snapshot.getMissionDate(),
          snapshot.getId(),
          failureMessageOf(exception),
          exception);
    }
  }

  private void tryRecordRecoveryFailure(
      DailySettlementSnapshot snapshot,
      String batchRunKey,
      LocalDateTime now,
      String failureMessage) {
    try {
      dailySettlementSnapshotFailureRecordService.recordRetryFailure(
          snapshot.getId(), batchRunKey, now, failureMessage);
    } catch (RuntimeException recordingException) {
      log.warn(
          "FINALIZED 일일 정산 스냅샷 recovery 실패 기록 중 예외가 발생했습니다. snapshotId={}, reason={}",
          snapshot.getId(),
          failureMessageOf(recordingException),
          recordingException);
    }
  }

  private String failureMessageOf(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return exception.getClass().getSimpleName();
    }
    return message;
  }
}
