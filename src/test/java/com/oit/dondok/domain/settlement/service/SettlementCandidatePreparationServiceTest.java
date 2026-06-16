package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementRuleContextSnapshot;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementCandidatePreparationServiceTest {

  private static final Long CREW_ID = 1L;
  private static final Long SETTLEMENT_ID = 10L;
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 0, 0);
  private static final String BATCH_RUN_KEY = "batch-001";

  @Mock private CrewRepository crewRepository;
  @Mock private MissionRuleRepository missionRuleRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private SettlementEligibilityPolicy settlementEligibilityPolicy;

  @InjectMocks private SettlementCandidatePreparationService service;

  @Test
  void activeEligibleCrewIsClosedAndPendingSettlementIsCreated() {
    Crew crew = crew();
    MissionRule missionRule = missionRule();
    Settlement savedSettlement = settlement(SETTLEMENT_ID);
    given(crew.getStatus()).willReturn(CrewStatus.ACTIVE);
    given(missionRule.getDailySettlementType()).willReturn(DailySettlementType.B);
    given(missionRule.getFrequencyType()).willReturn(MissionFrequencyType.DAILY);
    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(settlementEligibilityPolicy.isCompletedCrewEligible(crew, missionRule, NOW))
        .willReturn(true);
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());
    given(settlementRepository.save(any(Settlement.class))).willReturn(savedSettlement);

    Optional<Long> result =
        service.prepareCompletedCrewSettlementCandidate(
            CREW_ID, DailySettlementType.B, BATCH_RUN_KEY, NOW);

    assertThat(result).contains(SETTLEMENT_ID);
    then(crew).should().close();
    ArgumentCaptor<Settlement> settlementCaptor = ArgumentCaptor.forClass(Settlement.class);
    then(settlementRepository).should().save(settlementCaptor.capture());
    assertThat(settlementCaptor.getValue().getStatus()).isEqualTo(SettlementStatus.PENDING);
    assertThat(settlementCaptor.getValue().getBatchRunKey()).isEqualTo(BATCH_RUN_KEY);
    SettlementRuleContextSnapshot snapshot = settlementCaptor.getValue().getRuleContextSnapshot();
    assertThat(snapshot.dailySettlementType()).isEqualTo(DailySettlementType.B);
    assertThat(snapshot.frequencyType()).isEqualTo(MissionFrequencyType.DAILY);
  }

  @Test
  void ineligibleCrewIsSkippedBeforeCloseOrSettlementCreation() {
    Crew crew = crew();
    MissionRule missionRule = missionRule();
    given(missionRule.getDailySettlementType()).willReturn(DailySettlementType.B);
    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(settlementEligibilityPolicy.isCompletedCrewEligible(crew, missionRule, NOW))
        .willReturn(false);

    Optional<Long> result =
        service.prepareCompletedCrewSettlementCandidate(
            CREW_ID, DailySettlementType.B, BATCH_RUN_KEY, NOW);

    assertThat(result).isEmpty();
    then(crew).should(never()).close();
    then(settlementRepository).should(never()).save(any());
  }

  @Test
  void typeMismatchCrewIsSkippedBeforeEligibilityCheck() {
    Crew crew = crew();
    MissionRule missionRule = missionRule();
    given(missionRule.getDailySettlementType()).willReturn(DailySettlementType.B);
    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));

    Optional<Long> result =
        service.prepareCompletedCrewSettlementCandidate(
            CREW_ID, DailySettlementType.A, BATCH_RUN_KEY, NOW);

    assertThat(result).isEmpty();
    then(settlementEligibilityPolicy).should(never()).isCompletedCrewEligible(any(), any(), any());
    then(crew).should(never()).close();
    then(settlementRepository).should(never()).save(any());
  }

  @Test
  void closedCrewBackfillReusesExistingSettlement() {
    Crew crew = crew();
    MissionRule missionRule = missionRule();
    Settlement existingSettlement = settlement(SETTLEMENT_ID);
    given(crew.getStatus()).willReturn(CrewStatus.CLOSED);
    given(missionRule.getDailySettlementType()).willReturn(DailySettlementType.B);
    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(settlementEligibilityPolicy.isCompletedCrewEligible(crew, missionRule, NOW))
        .willReturn(true);
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(existingSettlement));

    Optional<Long> result =
        service.prepareCompletedCrewSettlementCandidate(
            CREW_ID, DailySettlementType.B, BATCH_RUN_KEY, NOW);

    assertThat(result).contains(SETTLEMENT_ID);
    then(crew).should(never()).close();
    then(settlementRepository).should(never()).save(any());
  }

  @Test
  void missingCrewIsMarkedAsInputLoadFailedInPreparationFailure() {
    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.prepareCompletedCrewSettlementCandidate(
                    CREW_ID, DailySettlementType.B, BATCH_RUN_KEY, NOW))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .hasMessageContaining("crewId=" + CREW_ID)
        .extracting(ex -> ((SettlementBatchRunFailure) ex).getFailureCode())
        .isEqualTo(SettlementFailureCode.INPUT_LOAD_FAILED);
  }

  @Test
  void prepareCompletedCrewSettlementCandidateRejectsNullDailySettlementType() {
    assertThatThrownBy(
            () ->
                service.prepareCompletedCrewSettlementCandidate(CREW_ID, null, BATCH_RUN_KEY, NOW))
        .isInstanceOf(CustomException.class)
        .extracting(ex -> ((CustomException) ex).getErrorCode())
        .isEqualTo(GlobalErrorCode.INVALID_INPUT);
  }

  private Crew crew() {
    return org.mockito.Mockito.mock(Crew.class);
  }

  private MissionRule missionRule() {
    return org.mockito.Mockito.mock(MissionRule.class);
  }

  private Settlement settlement(Long id) {
    Settlement settlement = org.mockito.Mockito.mock(Settlement.class);
    given(settlement.getId()).willReturn(id);
    return settlement;
  }
}
