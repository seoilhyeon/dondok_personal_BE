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
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionLogRepository;
import com.oit.dondok.domain.settlement.entity.DailySettlementParticipantSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.repository.DailySettlementParticipantSnapshotRepository;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationResult;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DailySettlementSnapshotCreationServiceTest {

  private static final LocalDate MISSION_DATE = LocalDate.of(2026, 6, 15);
  private static final LocalDateTime FROZEN_AT = LocalDateTime.of(2026, 6, 15, 12, 0);

  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private MissionLogRepository missionLogRepository;
  @Mock private DailySettlementSnapshotRepository dailySettlementSnapshotRepository;

  @Mock
  private DailySettlementParticipantSnapshotRepository dailySettlementParticipantSnapshotRepository;

  @Mock private SettlementCalculatorService settlementCalculatorService;

  @InjectMocks private DailySettlementSnapshotCreationService service;

  @Test
  void createSnapshotStoresDashboardProjectionFields() {
    Member host = member(1L, "host@test.com");
    Member guest = member(2L, "guest@test.com");
    Crew crew = crew(10L, host);
    MissionRule missionRule =
        MissionRule.create(crew, MissionFrequencyType.DAILY, DailySettlementType.A);
    CrewParticipant hostParticipant = participant(100L, crew, host, 1_000L);
    CrewParticipant guestParticipant = participant(101L, crew, guest, 1_000L);
    MissionLog successLog = missionLog(hostParticipant, MISSION_DATE.atTime(9, 0));

    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
                    10L, MISSION_DATE, DailySettlementType.A, DailySettlementPhase.PROVISIONAL))
        .willReturn(java.util.Optional.empty());
    given(crewParticipantRepository.findByCrewIdAndStatus(10L, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(hostParticipant, guestParticipant));
    given(
            missionLogRepository.findManuallyApprovedLogsForDailySettlementProjection(
                org.mockito.Mockito.eq(10L),
                org.mockito.Mockito.eq(crew.getStartAt()),
                org.mockito.Mockito.eq(MISSION_DATE.plusDays(1).atStartOfDay())))
        .willReturn(List.of(successLog));
    given(settlementCalculatorService.calculate(any())).willCallRealMethod();
    given(dailySettlementSnapshotRepository.save(any(DailySettlementSnapshot.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(dailySettlementParticipantSnapshotRepository.saveAll(any()))
        .willAnswer(invocation -> invocation.getArgument(0));

    service.createSnapshot(
        missionRule, MISSION_DATE, DailySettlementPhase.PROVISIONAL, "batch-key", FROZEN_AT);

    ArgumentCaptor<DailySettlementSnapshot> snapshotCaptor =
        ArgumentCaptor.forClass(DailySettlementSnapshot.class);
    then(dailySettlementSnapshotRepository).should().save(snapshotCaptor.capture());
    assertThat(snapshotCaptor.getValue().getPhase()).isEqualTo(DailySettlementPhase.PROVISIONAL);
    assertThat(snapshotCaptor.getValue().getStatus()).isEqualTo(DailySettlementStatus.SUCCEEDED);
    assertThat(snapshotCaptor.getValue().getTotalParticipants()).isEqualTo(2);
    assertThat(snapshotCaptor.getValue().getTotalRecognizedSuccessCount()).isEqualTo(1);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<DailySettlementParticipantSnapshot>> participantCaptor =
        ArgumentCaptor.forClass(List.class);
    then(dailySettlementParticipantSnapshotRepository)
        .should()
        .saveAll(participantCaptor.capture());
    assertThat(participantCaptor.getValue()).hasSize(2);
    DailySettlementParticipantSnapshot hostSnapshot = participantCaptor.getValue().get(0);
    assertThat(hostSnapshot.getSuccessCount()).isEqualTo(1);
    assertThat(hostSnapshot.getShareRatio()).isEqualByComparingTo(new BigDecimal("1.000000"));
    assertThat(hostSnapshot.getExpectedRefundAmount()).isEqualTo(2_000L);
  }

  @Test
  void createFinalizedSnapshotUsesManualAndFinalizedAutoApproveLogs() {
    Member host = member(1L, "host@test.com");
    Member guest = member(2L, "guest@test.com");
    Crew crew = crew(10L, host);
    MissionRule missionRule =
        MissionRule.create(crew, MissionFrequencyType.DAILY, DailySettlementType.A);
    CrewParticipant hostParticipant = participant(100L, crew, host, 1_000L);
    CrewParticipant guestParticipant = participant(101L, crew, guest, 1_000L);
    MissionLog successLog = missionLog(hostParticipant, MISSION_DATE.atTime(9, 0));

    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
                    10L, MISSION_DATE, DailySettlementType.A, DailySettlementPhase.FINALIZED))
        .willReturn(java.util.Optional.empty());
    given(crewParticipantRepository.findByCrewIdAndStatus(10L, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(hostParticipant, guestParticipant));
    given(
            missionLogRepository.findFinalizedApprovedLogsForDailySettlementProjection(
                org.mockito.Mockito.eq(10L),
                org.mockito.Mockito.eq(crew.getStartAt()),
                org.mockito.Mockito.eq(MISSION_DATE.plusDays(1).atStartOfDay())))
        .willReturn(List.of(successLog));
    given(settlementCalculatorService.calculate(any())).willCallRealMethod();
    given(dailySettlementSnapshotRepository.save(any(DailySettlementSnapshot.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(dailySettlementParticipantSnapshotRepository.saveAll(any()))
        .willAnswer(invocation -> invocation.getArgument(0));

    service.createSnapshot(
        missionRule, MISSION_DATE, DailySettlementPhase.FINALIZED, "batch-key", FROZEN_AT);

    ArgumentCaptor<DailySettlementSnapshot> snapshotCaptor =
        ArgumentCaptor.forClass(DailySettlementSnapshot.class);
    then(dailySettlementSnapshotRepository).should().save(snapshotCaptor.capture());
    assertThat(snapshotCaptor.getValue().getPhase()).isEqualTo(DailySettlementPhase.FINALIZED);
    assertThat(snapshotCaptor.getValue().getStatus()).isEqualTo(DailySettlementStatus.SUCCEEDED);
    assertThat(snapshotCaptor.getValue().getTotalRecognizedSuccessCount()).isEqualTo(1);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<DailySettlementParticipantSnapshot>> participantCaptor =
        ArgumentCaptor.forClass(List.class);
    then(dailySettlementParticipantSnapshotRepository)
        .should()
        .saveAll(participantCaptor.capture());
    assertThat(participantCaptor.getValue()).hasSize(2);
    assertThat(participantCaptor.getValue().get(0).getSuccessCount()).isEqualTo(1);
  }

  @Test
  void createSnapshotThrowsCalculationFailureWhenCalculationResultHasDuplicateParticipantKey() {
    Member host = member(1L, "host@test.com");
    Crew crew = crew(10L, host);
    MissionRule missionRule =
        MissionRule.create(crew, MissionFrequencyType.DAILY, DailySettlementType.A);
    CrewParticipant hostParticipant = participant(100L, crew, host, 1_000L);
    SettlementParticipantResult firstResult = participantResult(100L, 1_000L);
    SettlementParticipantResult duplicateResult = participantResult(100L, 1_000L);

    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
                    10L, MISSION_DATE, DailySettlementType.A, DailySettlementPhase.PROVISIONAL))
        .willReturn(java.util.Optional.empty());
    given(crewParticipantRepository.findByCrewIdAndStatus(10L, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(hostParticipant));
    given(
            missionLogRepository.findManuallyApprovedLogsForDailySettlementProjection(
                org.mockito.Mockito.eq(10L),
                org.mockito.Mockito.eq(crew.getStartAt()),
                org.mockito.Mockito.eq(MISSION_DATE.plusDays(1).atStartOfDay())))
        .willReturn(List.of());
    given(settlementCalculatorService.calculate(any()))
        .willReturn(
            new SettlementCalculationResult(
                1,
                1_000L,
                1,
                1_000L,
                0L,
                RemainderPolicy.HOST_REMAINDER,
                List.of(firstResult, duplicateResult)));
    given(dailySettlementSnapshotRepository.save(any(DailySettlementSnapshot.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(
            () ->
                service.createSnapshot(
                    missionRule,
                    MISSION_DATE,
                    DailySettlementPhase.PROVISIONAL,
                    "batch-key",
                    FROZEN_AT))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.CALCULATION_FAILED);
  }

  @Test
  void createSnapshotThrowsCalculationFailureWhenParticipantResultIsMissing() {
    Member host = member(1L, "host@test.com");
    Crew crew = crew(10L, host);
    MissionRule missionRule =
        MissionRule.create(crew, MissionFrequencyType.DAILY, DailySettlementType.A);
    CrewParticipant hostParticipant = participant(100L, crew, host, 1_000L);

    given(
            dailySettlementSnapshotRepository
                .findByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
                    10L, MISSION_DATE, DailySettlementType.A, DailySettlementPhase.PROVISIONAL))
        .willReturn(java.util.Optional.empty());
    given(crewParticipantRepository.findByCrewIdAndStatus(10L, CrewParticipantStatus.LOCKED))
        .willReturn(List.of(hostParticipant));
    given(
            missionLogRepository.findManuallyApprovedLogsForDailySettlementProjection(
                org.mockito.Mockito.eq(10L),
                org.mockito.Mockito.eq(crew.getStartAt()),
                org.mockito.Mockito.eq(MISSION_DATE.plusDays(1).atStartOfDay())))
        .willReturn(List.of());
    given(settlementCalculatorService.calculate(any()))
        .willReturn(
            new SettlementCalculationResult(
                1, 1_000L, 0, 0L, 0L, RemainderPolicy.HOST_REMAINDER, List.of()));
    given(dailySettlementSnapshotRepository.save(any(DailySettlementSnapshot.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(
            () ->
                service.createSnapshot(
                    missionRule,
                    MISSION_DATE,
                    DailySettlementPhase.PROVISIONAL,
                    "batch-key",
                    FROZEN_AT))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.CALCULATION_FAILED);
  }

  private Member member(Long id, String email) {
    Member member = Member.create(email, "password", email);
    ReflectionTestUtils.setField(member, "id", id);
    return member;
  }

  private Crew crew(Long id, Member host) {
    Crew crew =
        Crew.create(
            host,
            "crew",
            "description",
            null,
            "category",
            "{}",
            com.oit.dondok.domain.crew.entity.HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.of(2026, 6, 1, 0, 0),
            1_000L,
            2,
            10,
            LocalDateTime.of(2026, 6, 1, 0, 0),
            LocalDateTime.of(2026, 6, 10, 0, 0),
            LocalDateTime.of(2026, 6, 20, 23, 59));
    ReflectionTestUtils.setField(crew, "id", id);
    return crew;
  }

  private CrewParticipant participant(Long id, Crew crew, Member member, Long depositAmount) {
    CrewParticipant participant =
        CrewParticipant.create(crew, member, depositAmount, LocalDateTime.of(2026, 6, 10, 0, 0));
    ReflectionTestUtils.setField(participant, "id", id);
    return participant;
  }

  private MissionLog missionLog(CrewParticipant participant, LocalDateTime serverTime) {
    MissionLog missionLog = org.mockito.Mockito.mock(MissionLog.class);
    given(missionLog.getCrewParticipant()).willReturn(participant);
    given(missionLog.getServerTime()).willReturn(serverTime);
    return missionLog;
  }

  private SettlementParticipantResult participantResult(Long participantKey, Long refundAmount) {
    return new SettlementParticipantResult(
        participantKey, false, 1_000L, 1, 1, 1, 0, BigDecimal.ONE, refundAmount, 0L, refundAmount);
  }
}
