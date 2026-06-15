package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationInput;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationResult;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantInput;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

  private final SettlementCalculatorService settlementCalculator =
      new SettlementCalculatorService();

  @Test
  void equalSplit() {
    SettlementCalculationInput input =
        createInput(
            List.of(
                participant(1L, true, 100_000L, 10, 10, 10, 0),
                participant(2L, false, 100_000L, 10, 10, 10, 0)));
    SettlementCalculationResult result = settlementCalculator.calculate(input);

    assertThat(result.totalParticipants()).isEqualTo(2);
    assertThat(result.totalLockedAmount()).isEqualTo(200_000L);
    assertThat(result.totalRecognizedSuccess()).isEqualTo(20);
    assertThat(result.totalBaseRefundAmount()).isEqualTo(200_000L);
    assertThat(result.totalRemainderAmount()).isZero();
    assertThat(result.remainderPolicy()).isEqualTo(RemainderPolicy.HOST_REMAINDER);
    assertThat(result.participants().get(0).shareRatio()).isEqualTo(new BigDecimal("0.500000"));
    assertThat(result.participants().get(1).shareRatio()).isEqualTo(new BigDecimal("0.500000"));
    assertThat(result.participants().get(0).baseRefundAmount()).isEqualTo(100_000L);
    assertThat(result.participants().get(1).baseRefundAmount()).isEqualTo(100_000L);
    assertThat(result.participants().get(0).refundAmount()).isEqualTo(100_000L);
    assertThat(result.participants().get(1).refundAmount()).isEqualTo(100_000L);
    assertThat(result.participants().get(0).remainderBonusAmount()).isZero();
    assertThat(result.participants().get(1).remainderBonusAmount()).isZero();
  }

  @Test
  void unequalSplitUsesFloorScale6ShareRatio() {
    SettlementCalculationInput input =
        createInput(
            List.of(
                participant(1L, false, 300_000L, 1, 1, 1, 0),
                participant(2L, true, 1L, 2, 2, 2, 0)));
    SettlementCalculationResult result = settlementCalculator.calculate(input);

    assertThat(result.totalLockedAmount()).isEqualTo(300_001L);
    assertThat(result.totalRecognizedSuccess()).isEqualTo(3);
    assertThat(result.participants().get(0).shareRatio()).isEqualTo(new BigDecimal("0.333333"));
    assertThat(result.participants().get(1).shareRatio()).isEqualTo(new BigDecimal("0.666666"));
    assertThat(result.participants().get(0).baseRefundAmount()).isEqualTo(100_000L);
    assertThat(result.participants().get(1).baseRefundAmount()).isEqualTo(200_000L);
    assertThat(result.totalBaseRefundAmount()).isEqualTo(300_000L);
    assertThat(result.totalRemainderAmount()).isEqualTo(1L);
  }

  @Test
  void hostRemainderAllocatesToHostOnly() {
    SettlementCalculationInput input =
        createInput(
            List.of(
                participant(3L, true, 300_000L, 1, 1, 1, 0),
                participant(4L, false, 1L, 2, 2, 2, 0)));
    SettlementCalculationResult result = settlementCalculator.calculate(input);

    assertThat(result.participants().get(0).remainderBonusAmount()).isEqualTo(1L);
    assertThat(result.participants().get(1).remainderBonusAmount()).isZero();
    assertThat(result.participants().get(0).refundAmount()).isEqualTo(100_001L);
    assertThat(result.participants().get(1).refundAmount()).isEqualTo(200_000L);
  }

  @Test
  void allFailCaseRefundsPrincipalDeposit() {
    SettlementCalculationInput input =
        createInput(
            List.of(
                participant(1L, true, 100_000L, 0, 0, 0, 0),
                participant(2L, false, 120_000L, 0, 0, 0, 0),
                participant(3L, false, 80_000L, 0, 0, 0, 0)));
    SettlementCalculationResult result = settlementCalculator.calculate(input);

    assertThat(result.totalRecognizedSuccess()).isEqualTo(0);
    assertThat(result.totalBaseRefundAmount()).isEqualTo(300_000L);
    assertThat(result.totalRemainderAmount()).isZero();
    assertThat(result.participants())
        .allSatisfy(
            participantResult -> {
              assertThat(participantResult.shareRatio()).isEqualTo(new BigDecimal("0.000000"));
              assertThat(participantResult.remainderBonusAmount()).isZero();
              assertThat(participantResult.refundAmount())
                  .isEqualTo(participantResult.depositAmount());
            });
  }

  @Test
  void passThroughRawRecognizedAndExcludedCounts() {
    SettlementCalculationInput input =
        createInput(
            List.of(
                participant(1L, true, 100_000L, 5, 3, 4, 2),
                participant(2L, false, 50_000L, 2, 2, 1, 0)));
    SettlementCalculationResult result = settlementCalculator.calculate(input);

    assertThat(result.participants())
        .satisfiesExactly(
            first -> {
              assertThat(first.participantKey()).isEqualTo(1L);
              assertThat(first.successCountRaw()).isEqualTo(5);
              assertThat(first.recognizedSuccessCount()).isEqualTo(3);
              assertThat(first.recognizedDatesCount()).isEqualTo(4);
              assertThat(first.excludedSuccessCount()).isEqualTo(2);
            },
            second -> {
              assertThat(second.participantKey()).isEqualTo(2L);
              assertThat(second.successCountRaw()).isEqualTo(2);
              assertThat(second.recognizedSuccessCount()).isEqualTo(2);
              assertThat(second.recognizedDatesCount()).isEqualTo(1);
              assertThat(second.excludedSuccessCount()).isEqualTo(0);
            });
  }

  @Test
  void rejectsOverflowWhenTotalLockedAmountExceedsLongRange() {
    SettlementCalculationInput input =
        createInput(
            List.of(
                participant(3L, true, Long.MAX_VALUE, 1, 1, 1, 0),
                participant(4L, false, 1L, 1, 1, 1, 0)));

    assertThatThrownBy(() -> settlementCalculator.calculate(input))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void rejectsOverflowWhenTotalRecognizedSuccessExceedsIntRange() {
    SettlementCalculationInput input =
        createInput(
            List.of(
                participant(3L, true, 100L, Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 0),
                participant(4L, false, 100L, 1, 1, 1, 0)));

    assertThatThrownBy(() -> settlementCalculator.calculate(input))
        .isInstanceOfSatisfying(
            CustomException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  private SettlementCalculationInput createInput(List<SettlementParticipantInput> participants) {
    return new SettlementCalculationInput(RemainderPolicy.HOST_REMAINDER, participants);
  }

  private SettlementParticipantInput participant(
      long participantKey,
      boolean host,
      long depositAmount,
      int successCountRaw,
      int recognizedSuccessCount,
      int recognizedDatesCount,
      int excludedSuccessCount) {
    return new SettlementParticipantInput(
        participantKey,
        host,
        depositAmount,
        successCountRaw,
        recognizedSuccessCount,
        recognizedDatesCount,
        excludedSuccessCount);
  }
}
