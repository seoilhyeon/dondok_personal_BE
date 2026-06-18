package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailySettlementSnapshotFailureRecordService {

  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long recordFailure(
      MissionRule missionRule,
      LocalDate missionDate,
      DailySettlementPhase phase,
      String batchRunKey,
      LocalDateTime frozenAt,
      String failureMessage) {
    Crew crew = missionRule.getCrew();
    DailySettlementSnapshot existingSnapshot =
        dailySettlementSnapshotRepository
            .findByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
                crew.getId(), missionDate, missionRule.getDailySettlementType(), phase)
            .orElse(null);

    if (existingSnapshot != null) {
      if (existingSnapshot.getStatus() == DailySettlementStatus.SUCCEEDED) {
        return existingSnapshot.getId();
      }
      if (existingSnapshot.getStatus() == DailySettlementStatus.RETRYING
          && !batchRunKey.equals(existingSnapshot.getBatchRunKey())) {
        return existingSnapshot.getId();
      }
      existingSnapshot.markFailed(batchRunKey, frozenAt, failureMessage);
      return existingSnapshot.getId();
    }

    DailySettlementSnapshot failedSnapshot =
        failedSnapshot(missionRule, missionDate, phase, batchRunKey, frozenAt, failureMessage);
    return dailySettlementSnapshotRepository.save(failedSnapshot).getId();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordRetryFailure(
      Long snapshotId, String batchRunKey, LocalDateTime frozenAt, String failureMessage) {
    dailySettlementSnapshotRepository
        .findRetryOwnerForUpdate(snapshotId, DailySettlementStatus.RETRYING, batchRunKey)
        .ifPresent(snapshot -> snapshot.markFailed(batchRunKey, frozenAt, failureMessage));
  }

  private DailySettlementSnapshot failedSnapshot(
      MissionRule missionRule,
      LocalDate missionDate,
      DailySettlementPhase phase,
      String batchRunKey,
      LocalDateTime frozenAt,
      String failureMessage) {
    Crew crew = missionRule.getCrew();
    return switch (phase) {
      case PROVISIONAL ->
          DailySettlementSnapshot.provisionalFailed(
              crew,
              missionDate,
              missionRule.getDailySettlementType(),
              missionRule.getFrequencyType(),
              batchRunKey,
              frozenAt,
              failureMessage);
      case FINALIZED ->
          DailySettlementSnapshot.finalizedFailed(
              crew,
              missionDate,
              missionRule.getDailySettlementType(),
              missionRule.getFrequencyType(),
              batchRunKey,
              frozenAt,
              failureMessage);
    };
  }
}
