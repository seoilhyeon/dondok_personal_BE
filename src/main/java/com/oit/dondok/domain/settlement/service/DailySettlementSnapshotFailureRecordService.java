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
  public Long recordFinalizedFailure(
      MissionRule missionRule,
      LocalDate missionDate,
      String batchRunKey,
      LocalDateTime frozenAt,
      String failureMessage) {
    Crew crew = missionRule.getCrew();
    DailySettlementSnapshot existingSnapshot =
        dailySettlementSnapshotRepository
            .findByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
                crew.getId(),
                missionDate,
                missionRule.getDailySettlementType(),
                DailySettlementPhase.FINALIZED)
            .orElse(null);

    if (existingSnapshot != null) {
      if (existingSnapshot.getStatus() == DailySettlementStatus.SUCCEEDED) {
        return existingSnapshot.getId();
      }
      existingSnapshot.markFailed(batchRunKey, frozenAt, failureMessage);
      return existingSnapshot.getId();
    }

    DailySettlementSnapshot failedSnapshot =
        DailySettlementSnapshot.finalizedFailed(
            crew,
            missionDate,
            missionRule.getDailySettlementType(),
            missionRule.getFrequencyType(),
            batchRunKey,
            frozenAt,
            failureMessage);
    return dailySettlementSnapshotRepository.save(failedSnapshot).getId();
  }
}
