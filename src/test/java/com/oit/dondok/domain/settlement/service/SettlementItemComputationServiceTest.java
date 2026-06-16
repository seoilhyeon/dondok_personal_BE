package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionLogRepository;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.entity.DailySettlementParticipantSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.repository.DailySettlementParticipantSnapshotRepository;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationInput;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationResult;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantInput;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementItemComputationServiceTest {

  private static final Long SETTLEMENT_ID = 10L;
  private static final Long CREW_ID = 20L;
  private static final Long PARTICIPANT_ID = 30L;
  private static final Long HOST_MEMBER_ID = 40L;
  private static final LocalDate MISSION_DATE = LocalDate.of(2026, 6, 5);
  private static final LocalDateTime START_AT = LocalDateTime.of(2026, 6, 1, 0, 0);
  private static final LocalDateTime END_AT = LocalDateTime.of(2026, 6, 30, 23, 59);

  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private MissionRuleRepository missionRuleRepository;
  @Mock private MissionLogRepository missionLogRepository;
  @Mock private FinalSettlementMissionDateResolver missionDateResolver;
  @Mock private DailySettlementSnapshotRepository dailySettlementSnapshotRepository;

  @Mock
  private DailySettlementParticipantSnapshotRepository dailySettlementParticipantSnapshotRepository;

  @Mock private SettlementRepository settlementRepository;
  @Mock private SettlementItemRepository settlementItemRepository;
  @Mock private SettlementCalculatorService settlementCalculatorService;

  @InjectMocks private SettlementItemComputationService settlementItemComputationService;

  @Test
  void ensureSettlementItemsUsesFinalizedDailyParticipantSnapshots() {
    Crew crew = crew();
    Settlement settlement = settlement(crew);
    MissionRule missionRule = missionRule();
    Member host = member(HOST_MEMBER_ID);
    CrewParticipant participant = participant(host);
    DailySettlementSnapshot snapshot = dailySnapshot(100L, MISSION_DATE);
    DailySettlementParticipantSnapshot participantSnapshot =
        participantSnapshot(snapshot, participant, 1);

    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(MISSION_DATE));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    CREW_ID,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(MISSION_DATE)))
        .willReturn(List.of(snapshot));
    given(dailySettlementParticipantSnapshotRepository.findByDailySettlementSnapshotIdIn(any()))
        .willReturn(List.of(participantSnapshot));
    givenFinalizedLogs(crew, snapshot, missionLog(participant));
    given(settlementCalculatorService.calculate(any(SettlementCalculationInput.class)))
        .willAnswer(
            invocation -> {
              SettlementCalculationInput input = invocation.getArgument(0);
              assertThat(input.participants()).hasSize(1);
              assertThat(input.participants().get(0).successCountRaw()).isEqualTo(1);
              assertThat(input.participants().get(0).recognizedSuccessCount()).isEqualTo(1);
              assertThat(input.participants().get(0).recognizedDatesCount()).isEqualTo(1);
              assertThat(input.participants().get(0).excludedSuccessCount()).isZero();
              return calculationResult(input.participants().get(0));
            });
    given(
            settlementItemRepository.findBySettlementIdAndCrewParticipantId(
                SETTLEMENT_ID, PARTICIPANT_ID))
        .willReturn(Optional.empty());
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(List.of());

    settlementItemComputationService.ensureSettlementItems(SETTLEMENT_ID);

    ArgumentCaptor<SettlementItem> itemCaptor = ArgumentCaptor.forClass(SettlementItem.class);
    then(settlementItemRepository).should().save(itemCaptor.capture());
    assertThat(itemCaptor.getValue().getSuccessCountRaw()).isEqualTo(1);
    assertThat(itemCaptor.getValue().getRecognizedSuccessCount()).isEqualTo(1);
    assertThat(itemCaptor.getValue().getExcludedSuccessCount()).isZero();
  }

  @Test
  void ensureSettlementItemsUsesLastCumulativeSnapshotWithoutSummingPreviousSnapshots() {
    Crew crew = crew();
    Settlement settlement = settlement(crew);
    MissionRule missionRule = missionRule();
    Member host = member(HOST_MEMBER_ID);
    CrewParticipant participant = participant(host);
    LocalDate firstDate = MISSION_DATE;
    LocalDate lastDate = MISSION_DATE.plusDays(1);
    DailySettlementSnapshot firstSnapshot = dailySnapshot(100L, firstDate);
    DailySettlementSnapshot lastSnapshot = dailySnapshot(101L, lastDate);
    DailySettlementParticipantSnapshot lastParticipantSnapshot =
        participantSnapshot(lastSnapshot, participant, 2);

    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(firstDate, lastDate));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    CREW_ID,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(firstDate, lastDate)))
        .willReturn(List.of(firstSnapshot, lastSnapshot));
    given(dailySettlementParticipantSnapshotRepository.findByDailySettlementSnapshotIdIn(any()))
        .willReturn(List.of(lastParticipantSnapshot));
    givenFinalizedLogs(crew, lastSnapshot, missionLog(participant), missionLog(participant));
    given(settlementCalculatorService.calculate(any(SettlementCalculationInput.class)))
        .willAnswer(
            invocation -> {
              SettlementCalculationInput input = invocation.getArgument(0);
              assertThat(input.participants().get(0).successCountRaw()).isEqualTo(2);
              assertThat(input.participants().get(0).recognizedSuccessCount()).isEqualTo(2);
              return calculationResult(input.participants().get(0));
            });
    given(
            settlementItemRepository.findBySettlementIdAndCrewParticipantId(
                SETTLEMENT_ID, PARTICIPANT_ID))
        .willReturn(Optional.empty());
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(List.of());

    settlementItemComputationService.ensureSettlementItems(SETTLEMENT_ID);
  }

  @Test
  void ensureSettlementItemsPreservesRawAndExcludedCountsFromFinalizedLogs() {
    Crew crew = crew();
    Settlement settlement = settlement(crew);
    MissionRule missionRule = missionRule();
    Member host = member(HOST_MEMBER_ID);
    CrewParticipant participant = participant(host);
    DailySettlementSnapshot snapshot = dailySnapshot(100L, MISSION_DATE);
    DailySettlementParticipantSnapshot participantSnapshot =
        participantSnapshot(snapshot, participant, 1);

    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(MISSION_DATE));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    CREW_ID,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(MISSION_DATE)))
        .willReturn(List.of(snapshot));
    given(dailySettlementParticipantSnapshotRepository.findByDailySettlementSnapshotIdIn(any()))
        .willReturn(List.of(participantSnapshot));
    givenFinalizedLogs(crew, snapshot, missionLog(participant), missionLog(participant));
    given(settlementCalculatorService.calculate(any(SettlementCalculationInput.class)))
        .willAnswer(
            invocation -> {
              SettlementCalculationInput input = invocation.getArgument(0);
              assertThat(input.participants().get(0).successCountRaw()).isEqualTo(2);
              assertThat(input.participants().get(0).recognizedSuccessCount()).isEqualTo(1);
              assertThat(input.participants().get(0).excludedSuccessCount()).isEqualTo(1);
              return calculationResult(input.participants().get(0));
            });
    given(
            settlementItemRepository.findBySettlementIdAndCrewParticipantId(
                SETTLEMENT_ID, PARTICIPANT_ID))
        .willReturn(Optional.empty());
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(List.of());

    settlementItemComputationService.ensureSettlementItems(SETTLEMENT_ID);
  }

  @Test
  void ensureSettlementItemsFailsWhenFinalizedSnapshotIsMissing() {
    Crew crew = crew();
    Settlement settlement = settlement(crew);
    MissionRule missionRule = missionRule();
    CrewParticipant participant = participant(member(HOST_MEMBER_ID));
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(MISSION_DATE));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    CREW_ID,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(MISSION_DATE)))
        .willReturn(List.of());

    assertThatThrownBy(() -> settlementItemComputationService.ensureSettlementItems(SETTLEMENT_ID))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.INPUT_LOAD_FAILED);
  }

  @Test
  void ensureSettlementItemsFailsWhenParticipantSnapshotIsMissing() {
    Crew crew = crew();
    Settlement settlement = settlement(crew);
    MissionRule missionRule = missionRule();
    CrewParticipant participant = participant(member(HOST_MEMBER_ID));
    DailySettlementSnapshot snapshot = dailySnapshot(100L, MISSION_DATE);
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(MISSION_DATE));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    CREW_ID,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(MISSION_DATE)))
        .willReturn(List.of(snapshot));
    given(dailySettlementParticipantSnapshotRepository.findByDailySettlementSnapshotIdIn(any()))
        .willReturn(List.of());

    assertThatThrownBy(() -> settlementItemComputationService.ensureSettlementItems(SETTLEMENT_ID))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.INPUT_LOAD_FAILED);
  }

  @Test
  void ensureSettlementItemsFailsWhenExistingSnapshotConflicts() {
    Crew crew = crew();
    Settlement settlement = settlement(crew);
    MissionRule missionRule = missionRule();
    CrewParticipant participant = participant(member(HOST_MEMBER_ID));
    DailySettlementSnapshot snapshot = dailySnapshot(100L, MISSION_DATE);
    DailySettlementParticipantSnapshot participantSnapshot =
        participantSnapshot(snapshot, participant, 1);
    SettlementItem existingItem = org.mockito.Mockito.mock(SettlementItem.class);
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(MISSION_DATE));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    CREW_ID,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(MISSION_DATE)))
        .willReturn(List.of(snapshot));
    given(dailySettlementParticipantSnapshotRepository.findByDailySettlementSnapshotIdIn(any()))
        .willReturn(List.of(participantSnapshot));
    givenFinalizedLogs(crew, snapshot, missionLog(participant));
    given(settlementCalculatorService.calculate(any(SettlementCalculationInput.class)))
        .willReturn(
            calculationResult(
                new SettlementParticipantInput(PARTICIPANT_ID, true, 10_000L, 1, 1, 1, 0)));
    given(
            settlementItemRepository.findBySettlementIdAndCrewParticipantId(
                SETTLEMENT_ID, PARTICIPANT_ID))
        .willReturn(Optional.of(existingItem));
    given(
            existingItem.matchesCalculation(
                anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any(), anyLong(),
                anyLong(), anyLong()))
        .willReturn(false);
    given(existingItem.getId()).willReturn(999L);

    assertThatThrownBy(() -> settlementItemComputationService.ensureSettlementItems(SETTLEMENT_ID))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.CALCULATION_FAILED);
  }

  @Test
  void ensureSettlementItemsFailsWhenCalculationResultHasDuplicateParticipantKey() {
    Crew crew = crew();
    Settlement settlement = settlement(crew);
    MissionRule missionRule = missionRule();
    CrewParticipant participant = participant(member(HOST_MEMBER_ID));
    DailySettlementSnapshot snapshot = dailySnapshot(100L, MISSION_DATE);
    DailySettlementParticipantSnapshot participantSnapshot =
        participantSnapshot(snapshot, participant, 1);
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(missionDateResolver.resolveMissionDates(crew, missionRule))
        .willReturn(List.of(MISSION_DATE));
    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
                    CREW_ID,
                    DailySettlementType.A,
                    DailySettlementPhase.FINALIZED,
                    DailySettlementStatus.SUCCEEDED,
                    List.of(MISSION_DATE)))
        .willReturn(List.of(snapshot));
    given(dailySettlementParticipantSnapshotRepository.findByDailySettlementSnapshotIdIn(any()))
        .willReturn(List.of(participantSnapshot));
    givenFinalizedLogs(crew, snapshot, missionLog(participant));
    given(settlementCalculatorService.calculate(any(SettlementCalculationInput.class)))
        .willReturn(
            new SettlementCalculationResult(
                1,
                10_000L,
                1,
                10_000L,
                0L,
                RemainderPolicy.HOST_REMAINDER,
                List.of(result(PARTICIPANT_ID), result(PARTICIPANT_ID))));

    assertThatThrownBy(() -> settlementItemComputationService.ensureSettlementItems(SETTLEMENT_ID))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.CALCULATION_FAILED);
  }

  private SettlementCalculationResult calculationResult(SettlementParticipantInput input) {
    return new SettlementCalculationResult(
        1,
        10_000L,
        input.recognizedSuccessCount(),
        10_000L,
        0L,
        RemainderPolicy.HOST_REMAINDER,
        List.of(
            SettlementParticipantResult.builder(input)
                .shareRatio(new BigDecimal("1.000000"))
                .baseRefundAmount(10_000L)
                .remainderBonusAmount(0L)
                .refundAmount(10_000L)
                .build()));
  }

  private SettlementParticipantResult result(Long participantId) {
    return SettlementParticipantResult.builder(
            new SettlementParticipantInput(participantId, true, 10_000L, 1, 1, 1, 0))
        .shareRatio(new BigDecimal("1.000000"))
        .baseRefundAmount(10_000L)
        .remainderBonusAmount(0L)
        .refundAmount(10_000L)
        .build();
  }

  private Settlement settlement(Crew crew) {
    Settlement settlement = org.mockito.Mockito.mock(Settlement.class);
    org.mockito.Mockito.lenient().when(settlement.getId()).thenReturn(SETTLEMENT_ID);
    org.mockito.Mockito.lenient().when(settlement.getCrew()).thenReturn(crew);
    return settlement;
  }

  private Crew crew() {
    Crew crew = org.mockito.Mockito.mock(Crew.class);
    Member host = member(HOST_MEMBER_ID);
    org.mockito.Mockito.lenient().when(crew.getId()).thenReturn(CREW_ID);
    org.mockito.Mockito.lenient().when(crew.getHostMember()).thenReturn(host);
    org.mockito.Mockito.lenient().when(crew.getStartAt()).thenReturn(START_AT);
    org.mockito.Mockito.lenient().when(crew.getEndAt()).thenReturn(END_AT);
    return crew;
  }

  private MissionRule missionRule() {
    MissionRule missionRule = org.mockito.Mockito.mock(MissionRule.class);
    org.mockito.Mockito.lenient()
        .when(missionRule.getDailySettlementType())
        .thenReturn(DailySettlementType.A);
    return missionRule;
  }

  private Member member(Long id) {
    Member member = org.mockito.Mockito.mock(Member.class);
    org.mockito.Mockito.lenient().when(member.getId()).thenReturn(id);
    return member;
  }

  private CrewParticipant participant(Member member) {
    CrewParticipant participant = org.mockito.Mockito.mock(CrewParticipant.class);
    org.mockito.Mockito.lenient().when(participant.getId()).thenReturn(PARTICIPANT_ID);
    org.mockito.Mockito.lenient().when(participant.getMember()).thenReturn(member);
    org.mockito.Mockito.lenient().when(participant.getDepositAmount()).thenReturn(10_000L);
    return participant;
  }

  private void givenFinalizedLogs(
      Crew crew, DailySettlementSnapshot snapshot, MissionLog... missionLogs) {
    given(
            missionLogRepository.findFinalizedApprovedLogsForDailySettlementProjection(
                crew.getId(),
                crew.getStartAt(),
                snapshot.getMissionDate().plusDays(1).atStartOfDay()))
        .willReturn(List.of(missionLogs));
  }

  private MissionLog missionLog(CrewParticipant participant) {
    MissionLog missionLog = org.mockito.Mockito.mock(MissionLog.class);
    org.mockito.Mockito.lenient().when(missionLog.getCrewParticipant()).thenReturn(participant);
    return missionLog;
  }

  private DailySettlementSnapshot dailySnapshot(Long id, LocalDate missionDate) {
    DailySettlementSnapshot snapshot = org.mockito.Mockito.mock(DailySettlementSnapshot.class);
    org.mockito.Mockito.lenient().when(snapshot.getId()).thenReturn(id);
    org.mockito.Mockito.lenient().when(snapshot.getMissionDate()).thenReturn(missionDate);
    return snapshot;
  }

  private DailySettlementParticipantSnapshot participantSnapshot(
      DailySettlementSnapshot snapshot, CrewParticipant participant, int successCount) {
    DailySettlementParticipantSnapshot participantSnapshot =
        org.mockito.Mockito.mock(DailySettlementParticipantSnapshot.class);
    org.mockito.Mockito.lenient()
        .when(participantSnapshot.getDailySettlementSnapshot())
        .thenReturn(snapshot);
    org.mockito.Mockito.lenient()
        .when(participantSnapshot.getCrewParticipant())
        .thenReturn(participant);
    org.mockito.Mockito.lenient()
        .when(participantSnapshot.getSuccessCount())
        .thenReturn(successCount);
    return participantSnapshot;
  }
}
