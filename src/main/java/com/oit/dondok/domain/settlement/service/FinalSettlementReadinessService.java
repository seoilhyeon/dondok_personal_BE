package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinalSettlementReadinessService {

  private static final long FINAL_GRACE_HOURS = 24L;

  private final FinalSettlementMissionDateResolver missionDateResolver;
  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository;

  @Transactional(readOnly = true)
  public boolean existsReadyFinalSettlementSnapshot(
      Crew crew, MissionRule missionRule, LocalDateTime now) {
    List<LocalDate> missionDates = missionDateResolver.resolveMissionDates(crew, missionRule);
    if (missionDates.isEmpty()) {
      return false;
    }

    Map<LocalDate, DailySettlementSnapshot> snapshotsByMissionDate =
        dailySettlementSnapshotRepository
            .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                crew.getId(),
                missionRule.getDailySettlementType(),
                DailySettlementPhase.FINALIZED,
                DailySettlementStatus.SUCCEEDED,
                missionDates)
            .stream()
            .collect(
                Collectors.toMap(
                    DailySettlementSnapshot::getMissionDate,
                    Function.identity(),
                    (first, second) -> {
                      throw new SettlementBatchRunFailure(
                          SettlementFailureCode.INPUT_LOAD_FAILED,
                          "동일한 미션일의 일일 정산 스냅샷이 중복 조회되었습니다. missionDate=" + first.getMissionDate());
                    }));

    if (!snapshotsByMissionDate.keySet().containsAll(missionDates)) {
      return false;
    }

    LocalDate lastMissionDate = missionDates.get(missionDates.size() - 1);
    DailySettlementSnapshot lastSnapshot = snapshotsByMissionDate.get(lastMissionDate);
    return !now.isBefore(lastSnapshot.getFrozenAt().plusHours(FINAL_GRACE_HOURS));
  }
}
