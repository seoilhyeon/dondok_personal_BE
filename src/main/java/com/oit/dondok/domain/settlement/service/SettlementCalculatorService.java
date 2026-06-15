package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.settlement.entity.RemainderPolicy;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationInput;
import com.oit.dondok.domain.settlement.service.model.SettlementCalculationResult;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantInput;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantResult;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementCalculatorService {

  private static final int SHARE_RATIO_SCALE = 6;

  @Transactional
  public SettlementCalculationResult calculate(SettlementCalculationInput input) {
    SettlementCalculationValidator.validate(input);

    try {
      long totalLockedAmount = sumDepositAmounts(input.participants());
      int totalRecognizedSuccess = sumRecognizedSuccess(input.participants());

      if (totalRecognizedSuccess == 0) {
        return calculateAllFailResult(input, totalLockedAmount);
      }

      long totalBaseRefundAmount = 0L;
      List<SettlementParticipantResult> participantResults =
          new ArrayList<>(input.participants().size());

      BigDecimal totalLockedDecimal = BigDecimal.valueOf(totalLockedAmount);
      BigDecimal totalRecognizedDecimal = BigDecimal.valueOf(totalRecognizedSuccess);

      for (SettlementParticipantInput participant : input.participants()) {
        BigDecimal shareRatio =
            divideAsShareRatio(
                BigDecimal.valueOf(participant.recognizedSuccessCount()), totalRecognizedDecimal);
        long baseRefundAmount =
            totalLockedDecimal
                .multiply(shareRatio)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();

        totalBaseRefundAmount = Math.addExact(totalBaseRefundAmount, baseRefundAmount);

        participantResults.add(
            SettlementParticipantResult.builder(participant)
                .shareRatio(shareRatio)
                .baseRefundAmount(baseRefundAmount)
                .remainderBonusAmount(0L)
                .refundAmount(baseRefundAmount)
                .build());
      }

      long totalRemainderAmount = Math.subtractExact(totalLockedAmount, totalBaseRefundAmount);
      if (input.remainderPolicy() == RemainderPolicy.HOST_REMAINDER) {
        participantResults =
            allocateHostRemainder(participantResults, input.participants(), totalRemainderAmount);
      }

      return new SettlementCalculationResult(
          input.participants().size(),
          totalLockedAmount,
          totalRecognizedSuccess,
          totalBaseRefundAmount,
          totalRemainderAmount,
          input.remainderPolicy(),
          participantResults);
    } catch (ArithmeticException exception) {
      throw new CustomException(
          GlobalErrorCode.INVALID_INPUT, new RuntimeException("정산 계산 중 정수 범위를 초과했습니다.", exception));
    }
  }

  private SettlementCalculationResult calculateAllFailResult(
      SettlementCalculationInput input, long totalLockedAmount) {
    List<SettlementParticipantResult> participantResults =
        input.participants().stream()
            .map(
                participant ->
                    SettlementParticipantResult.builder(participant)
                        .shareRatio(BigDecimal.ZERO.setScale(SHARE_RATIO_SCALE))
                        .baseRefundAmount(participant.depositAmount())
                        .remainderBonusAmount(0L)
                        .refundAmount(participant.depositAmount())
                        .build())
            .toList();

    return new SettlementCalculationResult(
        input.participants().size(),
        totalLockedAmount,
        0,
        totalLockedAmount,
        0L,
        input.remainderPolicy(),
        participantResults);
  }

  private List<SettlementParticipantResult> allocateHostRemainder(
      List<SettlementParticipantResult> results,
      List<SettlementParticipantInput> participants,
      long totalRemainderAmount) {
    if (totalRemainderAmount == 0L) {
      return results;
    }

    for (int i = 0; i < participants.size(); i++) {
      SettlementParticipantInput participant = participants.get(i);
      if (participant.host()) {
        long refundAmount = Math.addExact(results.get(i).refundAmount(), totalRemainderAmount);

        List<SettlementParticipantResult> adjusted = new ArrayList<>(results);
        adjusted.set(
            i,
            SettlementParticipantResult.builder(results.get(i))
                .remainderBonusAmount(totalRemainderAmount)
                .refundAmount(refundAmount)
                .build());
        return adjusted;
      }
    }

    throw new IllegalStateException("HOST_REMAINDER 정책에서 HOST 참여자를 찾을 수 없습니다.");
  }

  private BigDecimal divideAsShareRatio(BigDecimal numerator, BigDecimal denominator) {
    return numerator.divide(denominator, SHARE_RATIO_SCALE, RoundingMode.FLOOR);
  }

  private int sumRecognizedSuccess(List<SettlementParticipantInput> participants) {
    int totalRecognizedSuccess = 0;
    for (SettlementParticipantInput participant : participants) {
      totalRecognizedSuccess =
          Math.addExact(totalRecognizedSuccess, participant.recognizedSuccessCount());
    }
    return totalRecognizedSuccess;
  }

  private long sumDepositAmounts(List<SettlementParticipantInput> participants) {
    long totalLockedAmount = 0L;
    for (SettlementParticipantInput participant : participants) {
      totalLockedAmount = Math.addExact(totalLockedAmount, participant.depositAmount());
    }
    return totalLockedAmount;
  }
}
