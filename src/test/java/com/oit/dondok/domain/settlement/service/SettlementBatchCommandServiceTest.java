package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.point.service.PointLedgerService;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
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
class SettlementBatchCommandServiceTest {

  private static final Long SETTLEMENT_ID = 10L;
  private static final Long SETTLEMENT_ITEM_ID = 100L;
  private static final String BATCH_RUN_KEY = "batch-001";
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 0, 0);

  @Mock private SettlementRepository settlementRepository;
  @Mock private SettlementItemRepository settlementItemRepository;
  @Mock private PointLedgerService pointLedgerService;

  @InjectMocks private SettlementBatchCommandService service;

  @Test
  void claimSettlementUsesConditionalClaimWithRetryLimit() {
    given(
            settlementRepository.claimRunnable(
                SETTLEMENT_ID,
                BATCH_RUN_KEY,
                NOW,
                List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT),
                Settlement.MAX_RETRY_COUNT))
        .willReturn(1);

    boolean claimed = service.claimSettlement(SETTLEMENT_ID, BATCH_RUN_KEY, NOW);

    org.assertj.core.api.Assertions.assertThat(claimed).isTrue();
  }

  @Test
  void claimSettlementReturnsFalseForRaceLoser() {
    given(
            settlementRepository.claimRunnable(
                SETTLEMENT_ID,
                BATCH_RUN_KEY,
                NOW,
                List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT),
                Settlement.MAX_RETRY_COUNT))
        .willReturn(0);

    boolean claimed = service.claimSettlement(SETTLEMENT_ID, BATCH_RUN_KEY, NOW);

    org.assertj.core.api.Assertions.assertThat(claimed).isFalse();
  }

  @Test
  void refundOneSettlementItemMapsLedgerFailureToPointCreditFailure() {
    SettlementItem settlementItem = org.mockito.Mockito.mock(SettlementItem.class);
    given(settlementItemRepository.findById(SETTLEMENT_ITEM_ID))
        .willReturn(Optional.of(settlementItem));
    given(pointLedgerService.refundSettlement(settlementItem))
        .willThrow(new IllegalStateException("원장 반영 실패"));

    assertThatThrownBy(() -> service.refundOneSettlementItem(SETTLEMENT_ITEM_ID))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.POINT_CREDIT_FAILED);
  }

  @Test
  void verifyAndMarkSucceededRequiresAllItemsLinked() {
    Settlement settlement = org.mockito.Mockito.mock(Settlement.class);
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(settlementItemRepository.countBySettlementId(SETTLEMENT_ID)).willReturn(2L);
    given(settlementItemRepository.countBySettlementIdAndPointHistoryIsNotNull(SETTLEMENT_ID))
        .willReturn(1L);

    assertThatThrownBy(() -> service.verifyAndMarkSucceeded(SETTLEMENT_ID, NOW))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.POINT_CREDIT_FAILED);
  }

  @Test
  void markRunFailureDelegatesToSettlementTransition() {
    Settlement settlement = org.mockito.Mockito.mock(Settlement.class);
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));

    service.markRunFailure(
        SETTLEMENT_ID, SettlementFailureCode.CALCULATION_FAILED, "calculation failed", NOW);

    then(settlement)
        .should()
        .markFailedAttempt(SettlementFailureCode.CALCULATION_FAILED, "calculation failed", NOW);
  }
}
