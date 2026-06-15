package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementBatchProcessor {

  private final SettlementCandidatePreparationService settlementCandidatePreparationService;
  private final SettlementItemComputationService settlementItemComputationService;
  private final SettlementBatchCommandService settlementBatchCommandService;

  public Optional<Long> prepareCompletedCrewSettlementCandidate(
      Long crewId, String batchRunKey, LocalDateTime now) {
    return settlementCandidatePreparationService.prepareCompletedCrewSettlementCandidate(
        crewId, batchRunKey, now);
  }

  public boolean claimSettlement(Long settlementId, String batchRunKey, LocalDateTime startedAt) {
    return settlementBatchCommandService.claimSettlement(settlementId, batchRunKey, startedAt);
  }

  public List<Long> ensureSettlementItems(Long settlementId) {
    return settlementItemComputationService.ensureSettlementItems(settlementId);
  }

  public void refundOneSettlementItem(Long settlementItemId) {
    settlementBatchCommandService.refundOneSettlementItem(settlementItemId);
  }

  public void verifyAndMarkSucceeded(Long settlementId, LocalDateTime finishedAt) {
    settlementBatchCommandService.verifyAndMarkSucceeded(settlementId, finishedAt);
  }

  public void markRunFailure(
      Long settlementId,
      SettlementFailureCode failureCode,
      String failureMessage,
      LocalDateTime finishedAt) {
    settlementBatchCommandService.markRunFailure(
        settlementId, failureCode, failureMessage, finishedAt);
  }
}
