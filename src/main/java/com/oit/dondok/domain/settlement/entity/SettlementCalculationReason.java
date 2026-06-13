package com.oit.dondok.domain.settlement.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.oit.dondok.domain.settlement.entity.parser.SettlementCalculationReasonJsonParser;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantResult;
import java.util.LinkedHashMap;
import java.util.Map;

public record SettlementCalculationReason(
    String participantKey,
    Integer recognizedSuccessCount,
    String shareRatio,
    String remainderPolicy,
    Map<String, JsonNode> metadata) {

  public SettlementCalculationReason {
    if (metadata == null) {
      metadata = Map.of();
    } else {
      LinkedHashMap<String, JsonNode> copied = new LinkedHashMap<>();
      metadata.forEach((key, value) -> copied.put(key, value == null ? null : value.deepCopy()));
      metadata = Map.copyOf(copied);
    }
  }

  public static SettlementCalculationReason of(SettlementParticipantResult result) {
    if (result == null) {
      throw new IllegalArgumentException("result is required");
    }

    return new SettlementCalculationReason(
        result.participantKey(),
        result.recognizedSuccessCount(),
        result.shareRatio().toPlainString(),
        RemainderPolicy.HOST_REMAINDER.name(),
        Map.of());
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
