package com.oit.dondok.domain.settlement.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.oit.dondok.domain.settlement.entity.parser.SettlementCalculationReasonJsonParser;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantResult;

public record SettlementCalculationReason(
    String participantKey,
    Integer recognizedSuccessCount,
    String shareRatio,
    String remainderPolicy) {

  public static SettlementCalculationReason of(SettlementParticipantResult result) {
    if (result == null) {
      throw new IllegalArgumentException("result is required");
    }

    return new SettlementCalculationReason(
        result.participantKey(),
        result.recognizedSuccessCount(),
        result.shareRatio().toPlainString(),
        RemainderPolicy.HOST_REMAINDER.name());
  }

  public static SettlementCalculationReason parse(String json) {
    return SettlementCalculationReasonJsonParser.parse(json);
  }

  public String toJson() {
    return SettlementCalculationReasonJsonParser.toJson(this);
  }

  public JsonNode toJsonNode() {
    return SettlementCalculationReasonJsonParser.toJsonNode(this);
  }
}
