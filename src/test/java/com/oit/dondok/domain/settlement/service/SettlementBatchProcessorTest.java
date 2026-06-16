package com.oit.dondok.domain.settlement.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementBatchProcessorTest {

  private static final Long CREW_ID = 1L;
  private static final Long SETTLEMENT_ID = 10L;
  private static final Long SETTLEMENT_ITEM_ID = 100L;
  private static final String BATCH_RUN_KEY = "batch-001";
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 0, 0);

  @Mock private SettlementCandidatePreparationService candidatePreparationService;
  @Mock private SettlementItemComputationService itemComputationService;
  @Mock private SettlementBatchCommandService commandService;

  @InjectMocks private SettlementBatchProcessor processor;

  @Test
  void prepareCompletedCrewSettlementCandidateDelegatesToPreparationService() {
    given(
            candidatePreparationService.prepareCompletedCrewSettlementCandidate(
                CREW_ID, DailySettlementType.A, BATCH_RUN_KEY, NOW))
        .willReturn(Optional.of(SETTLEMENT_ID));

    processor.prepareCompletedCrewSettlementCandidate(
        CREW_ID, DailySettlementType.A, BATCH_RUN_KEY, NOW);

    then(candidatePreparationService)
        .should()
        .prepareCompletedCrewSettlementCandidate(
            CREW_ID, DailySettlementType.A, BATCH_RUN_KEY, NOW);
  }

  @Test
  void ensureSettlementItemsDelegatesToItemComputationService() {
    given(itemComputationService.ensureSettlementItems(SETTLEMENT_ID))
        .willReturn(List.of(SETTLEMENT_ITEM_ID));

    processor.ensureSettlementItems(SETTLEMENT_ID);

    then(itemComputationService).should().ensureSettlementItems(SETTLEMENT_ID);
  }

  @Test
  void claimSettlementDelegatesToCommandService() {
    given(commandService.claimSettlement(SETTLEMENT_ID, BATCH_RUN_KEY, NOW)).willReturn(true);

    processor.claimSettlement(SETTLEMENT_ID, BATCH_RUN_KEY, NOW);

    then(commandService).should().claimSettlement(SETTLEMENT_ID, BATCH_RUN_KEY, NOW);
  }

  @Test
  void refundAndCompletionFlowDelegatesToCommandService() {
    processor.refundOneSettlementItem(SETTLEMENT_ITEM_ID);
    processor.verifyAndMarkSucceeded(SETTLEMENT_ID, NOW);
    processor.markRunFailure(SETTLEMENT_ID, SettlementFailureCode.UNKNOWN, "fail", NOW);

    then(commandService).should().refundOneSettlementItem(SETTLEMENT_ITEM_ID);
    then(commandService).should().verifyAndMarkSucceeded(SETTLEMENT_ID, NOW);
    then(commandService)
        .should()
        .markRunFailure(SETTLEMENT_ID, SettlementFailureCode.UNKNOWN, "fail", NOW);
  }
}
