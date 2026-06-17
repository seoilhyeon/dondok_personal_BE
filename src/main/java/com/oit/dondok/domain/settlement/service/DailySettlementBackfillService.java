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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySettlementBackfillService {

  private final MissionRuleRepository missionRuleRepository;
  private final FinalSettlementMissionDateResolver missionDateResolver;
  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  private final DailySettlementSnapshotCreationService dailySettlementSnapshotCreationService;

  @Transactional
  public void backfillMissingFinalizedSnapshots(
      Collection<Long> crewIds,
      DailySettlementType dailySettlementType,
      String batchRunKey,
      LocalDateTime now) {
    Objects.requireNonNull(crewIds, "crewIds는 필수입니다.");
    Objects.requireNonNull(dailySettlementType, "일일 정산 타입은 필수입니다.");
    Objects.requireNonNull(batchRunKey, "배치 실행 키는 필수입니다.");
    Objects.requireNonNull(now, "배치 실행 시각은 필수입니다.");

    for (Long crewId : crewIds) {
      backfillOneCrew(crewId, dailySettlementType, batchRunKey, now);
    }
  }

  private void backfillOneCrew(
      Long crewId, DailySettlementType dailySettlementType, String batchRunKey, LocalDateTime now) {
    try {
      MissionRule missionRule = missionRuleRepository.findWithCrewByCrewId(crewId).orElse(null);
      if (missionRule == null) {
        log.warn("FINALIZED 일일 정산 스냅샷 backfill 대상 미션 규칙을 찾을 수 없습니다. crewId={}", crewId);
        return;
      }
      if (missionRule.getDailySettlementType() != dailySettlementType) {
        log.debug(
            "FINALIZED 일일 정산 스냅샷 backfill 타입이 일치하지 않아 건너뜁니다. crewId={}, requestedType={}, actualType={}",
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

      Set<LocalDate> existingSucceededMissionDates =
          findExistingSucceededMissionDates(crewId, dailySettlementType, missionDates);
      for (LocalDate missionDate : missionDates) {
        if (!existingSucceededMissionDates.contains(missionDate)) {
          createMissingFinalizedSnapshot(missionRule, crewId, missionDate, batchRunKey, now);
        }
      }
    } catch (RuntimeException exception) {
      log.warn(
          "FINALIZED 일일 정산 스냅샷 backfill 중 크루 단위 처리를 건너뜁니다. crewId={}, reason={}",
          crewId,
          exception.getMessage(),
          exception);
    }
  }

  private Set<LocalDate> findExistingSucceededMissionDates(
      Long crewId, DailySettlementType dailySettlementType, List<LocalDate> missionDates) {
    List<DailySettlementSnapshot> existingSnapshots =
        dailySettlementSnapshotRepository
            .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                crewId,
                dailySettlementType,
                DailySettlementPhase.FINALIZED,
                DailySettlementStatus.SUCCEEDED,
                missionDates);
    Set<LocalDate> existingMissionDates = new HashSet<>();
    for (DailySettlementSnapshot snapshot : existingSnapshots) {
      existingMissionDates.add(snapshot.getMissionDate());
    }
    return existingMissionDates;
  }

  private void createMissingFinalizedSnapshot(
      MissionRule missionRule,
      Long crewId,
      LocalDate missionDate,
      String batchRunKey,
      LocalDateTime now) {
    try {
      Long snapshotId =
          dailySettlementSnapshotCreationService.createSnapshot(
              missionRule,
              missionDate,
              DailySettlementPhase.FINALIZED,
              batchRunKey + "-finalized-backfill",
              now);
      log.info(
          "누락 FINALIZED 일일 정산 스냅샷을 확보했습니다. crewId={}, missionDate={}, snapshotId={}",
          crewId,
          missionDate,
          snapshotId);
    } catch (RuntimeException exception) {
      log.warn(
          "누락 FINALIZED 일일 정산 스냅샷 생성을 건너뜁니다. crewId={}, missionDate={}, reason={}",
          crewId,
          missionDate,
          exception.getMessage(),
          exception);
    }
  }
}
