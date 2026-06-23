package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySettlementBatchService {

  private static final ZoneId BATCH_ZONE = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter RUN_KEY_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final MissionRuleRepository missionRuleRepository;
  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  private final DailySettlementSnapshotCreationService dailySettlementSnapshotCreationService;
  private final SettlementNotificationService settlementNotificationService;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void runDailySettlementBatch(DailySettlementType dailySettlementType) {
    LocalDateTime now = LocalDateTime.now(BATCH_ZONE);
    runDailySettlementBatch(dailySettlementType, now);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void runDailySettlementBatch(DailySettlementType dailySettlementType, LocalDateTime now) {
    Map<Long, String> affectedCrews = new LinkedHashMap<>();

    LocalDate provisionalMissionDate = resolveMissionDate(dailySettlementType, now);
    createSnapshotsForPhase(
        dailySettlementType,
        provisionalMissionDate,
        DailySettlementPhase.PROVISIONAL,
        now,
        affectedCrews);

    LocalDate finalizedMissionDate = resolveFinalizableMissionDate(dailySettlementType, now);
    createSnapshotsForPhase(
        dailySettlementType,
        finalizedMissionDate,
        DailySettlementPhase.FINALIZED,
        now,
        affectedCrews);

    LocalDate immediateFinalizedMissionDate = resolveMissionDate(dailySettlementType, now);
    createSnapshotsForPhase(
        dailySettlementType,
        immediateFinalizedMissionDate,
        DailySettlementPhase.FINALIZED,
        now,
        missionRule ->
            isLastThreeMissionDays(missionRule.getCrew().getEndAt(), immediateFinalizedMissionDate),
        affectedCrews);

    affectedCrews.forEach(
        (crewId, crewTitle) -> {
          try {
            settlementNotificationService.sendExpectedRefundChangedNotifications(crewId, crewTitle);
          } catch (RuntimeException e) {
            log.warn("[배치] 예상 환급금 변동 알림 실패 crewId={}", crewId, e);
          }
        });
  }

  LocalDate resolveMissionDate(DailySettlementType dailySettlementType, LocalDateTime now) {
    return switch (dailySettlementType) {
      case A -> now.toLocalDate();
      case B, C -> now.toLocalDate().minusDays(1);
    };
  }

  LocalDate resolveFinalizableMissionDate(
      DailySettlementType dailySettlementType, LocalDateTime now) {
    return resolveMissionDate(dailySettlementType, now).minusDays(3);
  }

  private List<CrewStatus> candidateCrewStatuses(DailySettlementPhase phase) {
    if (phase == DailySettlementPhase.FINALIZED) {
      return List.of(CrewStatus.ACTIVE, CrewStatus.CLOSED);
    }
    return List.of(CrewStatus.ACTIVE);
  }

  private void createSnapshotsForPhase(
      DailySettlementType dailySettlementType,
      LocalDate missionDate,
      DailySettlementPhase phase,
      LocalDateTime now,
      Map<Long, String> affectedCrews) {
    createSnapshotsForPhase(
        dailySettlementType, missionDate, phase, now, missionRule -> true, affectedCrews);
  }

  private void createSnapshotsForPhase(
      DailySettlementType dailySettlementType,
      LocalDate missionDate,
      DailySettlementPhase phase,
      LocalDateTime now,
      Predicate<MissionRule> missionRuleFilter,
      Map<Long, String> affectedCrews) {
    String batchRunKey =
        "daily-settlement-"
            + dailySettlementType.name()
            + "-"
            + phase.name()
            + "-"
            + RUN_KEY_FORMATTER.format(now);
    LocalDateTime missionDateStart = missionDate.atStartOfDay();
    LocalDateTime missionDateEndExclusive = missionDate.plusDays(1).atStartOfDay();

    missionRuleRepository
        .findRulesForDailySettlement(
            dailySettlementType,
            missionDateStart,
            missionDateEndExclusive,
            missionDate.getDayOfWeek().getValue(),
            candidateCrewStatuses(phase))
        .stream()
        .filter(missionRuleFilter)
        .filter(
            missionRule ->
                !hasExistingSnapshot(
                    missionRule.getCrew().getId(), missionDate, dailySettlementType, phase))
        .forEach(
            missionRule -> {
              try {
                dailySettlementSnapshotCreationService.createSnapshot(
                    missionRule, missionDate, phase, batchRunKey, now);
                affectedCrews.putIfAbsent(
                    missionRule.getCrew().getId(), missionRule.getCrew().getTitle());
              } catch (RuntimeException exception) {
                log.error(
                    "[배치] 일일 정산 스냅샷 생성 중 예외 발생. crewId={}, missionDate={}, type={}, phase={}",
                    missionRule.getCrew().getId(),
                    missionDate,
                    dailySettlementType,
                    phase,
                    exception);
              }
            });
  }

  private boolean hasExistingSnapshot(
      Long crewId,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      DailySettlementPhase phase) {
    return dailySettlementSnapshotRepository
        .existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
            crewId, missionDate, dailySettlementType, phase);
  }

  private boolean isLastThreeMissionDays(LocalDateTime crewEndAt, LocalDate missionDate) {
    LocalDate endDate = crewEndAt.toLocalDate();
    return !missionDate.isBefore(endDate.minusDays(2)) && !missionDate.isAfter(endDate);
  }
}
