package com.oit.dondok.domain.settlement.service;

import static org.mockito.ArgumentMatchers.any;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DailySettlementSnapshotRetryServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 17, 12, 15);

  @Mock private DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  @Mock private DailySettlementSnapshotRetryClaimService dailySettlementSnapshotRetryClaimService;
  @Mock private MissionRuleRepository missionRuleRepository;
  @Mock private DailySettlementSnapshotCreationService dailySettlementSnapshotCreationService;

  @Mock
  private DailySettlementSnapshotFailureRecordService dailySettlementSnapshotFailureRecordService;

  @InjectMocks private DailySettlementSnapshotRetryService service;

  @Test
  void runRetrySnapshotBatchRetriesFailedProvisionalSnapshot() {
    DailySettlementSnapshot snapshot =
        snapshot(1L, 10L, DailySettlementType.A, DailySettlementPhase.PROVISIONAL);
    MissionRule missionRule = missionRule(DailySettlementType.A);
    givenRetryTargets(snapshot);
    givenClaimed(snapshot);
    given(missionRuleRepository.findWithCrewByCrewId(10L)).willReturn(Optional.of(missionRule));

    service.runRetrySnapshotBatch(NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .retrySnapshot(
            missionRule,
            LocalDate.of(2026, 6, 15),
            DailySettlementPhase.PROVISIONAL,
            "daily-settlement-snapshot-retry-1-20260617121500",
            NOW);
  }

  @Test
  void runRetrySnapshotBatchRetriesFailedFinalizedSnapshot() {
    DailySettlementSnapshot snapshot =
        snapshot(1L, 10L, DailySettlementType.B, DailySettlementPhase.FINALIZED);
    MissionRule missionRule = missionRule(DailySettlementType.B);
    givenRetryTargets(snapshot);
    givenClaimed(snapshot);
    given(missionRuleRepository.findWithCrewByCrewId(10L)).willReturn(Optional.of(missionRule));

    service.runRetrySnapshotBatch(NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .retrySnapshot(
            missionRule,
            LocalDate.of(2026, 6, 15),
            DailySettlementPhase.FINALIZED,
            "daily-settlement-snapshot-retry-1-20260617121500",
            NOW);
  }

  @Test
  void runRetrySnapshotBatchContinuesWhenOneSnapshotFails() {
    DailySettlementSnapshot first =
        snapshot(1L, 10L, DailySettlementType.A, DailySettlementPhase.PROVISIONAL);
    DailySettlementSnapshot second =
        snapshot(2L, 20L, DailySettlementType.A, DailySettlementPhase.PROVISIONAL);
    MissionRule firstRule = missionRule(DailySettlementType.A);
    MissionRule secondRule = missionRule(DailySettlementType.A);
    givenRetryTargets(first, second);
    givenClaimed(first);
    givenClaimed(second);
    given(missionRuleRepository.findWithCrewByCrewId(10L)).willReturn(Optional.of(firstRule));
    given(missionRuleRepository.findWithCrewByCrewId(20L)).willReturn(Optional.of(secondRule));
    given(
            dailySettlementSnapshotCreationService.retrySnapshot(
                firstRule,
                LocalDate.of(2026, 6, 15),
                DailySettlementPhase.PROVISIONAL,
                "daily-settlement-snapshot-retry-1-20260617121500",
                NOW))
        .willThrow(new IllegalStateException("retry failed"));

    service.runRetrySnapshotBatch(NOW);

    then(dailySettlementSnapshotCreationService)
        .should()
        .retrySnapshot(
            secondRule,
            LocalDate.of(2026, 6, 15),
            DailySettlementPhase.PROVISIONAL,
            "daily-settlement-snapshot-retry-2-20260617121500",
            NOW);
  }

  @Test
  void runRetrySnapshotBatchContinuesWhenClaimThrows() {
    DailySettlementSnapshot first =
        snapshot(1L, 10L, DailySettlementType.A, DailySettlementPhase.PROVISIONAL);
    DailySettlementSnapshot second =
        snapshot(2L, 20L, DailySettlementType.A, DailySettlementPhase.PROVISIONAL);
    MissionRule secondRule = missionRule(DailySettlementType.A);
    givenRetryTargets(first, second);
    given(
            dailySettlementSnapshotRetryClaimService.claim(
                1L, "daily-settlement-snapshot-retry-1-20260617121500", NOW, NOW.minusHours(1)))
        .willThrow(new IllegalStateException("claim failed"));
    givenClaimed(second);
    given(missionRuleRepository.findWithCrewByCrewId(20L)).willReturn(Optional.of(secondRule));

    service.runRetrySnapshotBatch(NOW);

    then(dailySettlementSnapshotRepository).should(never()).findWithCrewById(1L);
    then(dailySettlementSnapshotCreationService)
        .should()
        .retrySnapshot(
            secondRule,
            LocalDate.of(2026, 6, 15),
            DailySettlementPhase.PROVISIONAL,
            "daily-settlement-snapshot-retry-2-20260617121500",
            NOW);
  }

  @Test
  void runRetrySnapshotBatchSkipsWhenClaimLoses() {
    DailySettlementSnapshot snapshot =
        snapshot(1L, 10L, DailySettlementType.A, DailySettlementPhase.PROVISIONAL);
    givenRetryTargets(snapshot);
    given(
            dailySettlementSnapshotRetryClaimService.claim(
                1L, "daily-settlement-snapshot-retry-1-20260617121500", NOW, NOW.minusHours(1)))
        .willReturn(false);

    service.runRetrySnapshotBatch(NOW);

    then(dailySettlementSnapshotRepository).should(never()).findWithCrewById(1L);
    then(dailySettlementSnapshotCreationService)
        .should(never())
        .retrySnapshot(any(), any(), any(), any(), any());
  }

  @Test
  void runRetrySnapshotBatchRecordsFailureWhenMissionRuleIsMissing() {
    DailySettlementSnapshot snapshot =
        snapshot(1L, 10L, DailySettlementType.A, DailySettlementPhase.PROVISIONAL);
    givenRetryTargets(snapshot);
    givenClaimed(snapshot);
    given(missionRuleRepository.findWithCrewByCrewId(10L)).willReturn(Optional.empty());

    service.runRetrySnapshotBatch(NOW);

    then(dailySettlementSnapshotCreationService)
        .should(never())
        .retrySnapshot(any(), any(), any(), any(), any());
    then(dailySettlementSnapshotFailureRecordService)
        .should()
        .recordRetryFailure(
            1L, "daily-settlement-snapshot-retry-1-20260617121500", NOW, "미션 규칙을 찾을 수 없습니다.");
  }

  @Test
  void runRetrySnapshotBatchRecordsFailureWhenMissionRuleTypeDoesNotMatchSnapshotType() {
    DailySettlementSnapshot snapshot =
        snapshot(1L, 10L, DailySettlementType.A, DailySettlementPhase.PROVISIONAL);
    MissionRule missionRule = missionRule(DailySettlementType.B);
    givenRetryTargets(snapshot);
    givenClaimed(snapshot);
    given(missionRuleRepository.findWithCrewByCrewId(10L)).willReturn(Optional.of(missionRule));

    service.runRetrySnapshotBatch(NOW);

    then(dailySettlementSnapshotCreationService)
        .should(never())
        .retrySnapshot(any(), any(), any(), any(), any());
    then(dailySettlementSnapshotFailureRecordService)
        .should()
        .recordRetryFailure(
            1L,
            "daily-settlement-snapshot-retry-1-20260617121500",
            NOW,
            "스냅샷 정산 타입과 현재 미션 규칙 정산 타입이 일치하지 않습니다.");
  }

  @Test
  void runRetrySnapshotBatchLimitsRetryTargetsPerRun() {
    givenRetryTargets();

    service.runRetrySnapshotBatch(NOW);

    then(dailySettlementSnapshotRepository)
        .should()
        .findRetryTargetIds(
            DailySettlementStatus.FAILED,
            DailySettlementStatus.RETRYING,
            DailySettlementSnapshot.MAX_RETRY_COUNT,
            NOW,
            NOW.minusHours(1),
            PageRequest.of(0, 50));
  }

  private void givenRetryTargets(DailySettlementSnapshot... snapshots) {
    java.util.List<Long> snapshotIds =
        Arrays.stream(snapshots).map(snapshot -> snapshot.getId()).toList();
    given(
            dailySettlementSnapshotRepository.findRetryTargetIds(
                eq(DailySettlementStatus.FAILED),
                eq(DailySettlementStatus.RETRYING),
                eq(DailySettlementSnapshot.MAX_RETRY_COUNT),
                eq(NOW),
                eq(NOW.minusHours(1)),
                eq(PageRequest.of(0, 50))))
        .willReturn(snapshotIds);
  }

  private void givenClaimed(DailySettlementSnapshot snapshot) {
    Long snapshotId = snapshot.getId();
    given(
            dailySettlementSnapshotRetryClaimService.claim(
                snapshotId,
                "daily-settlement-snapshot-retry-" + snapshotId + "-20260617121500",
                NOW,
                NOW.minusHours(1)))
        .willReturn(true);
    given(dailySettlementSnapshotRepository.findWithCrewById(snapshotId))
        .willReturn(Optional.of(snapshot));
  }

  private DailySettlementSnapshot snapshot(
      Long id, Long crewId, DailySettlementType type, DailySettlementPhase phase) {
    DailySettlementSnapshot snapshot = org.mockito.Mockito.mock(DailySettlementSnapshot.class);
    Crew crew = org.mockito.Mockito.mock(Crew.class);
    org.mockito.Mockito.lenient().when(snapshot.getId()).thenReturn(id);
    org.mockito.Mockito.lenient().when(snapshot.getCrew()).thenReturn(crew);
    org.mockito.Mockito.lenient().when(crew.getId()).thenReturn(crewId);
    org.mockito.Mockito.lenient().when(snapshot.getDailySettlementType()).thenReturn(type);
    org.mockito.Mockito.lenient().when(snapshot.getPhase()).thenReturn(phase);
    org.mockito.Mockito.lenient()
        .when(snapshot.getMissionDate())
        .thenReturn(LocalDate.of(2026, 6, 15));
    org.mockito.Mockito.lenient()
        .when(snapshot.getStatus())
        .thenReturn(DailySettlementStatus.RETRYING);
    org.mockito.Mockito.lenient()
        .when(snapshot.getBatchRunKey())
        .thenReturn("daily-settlement-snapshot-retry-" + id + "-20260617121500");
    return snapshot;
  }

  private MissionRule missionRule(DailySettlementType type) {
    MissionRule missionRule = org.mockito.Mockito.mock(MissionRule.class);
    org.mockito.Mockito.lenient().when(missionRule.getDailySettlementType()).thenReturn(type);
    return missionRule;
  }
}
