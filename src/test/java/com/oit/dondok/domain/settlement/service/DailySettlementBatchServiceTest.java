package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailySettlementBatchServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 12, 0);

  @Mock private MissionRuleRepository missionRuleRepository;
  @Mock private DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  @Mock private DailySettlementSnapshotCreationService dailySettlementSnapshotCreationService;

  @InjectMocks private DailySettlementBatchService service;

  @Test
  void resolveMissionDateByDailySettlementType() {
    assertThat(service.resolveMissionDate(DailySettlementType.A, NOW))
        .isEqualTo(LocalDate.of(2026, 6, 15));
    assertThat(service.resolveMissionDate(DailySettlementType.B, NOW))
        .isEqualTo(LocalDate.of(2026, 6, 14));
    assertThat(service.resolveMissionDate(DailySettlementType.C, NOW))
        .isEqualTo(LocalDate.of(2026, 6, 14));
  }

  @Test
  void runDailySettlementBatchSkipsExistingSnapshot() {
    MissionRule missionRule = missionRule(10L);
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE)))
        .willReturn(List.of(missionRule));
    given(
            dailySettlementSnapshotRepository
                .existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhaseAndStatus(
                    10L,
                    LocalDate.of(2026, 6, 15),
                    DailySettlementType.A,
                    DailySettlementPhase.PROVISIONAL,
                    DailySettlementStatus.SUCCEEDED))
        .willReturn(true);

    service.runDailySettlementBatch(DailySettlementType.A, NOW);

    then(dailySettlementSnapshotRepository)
        .should(never())
        .existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhaseAndStatusAndRetryCountGreaterThanEqual(
            10L,
            LocalDate.of(2026, 6, 15),
            DailySettlementType.A,
            DailySettlementPhase.PROVISIONAL,
            DailySettlementStatus.FAILED,
            DailySettlementSnapshot.MAX_RETRY_COUNT);
    then(dailySettlementSnapshotCreationService)
        .should(never())
        .createSnapshot(
            org.mockito.Mockito.any(),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.any());
  }

  @Test
  void runDailySettlementBatchRetriesFailedProvisionalSnapshotWhenRetryCountIsBelowMax() {
    MissionRule missionRule = missionRule(10L);
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE)))
        .willReturn(List.of(missionRule));
    given(
            dailySettlementSnapshotRepository
                .existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhaseAndStatus(
                    10L,
                    LocalDate.of(2026, 6, 15),
                    DailySettlementType.A,
                    DailySettlementPhase.PROVISIONAL,
                    DailySettlementStatus.SUCCEEDED))
        .willReturn(false);
    given(
            dailySettlementSnapshotRepository
                .existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhaseAndStatusAndRetryCountGreaterThanEqual(
                    10L,
                    LocalDate.of(2026, 6, 15),
                    DailySettlementType.A,
                    DailySettlementPhase.PROVISIONAL,
                    DailySettlementStatus.FAILED,
                    DailySettlementSnapshot.MAX_RETRY_COUNT))
        .willReturn(false);

    service.runDailySettlementBatch(DailySettlementType.A, NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .createSnapshot(
            org.mockito.Mockito.eq(missionRule),
            org.mockito.Mockito.eq(LocalDate.of(2026, 6, 15)),
            org.mockito.Mockito.eq(DailySettlementPhase.PROVISIONAL),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.eq(NOW));
  }

  @Test
  void runDailySettlementBatchSkipsProvisionalSnapshotWhenRetryCountReachedMax() {
    MissionRule missionRule = missionRule(10L);
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE)))
        .willReturn(List.of(missionRule));
    given(
            dailySettlementSnapshotRepository
                .existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhaseAndStatus(
                    10L,
                    LocalDate.of(2026, 6, 15),
                    DailySettlementType.A,
                    DailySettlementPhase.PROVISIONAL,
                    DailySettlementStatus.SUCCEEDED))
        .willReturn(false);
    given(
            dailySettlementSnapshotRepository
                .existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhaseAndStatusAndRetryCountGreaterThanEqual(
                    10L,
                    LocalDate.of(2026, 6, 15),
                    DailySettlementType.A,
                    DailySettlementPhase.PROVISIONAL,
                    DailySettlementStatus.FAILED,
                    DailySettlementSnapshot.MAX_RETRY_COUNT))
        .willReturn(true);

    service.runDailySettlementBatch(DailySettlementType.A, NOW);

    then(dailySettlementSnapshotCreationService)
        .should(never())
        .createSnapshot(
            org.mockito.Mockito.eq(missionRule),
            org.mockito.Mockito.eq(LocalDate.of(2026, 6, 15)),
            org.mockito.Mockito.eq(DailySettlementPhase.PROVISIONAL),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.any());
  }

  @Test
  void runDailySettlementBatchDelegatesMissionDateDayOfWeekToCandidateQuery() {
    MissionRule missionRule = missionRule(10L);
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE)))
        .willReturn(List.of(missionRule));

    service.runDailySettlementBatch(DailySettlementType.A, NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .createSnapshot(
            org.mockito.Mockito.eq(missionRule),
            org.mockito.Mockito.eq(LocalDate.of(2026, 6, 15)),
            org.mockito.Mockito.eq(DailySettlementPhase.PROVISIONAL),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.eq(NOW));
  }

  @Test
  void runDailySettlementBatchCreatesFinalizedSnapshotForReviewGraceExpiredMissionDate() {
    MissionRule missionRule = missionRule(10L);
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE)))
        .willReturn(List.of());
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 12).atStartOfDay(),
                LocalDate.of(2026, 6, 13).atStartOfDay(),
                5,
                List.of(CrewStatus.ACTIVE, CrewStatus.CLOSED)))
        .willReturn(List.of(missionRule));

    service.runDailySettlementBatch(DailySettlementType.A, NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .createSnapshot(
            org.mockito.Mockito.eq(missionRule),
            org.mockito.Mockito.eq(LocalDate.of(2026, 6, 12)),
            org.mockito.Mockito.eq(DailySettlementPhase.FINALIZED),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.eq(NOW));
  }

  @Test
  void runDailySettlementBatchRetriesFailedFinalizedSnapshot() {
    MissionRule missionRule = missionRule(10L);
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE)))
        .willReturn(List.of());
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 12).atStartOfDay(),
                LocalDate.of(2026, 6, 13).atStartOfDay(),
                5,
                List.of(CrewStatus.ACTIVE, CrewStatus.CLOSED)))
        .willReturn(List.of(missionRule));
    given(
            dailySettlementSnapshotRepository
                .existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhaseAndStatus(
                    10L,
                    LocalDate.of(2026, 6, 12),
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED))
        .willReturn(false);

    service.runDailySettlementBatch(DailySettlementType.A, NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .createSnapshot(
            org.mockito.Mockito.eq(missionRule),
            org.mockito.Mockito.eq(LocalDate.of(2026, 6, 12)),
            org.mockito.Mockito.eq(DailySettlementPhase.FINALIZED),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.eq(NOW));
  }

  @Test
  void runDailySettlementBatchSkipsFinalizedSnapshotWhenRetryCountReachedMax() {
    MissionRule missionRule = missionRule(10L);
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE)))
        .willReturn(List.of());
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 12).atStartOfDay(),
                LocalDate.of(2026, 6, 13).atStartOfDay(),
                5,
                List.of(CrewStatus.ACTIVE, CrewStatus.CLOSED)))
        .willReturn(List.of(missionRule));
    given(
            dailySettlementSnapshotRepository
                .existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhaseAndStatus(
                    10L,
                    LocalDate.of(2026, 6, 12),
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED))
        .willReturn(false);
    given(
            dailySettlementSnapshotRepository
                .existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhaseAndStatusAndRetryCountGreaterThanEqual(
                    10L,
                    LocalDate.of(2026, 6, 12),
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.FAILED,
                    DailySettlementSnapshot.MAX_RETRY_COUNT))
        .willReturn(true);

    service.runDailySettlementBatch(DailySettlementType.A, NOW);

    then(dailySettlementSnapshotCreationService)
        .should(never())
        .createSnapshot(
            org.mockito.Mockito.eq(missionRule),
            org.mockito.Mockito.eq(LocalDate.of(2026, 6, 12)),
            org.mockito.Mockito.eq(DailySettlementPhase.FINALIZED),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.any());
  }

  @Test
  void runDailySettlementBatchCreatesImmediateFinalizedSnapshotForLastThreeMissionDays() {
    MissionRule missionRule = missionRule(10L, LocalDateTime.of(2026, 6, 15, 23, 59, 59));
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE)))
        .willReturn(List.of());
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 12).atStartOfDay(),
                LocalDate.of(2026, 6, 13).atStartOfDay(),
                5,
                List.of(CrewStatus.ACTIVE, CrewStatus.CLOSED)))
        .willReturn(List.of());
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE, CrewStatus.CLOSED)))
        .willReturn(List.of(missionRule));

    service.runDailySettlementBatch(DailySettlementType.A, NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .createSnapshot(
            org.mockito.Mockito.eq(missionRule),
            org.mockito.Mockito.eq(LocalDate.of(2026, 6, 15)),
            org.mockito.Mockito.eq(DailySettlementPhase.FINALIZED),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.eq(NOW));
  }

  @Test
  void runDailySettlementBatchSkipsImmediateFinalizedSnapshotOutsideLastThreeMissionDays() {
    MissionRule missionRule = missionRule(10L, LocalDateTime.of(2026, 6, 20, 23, 59, 59));
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE)))
        .willReturn(List.of());
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 12).atStartOfDay(),
                LocalDate.of(2026, 6, 13).atStartOfDay(),
                5,
                List.of(CrewStatus.ACTIVE, CrewStatus.CLOSED)))
        .willReturn(List.of());
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE, CrewStatus.CLOSED)))
        .willReturn(List.of(missionRule));

    service.runDailySettlementBatch(DailySettlementType.A, NOW);

    then(dailySettlementSnapshotCreationService)
        .should(never())
        .createSnapshot(
            org.mockito.Mockito.eq(missionRule),
            org.mockito.Mockito.eq(LocalDate.of(2026, 6, 15)),
            org.mockito.Mockito.eq(DailySettlementPhase.FINALIZED),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.any());
  }

  @Test
  void runDailySettlementBatchDoesNotCreateDuplicateImmediateFinalizedSnapshot() {
    MissionRule missionRule = missionRule(10L, LocalDateTime.of(2026, 6, 15, 23, 59, 59));
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE)))
        .willReturn(List.of());
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 12).atStartOfDay(),
                LocalDate.of(2026, 6, 13).atStartOfDay(),
                5,
                List.of(CrewStatus.ACTIVE, CrewStatus.CLOSED)))
        .willReturn(List.of());
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE, CrewStatus.CLOSED)))
        .willReturn(List.of(missionRule));
    given(
            dailySettlementSnapshotRepository
                .existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhaseAndStatus(
                    10L,
                    LocalDate.of(2026, 6, 15),
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED))
        .willReturn(true);

    service.runDailySettlementBatch(DailySettlementType.A, NOW);

    then(dailySettlementSnapshotCreationService)
        .should(never())
        .createSnapshot(
            org.mockito.Mockito.eq(missionRule),
            org.mockito.Mockito.eq(LocalDate.of(2026, 6, 15)),
            org.mockito.Mockito.eq(DailySettlementPhase.FINALIZED),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.any());
  }

  @Test
  void runDailySettlementBatchAllowsFinalizedSnapshotBackfillForClosedCrew() {
    MissionRule missionRule = missionRule(10L);
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 15).atStartOfDay(),
                LocalDate.of(2026, 6, 16).atStartOfDay(),
                1,
                List.of(CrewStatus.ACTIVE)))
        .willReturn(List.of());
    given(
            missionRuleRepository.findRulesForDailySettlement(
                DailySettlementType.A,
                LocalDate.of(2026, 6, 12).atStartOfDay(),
                LocalDate.of(2026, 6, 13).atStartOfDay(),
                5,
                List.of(CrewStatus.ACTIVE, CrewStatus.CLOSED)))
        .willReturn(List.of(missionRule));

    service.runDailySettlementBatch(DailySettlementType.A, NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .createSnapshot(
            org.mockito.Mockito.eq(missionRule),
            org.mockito.Mockito.eq(LocalDate.of(2026, 6, 12)),
            org.mockito.Mockito.eq(DailySettlementPhase.FINALIZED),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.eq(NOW));
  }

  private MissionRule missionRule(Long crewId) {
    return missionRule(crewId, null);
  }

  private MissionRule missionRule(Long crewId, LocalDateTime crewEndAt) {
    MissionRule missionRule = org.mockito.Mockito.mock(MissionRule.class);
    Crew crew = org.mockito.Mockito.mock(Crew.class);
    given(missionRule.getCrew()).willReturn(crew);
    org.mockito.Mockito.lenient().when(crew.getId()).thenReturn(crewId);
    if (crewEndAt != null) {
      given(crew.getEndAt()).willReturn(crewEndAt);
    }
    return missionRule;
  }
}
