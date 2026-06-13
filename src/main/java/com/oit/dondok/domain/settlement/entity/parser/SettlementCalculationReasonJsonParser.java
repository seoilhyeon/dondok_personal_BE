package com.oit.dondok.domain.settlement.entity.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oit.dondok.domain.settlement.entity.SettlementCalculationReason;
import java.util.Objects;

public final class SettlementCalculationReasonJsonParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String PARTICIPANT_KEY = "participant_key";
  private static final String RECOGNIZED_SUCCESS_COUNT = "recognized_success_count";
  private static final String SHARE_RATIO = "share_ratio";
  private static final String REMAINDER_POLICY = "remainder_policy";

  private SettlementCalculationReasonJsonParser() {}

  public static SettlementCalculationReason parse(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("settlement calculation reason is required");
    }
    try {
      JsonNode node = OBJECT_MAPPER.readTree(json);
      if (!node.isObject()) {
        throw new IllegalArgumentException("settlement calculation reason must be a JSON object");
      }

      return new SettlementCalculationReason(
          stringValue(node, PARTICIPANT_KEY),
          integerValue(node, RECOGNIZED_SUCCESS_COUNT),
          stringValue(node, SHARE_RATIO),
          stringValue(node, REMAINDER_POLICY));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(
          "failed to parse settlement calculation reason", exception);
    }
  }

  public static String toJson(SettlementCalculationReason reason) {
    Objects.requireNonNull(reason, "reason is required");
    try {
      return OBJECT_MAPPER.writeValueAsString(toJsonNode(reason));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("failed to serialize settlement calculation reason", e);
    }
  }

  public static JsonNode toJsonNode(SettlementCalculationReason reason) {
    ObjectNode node = OBJECT_MAPPER.createObjectNode();
    if (reason.participantKey() != null) {
      node.put(PARTICIPANT_KEY, reason.participantKey());
    }
    if (reason.recognizedSuccessCount() != null) {
      node.put(RECOGNIZED_SUCCESS_COUNT, reason.recognizedSuccessCount());
    }
    if (reason.shareRatio() != null) {
      node.put(SHARE_RATIO, reason.shareRatio());
    }
    if (reason.remainderPolicy() != null) {
      node.put(REMAINDER_POLICY, reason.remainderPolicy());
    }
    return node;
  }

  private static String stringValue(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static Integer integerValue(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.canConvertToInt()) {
      return value.intValue();
    }
    try {
      return Integer.valueOf(value.asText());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "failed to parse settlement calculation reason", exception);
    }
  }
}
