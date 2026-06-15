package com.oit.dondok.domain.settlement.service.model;

import java.math.BigDecimal;
import java.util.Objects;

public record SettlementParticipantResult(
    long participantKey,
    boolean host,
    long depositAmount,
    int successCountRaw,
    int recognizedSuccessCount,
    int recognizedDatesCount,
    int excludedSuccessCount,
    BigDecimal shareRatio,
    long baseRefundAmount,
    long remainderBonusAmount,
    long refundAmount) {

  public static Builder builder(SettlementParticipantInput input) {
    return new Builder(input);
  }

  public static Builder builder(SettlementParticipantResult result) {
    return new Builder(result);
  }

  public static class Builder {
    private final long participantKey;
    private final boolean host;
    private final long depositAmount;
    private final int successCountRaw;
    private final int recognizedSuccessCount;
    private final int recognizedDatesCount;
    private final int excludedSuccessCount;
    private BigDecimal shareRatio;
    private long baseRefundAmount;
    private long remainderBonusAmount;
    private long refundAmount;
    private boolean shareRatioSet;
    private boolean baseRefundAmountSet;
    private boolean remainderBonusAmountSet;
    private boolean refundAmountSet;

    private Builder(SettlementParticipantInput input) {
      this.participantKey = input.participantKey();
      this.host = input.host();
      this.depositAmount = input.depositAmount();
      this.successCountRaw = input.successCountRaw();
      this.recognizedSuccessCount = input.recognizedSuccessCount();
      this.recognizedDatesCount = input.recognizedDatesCount();
      this.excludedSuccessCount = input.excludedSuccessCount();
    }

    private Builder(SettlementParticipantResult result) {
      this.participantKey = result.participantKey();
      this.host = result.host();
      this.depositAmount = result.depositAmount();
      this.successCountRaw = result.successCountRaw();
      this.recognizedSuccessCount = result.recognizedSuccessCount();
      this.recognizedDatesCount = result.recognizedDatesCount();
      this.excludedSuccessCount = result.excludedSuccessCount();
      this.shareRatio = result.shareRatio();
      this.baseRefundAmount = result.baseRefundAmount();
      this.remainderBonusAmount = result.remainderBonusAmount();
      this.refundAmount = result.refundAmount();
      this.shareRatioSet = true;
      this.baseRefundAmountSet = true;
      this.remainderBonusAmountSet = true;
      this.refundAmountSet = true;
    }

    public Builder shareRatio(BigDecimal shareRatio) {
      this.shareRatio = Objects.requireNonNull(shareRatio);
      this.shareRatioSet = true;
      return this;
    }

    public Builder baseRefundAmount(long baseRefundAmount) {
      this.baseRefundAmount = baseRefundAmount;
      this.baseRefundAmountSet = true;
      return this;
    }

    public Builder remainderBonusAmount(long remainderBonusAmount) {
      this.remainderBonusAmount = remainderBonusAmount;
      this.remainderBonusAmountSet = true;
      return this;
    }

    public Builder refundAmount(long refundAmount) {
      this.refundAmount = refundAmount;
      this.refundAmountSet = true;
      return this;
    }

    public SettlementParticipantResult build() {
      if (!shareRatioSet) {
        throw new IllegalStateException("shareRatio가 필요합니다.");
      }
      if (!baseRefundAmountSet) {
        throw new IllegalStateException("baseRefundAmount가 필요합니다.");
      }
      if (!remainderBonusAmountSet) {
        throw new IllegalStateException("remainderBonusAmount가 필요합니다.");
      }
      if (!refundAmountSet) {
        throw new IllegalStateException("refundAmount가 필요합니다.");
      }

      return new SettlementParticipantResult(
          participantKey,
          host,
          depositAmount,
          successCountRaw,
          recognizedSuccessCount,
          recognizedDatesCount,
          excludedSuccessCount,
          shareRatio,
          baseRefundAmount,
          remainderBonusAmount,
          refundAmount);
    }
  }
}
