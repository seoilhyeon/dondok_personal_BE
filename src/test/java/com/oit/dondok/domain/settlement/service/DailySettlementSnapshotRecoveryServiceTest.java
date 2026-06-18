package com.oit.dondok.domain.settlement.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
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
class DailySettlementSnapshotRecoveryServiceTest {

  private static final Long CREW_ID = 1L;
  private static final Long SNAPSHOT_ID = 10L;
  private static final LocalDate MISSION_DATE = LocalDate.of(2026, 6, 10);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 0, 0);
  private static final String BATCH_RUN_KEY = "settlement-A-20260613000000";
  private static final String RECOVERY_BATCH_RUN_KEY =
      BATCH_RUN_KEY + "-finalized-recovery-" + SNAPSHOT_ID;

  @Mock private MissionRuleRepository missionRuleRepository;
  @Mock private FinalSettlementMissionDateResolver missionDateResolver;
  @Mock private DailySettlementSnapshotRepository dailySettlementSnapshotRepository;

  @Mock
  private DailySettlementSnapshotRecoveryClaimService dailySettlementSnapshotRecoveryClaimService;

  @Mock private DailySettlementSnapshotCreationService dailySettlementSnapshotCreationService;

  @Mock
  private DailySettlementSnapshotFailureRecordService dailySettlementSnapshotFailureRecordService;

  @InjectMocks
  private DailySettlementSnapshotRecoveryService dailySettlementSnapshotRecoveryService;

  @Test
  void recoverExhaustedFinalizedSnapshotClaimsAndRetriesSnapshot() {
    Crew crew = crew(CREW_ID);
    MissionRule missionRule = missionRule(crew, DailySettlementType.A);
    DailySettlementSnapshot snapshot = snapshot(SNAPSHOT_ID, MISSION_DATE);
    givenBaseRuleAndDates(crew, missionRule);
    givenRecoveryTargets(snapshot);
    given(
            dailySettlementSnapshotRecoveryClaimService.claimExhaustedFinalized(
                SNAPSHOT_ID, RECOVERY_BATCH_RUN_KEY, NOW))
        .willReturn(true);
    given(
            dailySettlementSnapshotCreationService.retrySnapshot(
                missionRule,
                MISSION_DATE,
                DailySettlementPhase.FINALIZED,
                RECOVERY_BATCH_RUN_KEY,
                NOW))
        .willReturn(SNAPSHOT_ID);

    dailySettlementSnapshotRecoveryService.recoverExhaustedFinalizedSnapshots(
        List.of(CREW_ID), DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .retrySnapshot(
            missionRule, MISSION_DATE, DailySettlementPhase.FINALIZED, RECOVERY_BATCH_RUN_KEY, NOW);
    then(dailySettlementSnapshotFailureRecordService)
        .should(never())
        .recordRetryFailure(any(), any(), any(), any());
  }

  @Test
  void recoverExhaustedFinalizedSnapshotRecordsFailureWhenRetryFails() {
    Crew crew = crew(CREW_ID);
    MissionRule missionRule = missionRule(crew, DailySettlementType.A);
    DailySettlementSnapshot snapshot = snapshot(SNAPSHOT_ID, MISSION_DATE);
    givenBaseRuleAndDates(crew, missionRule);
    givenRecoveryTargets(snapshot);
    given(
            dailySettlementSnapshotRecoveryClaimService.claimExhaustedFinalized(
                SNAPSHOT_ID, RECOVERY_BATCH_RUN_KEY, NOW))
        .willReturn(true);
    given(
            dailySettlementSnapshotCreationService.retrySnapshot(
                missionRule,
                MISSION_DATE,
                DailySettlementPhase.FINALIZED,
                RECOVERY_BATCH_RUN_KEY,
                NOW))
        .willThrow(new CustomException(GlobalErrorCode.SERVER_ERROR));

    dailySettlementSnapshotRecoveryService.recoverExhaustedFinalizedSnapshots(
        List.of(CREW_ID), DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(dailySettlementSnapshotFailureRecordService)
        .should()
        .recordRetryFailure(eq(SNAPSHOT_ID), eq(RECOVERY_BATCH_RUN_KEY), eq(NOW), anyString());
  }

  @Test
  void recoverExhaustedFinalizedSnapshotSkipsWhenClaimLoses() {
    Crew crew = crew(CREW_ID);
    MissionRule missionRule = missionRule(crew, DailySettlementType.A);
    DailySettlementSnapshot snapshot = snapshot(SNAPSHOT_ID, MISSION_DATE);
    givenBaseRuleAndDates(crew, missionRule);
    givenRecoveryTargets(snapshot);
    given(
            dailySettlementSnapshotRecoveryClaimService.claimExhaustedFinalized(
                SNAPSHOT_ID, RECOVERY_BATCH_RUN_KEY, NOW))
        .willReturn(false);

    dailySettlementSnapshotRecoveryService.recoverExhaustedFinalizedSnapshots(
        List.of(CREW_ID), DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(dailySettlementSnapshotCreationService)
        .should(never())
        .retrySnapshot(any(), any(), any(), any(), any());
  }

  @Test
  void recoverExhaustedFinalizedSnapshotSkipsMissionRuleTypeMismatch() {
    Crew crew = crew(CREW_ID);
    MissionRule missionRule = missionRule(crew, DailySettlementType.B);
    given(missionRuleRepository.findWithCrewByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));

    dailySettlementSnapshotRecoveryService.recoverExhaustedFinalizedSnapshots(
        List.of(CREW_ID), DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(missionDateResolver).should(never()).resolveMissionDates(any(), any());
    then(dailySettlementSnapshotRepository)
        .should(never())
        .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndRetryCountGreaterThanEqualAndMissionDateIn(
            any(), any(), any(), any(), any(Integer.class), any());
  }

  @Test
  void recoverExhaustedFinalizedSnapshotQueriesOnlyFinalizedExhaustedSnapshots() {
    Crew crew = crew(CREW_ID);
    MissionRule missionRule = missionRule(crew, DailySettlementType.A);
    givenBaseRuleAndDates(crew, missionRule);
    givenRecoveryTargets();

    dailySettlementSnapshotRecoveryService.recoverExhaustedFinalizedSnapshots(
        List.of(CREW_ID), DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(dailySettlementSnapshotRepository)
        .should()
        .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndRetryCountGreaterThanEqualAndMissionDateIn(
            CREW_ID,
            DailySettlementType.A,
            DailySettlementPhase.FINALIZED,
            DailySettlementStatus.FAILED,
            DailySettlementSnapshot.MAX_RETRY_COUNT,
            List.of(MISSION_DATE));
  }

  private void givenBaseRuleAndDates(Crew crew, MissionRule missionRule) {
    given(missionRuleRepository.findWithCrewByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(MISSION_DATE));
  }

  private void givenRecoveryTargets(DailySettlementSnapshot... snapshots) {
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndRetryCountGreaterThanEqualAndMissionDateIn(
                    CREW_ID,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.FAILED,
                    DailySettlementSnapshot.MAX_RETRY_COUNT,
                    List.of(MISSION_DATE)))
        .willReturn(List.of(snapshots));
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

  private DailySettlementSnapshot snapshot(Long id, LocalDate missionDate) {
    DailySettlementSnapshot snapshot = org.mockito.Mockito.mock(DailySettlementSnapshot.class);
    given(snapshot.getId()).willReturn(id);
    org.mockito.Mockito.lenient().when(snapshot.getMissionDate()).thenReturn(missionDate);
    return snapshot;
  }
}
