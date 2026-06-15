package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.point.service.PointLedgerService;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementBatchCommandService {

  private static final List<SettlementStatus> CLAIMABLE_STATUSES =
      List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT);

  private final SettlementRepository settlementRepository;
  private final SettlementItemRepository settlementItemRepository;
  private final PointLedgerService pointLedgerService;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean claimSettlement(Long settlementId, String batchRunKey, LocalDateTime startedAt) {
    return settlementRepository.claimRunnable(
            settlementId, batchRunKey, startedAt, CLAIMABLE_STATUSES, Settlement.MAX_RETRY_COUNT)
        == 1;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void refundOneSettlementItem(Long settlementItemId) {
    SettlementItem settlementItem =
        settlementItemRepository
            .findById(settlementItemId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "정산 항목을 찾을 수 없습니다. settlementItemId=" + settlementItemId));
    try {
      pointLedgerService.refundSettlement(settlementItem);
    } catch (RuntimeException exception) {
      throw new SettlementBatchRunFailure(
          SettlementFailureCode.POINT_CREDIT_FAILED,
          "정산 항목 포인트 크레딧 처리에 실패했습니다. settlementItemId=" + settlementItemId,
          exception);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void verifyAndMarkSucceeded(Long settlementId, LocalDateTime finishedAt) {
    Settlement settlement = requireSettlement(settlementId);
    long totalItems = settlementItemRepository.countBySettlementId(settlementId);
    long paidItems =
        settlementItemRepository.countBySettlementIdAndPointHistoryIsNotNull(settlementId);
    if (totalItems == 0 || totalItems != paidItems) {
      throw new SettlementBatchRunFailure(
          SettlementFailureCode.POINT_CREDIT_FAILED,
          "정산 항목이 포인트 이력과 모두 연결되지 않았습니다. settlementId=" + settlementId);
    }
    settlement.markSucceeded(finishedAt);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markRunFailure(
      Long settlementId,
      SettlementFailureCode failureCode,
      String failureMessage,
      LocalDateTime finishedAt) {
    Settlement settlement = requireSettlement(settlementId);
    settlement.markFailedAttempt(failureCode, failureMessage, finishedAt);
  }

  private Settlement requireSettlement(Long settlementId) {
    return settlementRepository
        .findById(settlementId)
        .orElseThrow(
            () -> new IllegalStateException("정산을 찾을 수 없습니다. settlementId=" + settlementId));
  }
}
