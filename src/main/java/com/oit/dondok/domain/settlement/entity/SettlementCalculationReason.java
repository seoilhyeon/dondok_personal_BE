package com.oit.dondok.domain.settlement.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oit.dondok.domain.settlement.service.model.SettlementParticipantResult;
import java.util.Objects;

public record SettlementCalculationReason(JsonNode reason) {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public SettlementCalculationReason {
    Objects.requireNonNull(reason, "reason is required");
    if (!reason.isObject()) {
      throw new IllegalArgumentException("reason must be a JSON object");
    }
  }

  public static SettlementCalculationReason of(SettlementParticipantResult result) {
    ObjectNode node = OBJECT_MAPPER.createObjectNode();
    node.put("participant_key", result.participantKey());
    node.put("recognized_success_count", result.recognizedSuccessCount());
    node.put("share_ratio", result.shareRatio().toPlainString());
    node.put("remainder_policy", RemainderPolicy.HOST_REMAINDER.name());
    return new SettlementCalculationReason(node);
  }

  public static SettlementCalculationReason parse(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("reason is required");
    }
    try {
      JsonNode node = OBJECT_MAPPER.readTree(json);
      return new SettlementCalculationReason(node);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(
          "failed to parse settlement calculation reason", exception);
    }
  }

  public String toJson() {
    try {
      return OBJECT_MAPPER.writeValueAsString(reason);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("failed to serialize settlement calculation reason", e);
    }
  }

  public JsonNode toJsonNode() {
    return reason;
  }
}
