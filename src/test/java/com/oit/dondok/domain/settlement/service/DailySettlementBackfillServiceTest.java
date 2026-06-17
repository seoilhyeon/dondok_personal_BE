package com.oit.dondok.domain.settlement.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailySettlementBackfillServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 0, 0);
  private static final String BATCH_RUN_KEY = "settlement-A-20260613000000";
  private static final String BACKFILL_BATCH_RUN_KEY = BATCH_RUN_KEY + "-finalized-backfill";

  @Mock private MissionRuleRepository missionRuleRepository;
  @Mock private FinalSettlementMissionDateResolver missionDateResolver;
  @Mock private DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  @Mock private DailySettlementSnapshotCreationService dailySettlementSnapshotCreationService;

  @InjectMocks private DailySettlementBackfillService dailySettlementBackfillService;

  @Test
  void backfillCreatesMissingFinalizedSnapshots() {
    Crew crew = crew(1L);
    MissionRule missionRule = missionRule(crew, DailySettlementType.A);
    LocalDate existingDate = LocalDate.of(2026, 6, 10);
    LocalDate missingDate = LocalDate.of(2026, 6, 11);
    DailySettlementSnapshot existingSnapshot = snapshot(existingDate);
    givenBaseCrewAndRule(crew, missionRule);
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(existingDate, missingDate));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    1L,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(existingDate, missingDate)))
        .willReturn(List.of(existingSnapshot));

    dailySettlementBackfillService.backfillMissingFinalizedSnapshots(
        List.of(1L), DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .createSnapshot(
            missionRule, missingDate, DailySettlementPhase.FINALIZED, BACKFILL_BATCH_RUN_KEY, NOW);
    then(dailySettlementSnapshotCreationService)
        .should(never())
        .createSnapshot(
            missionRule, existingDate, DailySettlementPhase.FINALIZED, BACKFILL_BATCH_RUN_KEY, NOW);
  }

  @Test
  void backfillSkipsExistingFinalizedSnapshots() {
    Crew crew = crew(1L);
    MissionRule missionRule = missionRule(crew, DailySettlementType.A);
    LocalDate firstDate = LocalDate.of(2026, 6, 10);
    LocalDate secondDate = LocalDate.of(2026, 6, 11);
    DailySettlementSnapshot firstSnapshot = snapshot(firstDate);
    DailySettlementSnapshot secondSnapshot = snapshot(secondDate);
    givenBaseCrewAndRule(crew, missionRule);
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(firstDate, secondDate));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    1L,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(firstDate, secondDate)))
        .willReturn(List.of(firstSnapshot, secondSnapshot));

    dailySettlementBackfillService.backfillMissingFinalizedSnapshots(
        List.of(1L), DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(dailySettlementSnapshotCreationService)
        .should(never())
        .createSnapshot(any(), any(), any(), any(), any());
  }

  @Test
  void backfillUsesScheduledMissionDatesOnly() {
    Crew crew = crew(1L);
    MissionRule missionRule = missionRule(crew, DailySettlementType.A);
    LocalDate scheduledDate = LocalDate.of(2026, 6, 12);
    givenBaseCrewAndRule(crew, missionRule);
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(scheduledDate));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    1L,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(scheduledDate)))
        .willReturn(List.of());

    dailySettlementBackfillService.backfillMissingFinalizedSnapshots(
        List.of(1L), DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .createSnapshot(
            missionRule,
            scheduledDate,
            DailySettlementPhase.FINALIZED,
            BACKFILL_BATCH_RUN_KEY,
            NOW);
  }

  @Test
  void backfillContinuesWhenOneSnapshotCreationFails() {
    Crew crew = crew(1L);
    MissionRule missionRule = missionRule(crew, DailySettlementType.A);
    LocalDate firstDate = LocalDate.of(2026, 6, 10);
    LocalDate secondDate = LocalDate.of(2026, 6, 11);
    givenBaseCrewAndRule(crew, missionRule);
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(firstDate, secondDate));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    1L,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(firstDate, secondDate)))
        .willReturn(List.of());
    given(
            dailySettlementSnapshotCreationService.createSnapshot(
                missionRule,
                firstDate,
                DailySettlementPhase.FINALIZED,
                BACKFILL_BATCH_RUN_KEY,
                NOW))
        .willThrow(new IllegalStateException("snapshot failed"));

    dailySettlementBackfillService.backfillMissingFinalizedSnapshots(
        List.of(1L), DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .createSnapshot(
            missionRule, firstDate, DailySettlementPhase.FINALIZED, BACKFILL_BATCH_RUN_KEY, NOW);
    then(dailySettlementSnapshotCreationService)
        .should()
        .createSnapshot(
            missionRule, secondDate, DailySettlementPhase.FINALIZED, BACKFILL_BATCH_RUN_KEY, NOW);
  }

  @Test
  void backfillRetriesFailedFinalizedSnapshotBecauseOnlySucceededSnapshotsAreSkipped() {
    Crew crew = crew(1L);
    MissionRule missionRule = missionRule(crew, DailySettlementType.A);
    LocalDate failedDate = LocalDate.of(2026, 6, 10);
    givenBaseCrewAndRule(crew, missionRule);
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(failedDate));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    1L,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(failedDate)))
        .willReturn(List.of());

    dailySettlementBackfillService.backfillMissingFinalizedSnapshots(
        List.of(1L), DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .createSnapshot(
            missionRule, failedDate, DailySettlementPhase.FINALIZED, BACKFILL_BATCH_RUN_KEY, NOW);
  }

  @Test
  void backfillSkipsFailedFinalizedSnapshotWhenRetryCountReachedMax() {
    Crew crew = crew(1L);
    MissionRule missionRule = missionRule(crew, DailySettlementType.A);
    LocalDate exhaustedDate = LocalDate.of(2026, 6, 10);
    DailySettlementSnapshot exhaustedSnapshot = snapshot(exhaustedDate);
    givenBaseCrewAndRule(crew, missionRule);
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(exhaustedDate));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    1L,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(exhaustedDate)))
        .willReturn(List.of());
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndRetryCountGreaterThanEqualAndMissionDateIn(
                    1L,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.FAILED,
                    DailySettlementSnapshot.MAX_RETRY_COUNT,
                    List.of(exhaustedDate)))
        .willReturn(List.of(exhaustedSnapshot));

    dailySettlementBackfillService.backfillMissingFinalizedSnapshots(
        List.of(1L), DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(dailySettlementSnapshotCreationService)
        .should(never())
        .createSnapshot(any(), any(), any(), any(), any());
  }

  @Test
  void backfillSkipsMissionRuleTypeMismatch() {
    Crew crew = crew(1L);
    MissionRule missionRule = missionRule(crew, DailySettlementType.B);
    givenBaseCrewAndRule(crew, missionRule);

    dailySettlementBackfillService.backfillMissingFinalizedSnapshots(
        List.of(1L), DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(missionDateResolver).should(never()).resolveMissionDates(any(), any());
    then(dailySettlementSnapshotCreationService)
        .should(never())
        .createSnapshot(any(), any(), any(), any(), any());
  }

  private void givenBaseCrewAndRule(Crew crew, MissionRule missionRule) {
    given(missionRuleRepository.findWithCrewByCrewId(1L)).willReturn(Optional.of(missionRule));
  }

  private Crew crew(Long id) {
    Crew crew = org.mockito.Mockito.mock(Crew.class);
    org.mockito.Mockito.lenient().when(crew.getId()).thenReturn(id);
    return crew;
  }

  private MissionRule missionRule(Crew crew, DailySettlementType dailySettlementType) {
    MissionRule missionRule = org.mockito.Mockito.mock(MissionRule.class);
    org.mockito.Mockito.lenient().when(missionRule.getCrew()).thenReturn(crew);
    given(missionRule.getDailySettlementType()).willReturn(dailySettlementType);
    return missionRule;
  }

  private DailySettlementSnapshot snapshot(LocalDate missionDate) {
    DailySettlementSnapshot snapshot = org.mockito.Mockito.mock(DailySettlementSnapshot.class);
    given(snapshot.getMissionDate()).willReturn(missionDate);
    return snapshot;
  }
}
