package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.repository.MissionLogRepository;
import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationInput;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationResult;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantInput;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantResult;
import java.math.BigDecimal;
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
  private static final LocalDateTime START_AT = LocalDateTime.of(2026, 6, 1, 0, 0);
  private static final LocalDateTime END_AT = LocalDateTime.of(2026, 6, 30, 23, 59);

  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private MissionLogRepository missionLogRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private SettlementItemRepository settlementItemRepository;
  @Mock private SettlementCalculatorService settlementCalculatorService;

  @InjectMocks private SettlementItemComputationService settlementItemComputationService;

  @Test
  void ensureSettlementItemsRecognizesOnlyOneSuccessPerServerDate() {
    Crew crew = crew();
    Settlement settlement = settlement(crew);
    Member host = member(HOST_MEMBER_ID);
    CrewParticipant participant = participant(host);
    MissionLog firstLog = missionLog(participant, LocalDateTime.of(2026, 6, 5, 8, 0));
    MissionLog duplicateSameDateLog = missionLog(participant, LocalDateTime.of(2026, 6, 5, 21, 0));

    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant));
    given(
            missionLogRepository
                .findByCrewIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThanEqual(
                    CREW_ID, CertificationStatus.SUCCESS, START_AT, END_AT))
        .willReturn(List.of(firstLog, duplicateSameDateLog));
    given(settlementCalculatorService.calculate(any(SettlementCalculationInput.class)))
        .willAnswer(
            invocation -> {
              SettlementCalculationInput input = invocation.getArgument(0);
              assertThat(input.participants()).hasSize(1);
              assertThat(input.participants().get(0).successCountRaw()).isEqualTo(2);
              assertThat(input.participants().get(0).recognizedSuccessCount()).isEqualTo(1);
              assertThat(input.participants().get(0).recognizedDatesCount()).isEqualTo(1);
              assertThat(input.participants().get(0).excludedSuccessCount()).isEqualTo(1);
              return new SettlementCalculationResult(
                  1,
                  10_000L,
                  1,
                  10_000L,
                  0L,
                  RemainderPolicy.HOST_REMAINDER,
                  List.of(
                      SettlementParticipantResult.builder(input.participants().get(0))
                          .shareRatio(new BigDecimal("1.000000"))
                          .baseRefundAmount(10_000L)
                          .remainderBonusAmount(0L)
                          .refundAmount(10_000L)
                          .build()));
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
    assertThat(itemCaptor.getValue().getSuccessCountRaw()).isEqualTo(2);
    assertThat(itemCaptor.getValue().getRecognizedSuccessCount()).isEqualTo(1);
    assertThat(itemCaptor.getValue().getExcludedSuccessCount()).isEqualTo(1);
  }

  @Test
  void ensureSettlementItemsFailsWhenLockedBaselineIsEmpty() {
    Crew crew = crew();
    Settlement settlement = settlement(crew);
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
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
    Member host = member(HOST_MEMBER_ID);
    CrewParticipant participant = participant(host);
    SettlementItem existingItem = org.mockito.Mockito.mock(SettlementItem.class);
    MissionLog successLog = missionLog(participant, LocalDateTime.of(2026, 6, 5, 8, 0));
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant));
    given(
            missionLogRepository
                .findByCrewIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThanEqual(
                    CREW_ID, CertificationStatus.SUCCESS, START_AT, END_AT))
        .willReturn(List.of(successLog));
    given(settlementCalculatorService.calculate(any(SettlementCalculationInput.class)))
        .willReturn(
            new SettlementCalculationResult(
                1,
                10_000L,
                1,
                10_000L,
                0L,
                RemainderPolicy.HOST_REMAINDER,
                List.of(
                    SettlementParticipantResult.builder(
                            new SettlementParticipantInput(
                                PARTICIPANT_ID, true, 10_000L, 1, 1, 1, 0))
                        .shareRatio(new BigDecimal("1.000000"))
                        .baseRefundAmount(10_000L)
                        .remainderBonusAmount(0L)
                        .refundAmount(10_000L)
                        .build())));
    given(
            settlementItemRepository.findBySettlementIdAndCrewParticipantId(
                SETTLEMENT_ID, PARTICIPANT_ID))
        .willReturn(Optional.of(existingItem));
    given(
            existingItem.matchesCalculation(
                10_000L,
                1,
                1,
                1,
                0,
                START_AT,
                END_AT,
                new BigDecimal("1.000000"),
                10_000L,
                0L,
                10_000L))
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
    Member host = member(HOST_MEMBER_ID);
    Member member2 = member(HOST_MEMBER_ID + 1);
    CrewParticipant participant1 = participant(host, PARTICIPANT_ID);
    CrewParticipant participant2 = participant(member2, PARTICIPANT_ID + 1);
    MissionLog log1 = missionLog(participant1, LocalDateTime.of(2026, 6, 5, 8, 0));
    MissionLog log2 = missionLog(participant2, LocalDateTime.of(2026, 6, 6, 9, 0));

    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant1, participant2));
    given(
            missionLogRepository
                .findByCrewIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThanEqual(
                    CREW_ID, CertificationStatus.SUCCESS, START_AT, END_AT))
        .willReturn(List.of(log1, log2));
    given(settlementCalculatorService.calculate(any(SettlementCalculationInput.class)))
        .willReturn(
            new SettlementCalculationResult(
                2,
                20_000L,
                2,
                20_000L,
                0L,
                RemainderPolicy.HOST_REMAINDER,
                List.of(
                    SettlementParticipantResult.builder(
                            new SettlementParticipantInput(
                                PARTICIPANT_ID, true, 10_000L, 1, 1, 1, 0))
                        .shareRatio(new BigDecimal("0.500000"))
                        .baseRefundAmount(10_000L)
                        .remainderBonusAmount(0L)
                        .refundAmount(10_000L)
                        .build(),
                    SettlementParticipantResult.builder(
                            new SettlementParticipantInput(
                                PARTICIPANT_ID, false, 10_000L, 1, 1, 1, 0))
                        .shareRatio(new BigDecimal("0.500000"))
                        .baseRefundAmount(10_000L)
                        .remainderBonusAmount(0L)
                        .refundAmount(10_000L)
                        .build())));

    assertThatThrownBy(() -> settlementItemComputationService.ensureSettlementItems(SETTLEMENT_ID))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.CALCULATION_FAILED);
  }

  @Test
  void ensureSettlementItemsFailsWhenCalculationParticipantKeysMismatch() {
    Crew crew = crew();
    Settlement settlement = settlement(crew);
    CrewParticipant participant = participant(member(HOST_MEMBER_ID), PARTICIPANT_ID);
    MissionLog mismatchLog = missionLog(participant, LocalDateTime.of(2026, 6, 5, 8, 0));
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(participant));
    given(
            missionLogRepository
                .findByCrewIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThanEqual(
                    CREW_ID, CertificationStatus.SUCCESS, START_AT, END_AT))
        .willReturn(List.of(mismatchLog));
    given(settlementCalculatorService.calculate(any(SettlementCalculationInput.class)))
        .willReturn(
            new SettlementCalculationResult(
                1,
                10_000L,
                1,
                10_000L,
                0L,
                RemainderPolicy.HOST_REMAINDER,
                List.of(
                    SettlementParticipantResult.builder(
                            new SettlementParticipantInput(
                                PARTICIPANT_ID + 1, true, 10_000L, 1, 1, 1, 0))
                        .shareRatio(new BigDecimal("1.000000"))
                        .baseRefundAmount(10_000L)
                        .remainderBonusAmount(0L)
                        .refundAmount(10_000L)
                        .build())));

    assertThatThrownBy(() -> settlementItemComputationService.ensureSettlementItems(SETTLEMENT_ID))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .hasMessageContaining("missing=[30], extra=[31]")
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.CALCULATION_FAILED);
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

  private Member member(Long id) {
    Member member = org.mockito.Mockito.mock(Member.class);
    org.mockito.Mockito.lenient().when(member.getId()).thenReturn(id);
    return member;
  }

  private CrewParticipant participant(Member member) {
    return participant(member, PARTICIPANT_ID);
  }

  private CrewParticipant participant(Member member, Long memberId) {
    CrewParticipant participant = org.mockito.Mockito.mock(CrewParticipant.class);
    org.mockito.Mockito.lenient().when(participant.getId()).thenReturn(memberId);
    org.mockito.Mockito.lenient().when(participant.getMember()).thenReturn(member);
    org.mockito.Mockito.lenient().when(participant.getDepositAmount()).thenReturn(10_000L);
    return participant;
  }

  private MissionLog missionLog(CrewParticipant crewParticipant, LocalDateTime serverTime) {
    MissionLog missionLog = org.mockito.Mockito.mock(MissionLog.class);
    org.mockito.Mockito.lenient().when(missionLog.getCrewParticipant()).thenReturn(crewParticipant);
    org.mockito.Mockito.lenient().when(missionLog.getServerTime()).thenReturn(serverTime);
    return missionLog;
  }
}
