package com.oit.dondok.domain.settlement.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.settlement.entity.ParticipantStatusSnapshot;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import java.math.BigDecimal;
import java.math.RoundingMode;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SettlementItemDetailResponse(
    Long settlementItemId,
    Long crewParticipantId,
    String nickname,
    @JsonProperty("is_me") boolean isMe,
    ParticipantStatusSnapshot participantStatusSnapshot,
    Long depositAmount,
    Integer successCountRaw,
    Integer recognizedSuccessCount,
    Integer recognizedDatesCount,
    Integer excludedSuccessCount,
    String shareRatio,
    Integer rank,
    Long baseRefundAmount,
    Long remainderBonusAmount,
    Long refundAmount,
    Long pointHistoryId,
    JsonNode calculationReason) {

  public static SettlementItemDetailResponse from(
      SettlementItem item,
      JsonNode calculationReason,
      String nickname,
      Integer rank,
      boolean isMe) {
    return new SettlementItemDetailResponse(
        item.getId(),
        item.getCrewParticipant().getId(),
        nickname,
        isMe,
        item.getParticipantStatusSnapshot(),
        item.getDepositAmount(),
        item.getSuccessCountRaw(),
        item.getRecognizedSuccessCount(),
        item.getRecognizedDatesCount(),
        item.getExcludedSuccessCount(),
        formatShareRatio(item.getShareRatio()),
        rank,
        item.getBaseRefundAmount(),
        item.getRemainderBonusAmount(),
        item.getRefundAmount(),
        item.getPointHistory() == null ? null : item.getPointHistory().getId(),
        calculationReason);
  }

  private static String formatShareRatio(BigDecimal shareRatio) {
    return shareRatio.setScale(6, RoundingMode.FLOOR).toPlainString();
  }
}
