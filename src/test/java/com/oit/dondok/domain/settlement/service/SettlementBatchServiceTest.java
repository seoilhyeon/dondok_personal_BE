package com.oit.dondok.domain.settlement.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementBatchServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 0, 0);
  private static final String BATCH_RUN_KEY = "batch-001-test";

  @Mock private CrewRepository crewRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private SettlementBatchProcessor settlementBatchProcessor;

  @InjectMocks private SettlementBatchService settlementBatchService;

  @Test
  void runFinalSettlementBatchPreparesCandidatesAndCompletesClaimedSettlement() {
    Crew activeCrew = crew(1L);
    Crew closedCrew = crew(2L);
    Settlement settlement = settlement(10L);

    given(crewRepository.findByStatusAndEndAtLessThanEqual(CrewStatus.ACTIVE, NOW))
        .willReturn(List.of(activeCrew));
    given(crewRepository.findClosedWithoutSettlement()).willReturn(List.of(closedCrew));
    given(
            settlementRepository.findByStatusInAndRetryCountLessThanOrderByIdAsc(
                List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT),
                Settlement.MAX_RETRY_COUNT))
        .willReturn(List.of(settlement));
    given(settlementBatchProcessor.claimSettlement(10L, BATCH_RUN_KEY, NOW)).willReturn(true);
    given(settlementBatchProcessor.ensureSettlementItems(10L)).willReturn(List.of(100L, 101L));

    settlementBatchService.runFinalSettlementBatch(NOW, BATCH_RUN_KEY);

    then(settlementBatchProcessor)
        .should()
        .prepareCompletedCrewSettlementCandidate(1L, BATCH_RUN_KEY, NOW);
    then(settlementBatchProcessor)
        .should()
        .prepareCompletedCrewSettlementCandidate(2L, BATCH_RUN_KEY, NOW);
    then(settlementBatchProcessor).should().refundOneSettlementItem(100L);
    then(settlementBatchProcessor).should().refundOneSettlementItem(101L);
    then(settlementBatchProcessor).should().verifyAndMarkSucceeded(eq(10L), any());
    then(settlementBatchProcessor).should(never()).markRunFailure(any(), any(), any(), any());
  }

  @Test
  void runFinalSettlementBatchMarksClaimedSettlementRetryableWhenExecutionFails() {
    Settlement settlement = settlement(10L);

    given(crewRepository.findByStatusAndEndAtLessThanEqual(CrewStatus.ACTIVE, NOW))
        .willReturn(List.of());
    given(crewRepository.findClosedWithoutSettlement()).willReturn(List.of());
    given(
            settlementRepository.findByStatusInAndRetryCountLessThanOrderByIdAsc(
                List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT),
                Settlement.MAX_RETRY_COUNT))
        .willReturn(List.of(settlement));
    given(settlementBatchProcessor.claimSettlement(10L, BATCH_RUN_KEY, NOW)).willReturn(true);
    given(settlementBatchProcessor.ensureSettlementItems(10L))
        .willThrow(
            new SettlementBatchRunFailure(
                SettlementFailureCode.CALCULATION_FAILED, "calculation failed"));

    settlementBatchService.runFinalSettlementBatch(NOW, BATCH_RUN_KEY);

    then(settlementBatchProcessor)
        .should()
        .markRunFailure(eq(10L), eq(SettlementFailureCode.CALCULATION_FAILED), any(), any());
  }

  @Test
  void runFinalSettlementBatchMarksCandidateInputLoadFailure() {
    Crew failingCrew = crew(1L);
    Settlement settlement = settlement(10L);

    given(crewRepository.findByStatusAndEndAtLessThanEqual(CrewStatus.ACTIVE, NOW))
        .willReturn(List.of(failingCrew));
    given(crewRepository.findClosedWithoutSettlement()).willReturn(List.of());
    given(settlementRepository.findByCrewId(1L)).willReturn(Optional.of(settlement));
    given(settlementBatchProcessor.prepareCompletedCrewSettlementCandidate(1L, BATCH_RUN_KEY, NOW))
        .willThrow(
            new SettlementBatchRunFailure(SettlementFailureCode.INPUT_LOAD_FAILED, "crew missing"));
    given(
            settlementRepository.findByStatusInAndRetryCountLessThanOrderByIdAsc(
                List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT),
                Settlement.MAX_RETRY_COUNT))
        .willReturn(List.of());

    settlementBatchService.runFinalSettlementBatch(NOW, BATCH_RUN_KEY);

    then(settlementBatchProcessor)
        .should()
        .markRunFailure(eq(10L), eq(SettlementFailureCode.INPUT_LOAD_FAILED), any(), any());
  }

  @Test
  void runFinalSettlementBatchStopsBeforeSideEffectsWhenClaimLosesRace() {
    Settlement settlement = settlement(10L);

    given(crewRepository.findByStatusAndEndAtLessThanEqual(CrewStatus.ACTIVE, NOW))
        .willReturn(List.of());
    given(crewRepository.findClosedWithoutSettlement()).willReturn(List.of());
    given(
            settlementRepository.findByStatusInAndRetryCountLessThanOrderByIdAsc(
                List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT),
                Settlement.MAX_RETRY_COUNT))
        .willReturn(List.of(settlement));
    given(settlementBatchProcessor.claimSettlement(10L, BATCH_RUN_KEY, NOW)).willReturn(false);

    settlementBatchService.runFinalSettlementBatch(NOW, BATCH_RUN_KEY);

    then(settlementBatchProcessor).should().claimSettlement(10L, BATCH_RUN_KEY, NOW);
    then(settlementBatchProcessor).should(never()).ensureSettlementItems(any());
    then(settlementBatchProcessor).should(never()).refundOneSettlementItem(any());
    then(settlementBatchProcessor).should(never()).verifyAndMarkSucceeded(any(), any());
    then(settlementBatchProcessor).should(never()).markRunFailure(any(), any(), any(), any());
  }

  @Test
  void runFinalSettlementBatchClaimsOnlyPendingOrRetryWaitSettlements() {
    given(crewRepository.findByStatusAndEndAtLessThanEqual(CrewStatus.ACTIVE, NOW))
        .willReturn(List.of());
    given(crewRepository.findClosedWithoutSettlement()).willReturn(List.of());
    given(
            settlementRepository.findByStatusInAndRetryCountLessThanOrderByIdAsc(
                List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT),
                Settlement.MAX_RETRY_COUNT))
        .willReturn(List.of());

    settlementBatchService.runFinalSettlementBatch(NOW, BATCH_RUN_KEY);

    then(settlementRepository)
        .should()
        .findByStatusInAndRetryCountLessThanOrderByIdAsc(
            List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT),
            Settlement.MAX_RETRY_COUNT);
    then(settlementBatchProcessor).should(never()).claimSettlement(any(), any(), any());
  }

  private Crew crew(Long id) {
    Crew crew = org.mockito.Mockito.mock(Crew.class);
    given(crew.getId()).willReturn(id);
    return crew;
  }

  private Settlement settlement(Long id) {
    Settlement settlement = org.mockito.Mockito.mock(Settlement.class);
    given(settlement.getId()).willReturn(id);
    return settlement;
  }
}
