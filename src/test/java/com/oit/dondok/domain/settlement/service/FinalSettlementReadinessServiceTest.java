package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
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
class FinalSettlementReadinessServiceTest {

  private static final Long CREW_ID = 1L;
  private static final LocalDate FIRST_DATE = LocalDate.of(2026, 6, 10);
  private static final LocalDate LAST_DATE = LocalDate.of(2026, 6, 11);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 12, 12, 0);

  @Mock private FinalSettlementMissionDateResolver missionDateResolver;
  @Mock private DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  @Mock private Crew crew;
  @Mock private MissionRule missionRule;

  @InjectMocks private FinalSettlementReadinessService service;

  @Test
  void readyWhenAllFinalizedSnapshotsExistAndLastFrozenAtPassedTwentyFourHours() {
    givenBaseInputs();
    DailySettlementSnapshot firstSnapshot =
        snapshot(FIRST_DATE, LocalDateTime.of(2026, 6, 11, 12, 0));
    DailySettlementSnapshot lastSnapshot =
        snapshot(LAST_DATE, LocalDateTime.of(2026, 6, 11, 12, 0));
    givenSnapshots(firstSnapshot, lastSnapshot);

    assertThat(service.existsReadyFinalSettlementSnapshot(crew, missionRule, NOW)).isTrue();
  }

  @Test
  void notReadyWhenAnyExpectedMissionDateSnapshotIsMissing() {
    givenBaseInputs();
    givenSnapshots(snapshot(FIRST_DATE, LocalDateTime.of(2026, 6, 11, 12, 0)));

    assertThat(service.existsReadyFinalSettlementSnapshot(crew, missionRule, NOW)).isFalse();
  }

  @Test
  void notReadyBeforeLastSnapshotFrozenAtPlusTwentyFourHours() {
    givenBaseInputs();
    DailySettlementSnapshot firstSnapshot =
        snapshot(FIRST_DATE, LocalDateTime.of(2026, 6, 11, 12, 0));
    DailySettlementSnapshot lastSnapshot =
        snapshot(LAST_DATE, LocalDateTime.of(2026, 6, 11, 12, 1));
    givenSnapshots(firstSnapshot, lastSnapshot);

    assertThat(service.existsReadyFinalSettlementSnapshot(crew, missionRule, NOW)).isFalse();
  }

  @Test
  void notReadyWhenFinalizedSnapshotExistsOnlyAsFailedStatus() {
    givenBaseInputs();
    givenSnapshots();

    assertThat(service.existsReadyFinalSettlementSnapshot(crew, missionRule, NOW)).isFalse();
  }

  @Test
  void duplicateMissionDateSnapshotFailsFast() {
    givenBaseInputs();
    DailySettlementSnapshot duplicatedFirstSnapshot =
        snapshot(FIRST_DATE, LocalDateTime.of(2026, 6, 11, 12, 0));
    DailySettlementSnapshot duplicatedSecondSnapshot =
        snapshot(FIRST_DATE, LocalDateTime.of(2026, 6, 11, 12, 0));
    DailySettlementSnapshot lastSnapshot =
        snapshot(LAST_DATE, LocalDateTime.of(2026, 6, 11, 12, 0));
    givenSnapshots(duplicatedFirstSnapshot, duplicatedSecondSnapshot, lastSnapshot);

    assertThatThrownBy(() -> service.existsReadyFinalSettlementSnapshot(crew, missionRule, NOW))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .hasMessageContaining("missionDate=" + FIRST_DATE)
        .extracting(ex -> ((SettlementBatchRunFailure) ex).getFailureCode())
        .isEqualTo(SettlementFailureCode.INPUT_LOAD_FAILED);
  }

  private void givenBaseInputs() {
    given(crew.getId()).willReturn(CREW_ID);
    given(missionRule.getDailySettlementType()).willReturn(DailySettlementType.A);
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(FIRST_DATE, LAST_DATE));
  }

  private void givenSnapshots(DailySettlementSnapshot... snapshots) {
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    CREW_ID,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(FIRST_DATE, LAST_DATE)))
        .willReturn(List.of(snapshots));
  }

  private DailySettlementSnapshot snapshot(LocalDate missionDate, LocalDateTime frozenAt) {
    DailySettlementSnapshot snapshot = org.mockito.Mockito.mock(DailySettlementSnapshot.class);
    org.mockito.Mockito.lenient().when(snapshot.getMissionDate()).thenReturn(missionDate);
    org.mockito.Mockito.lenient().when(snapshot.getFrozenAt()).thenReturn(frozenAt);
    return snapshot;
  }
}
