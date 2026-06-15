package com.oit.dondok.domain.settlement.entity.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oit.dondok.domain.settlement.entity.SettlementCalculationReason;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SettlementCalculationReasonJsonParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String PARTICIPANT_KEY = "participant_key";
  private static final String RECOGNIZED_SUCCESS_COUNT = "recognized_success_count";
  private static final String SHARE_RATIO = "share_ratio";
  private static final String REMAINDER_POLICY = "remainder_policy";

  private SettlementCalculationReasonJsonParser() {}

  public static SettlementCalculationReason parse(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("정산 계산 사유가 필요합니다.");
    }
    try {
      JsonNode node = OBJECT_MAPPER.readTree(json);
      if (!node.isObject()) {
        throw new IllegalArgumentException("정산 계산 사유는 JSON 객체여야 합니다.");
      }

      Map<String, JsonNode> metadata = parseMetadata(node);

      return new SettlementCalculationReason(
          longValue(node, PARTICIPANT_KEY),
          integerValue(node, RECOGNIZED_SUCCESS_COUNT),
          stringValue(node, SHARE_RATIO),
          stringValue(node, REMAINDER_POLICY),
          metadata);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("정산 계산 사유 파싱에 실패했습니다.", exception);
    }
  }

  public static String toJson(SettlementCalculationReason reason) {
    Objects.requireNonNull(reason, "정산 계산 사유는 필수입니다.");
    try {
      return OBJECT_MAPPER.writeValueAsString(toJsonNode(reason));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("정산 계산 사유 직렬화에 실패했습니다.", e);
    }
  }

  public static JsonNode toJsonNode(SettlementCalculationReason reason) {
    Objects.requireNonNull(reason, "정산 계산 사유는 필수입니다.");
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
    if (reason.metadata() != null) {
      reason
          .metadata()
          .forEach((key, value) -> node.set(key, value == null ? null : value.deepCopy()));
    }
    return node;
  }

  private static Map<String, JsonNode> parseMetadata(JsonNode node) {
    Map<String, JsonNode> metadata = new LinkedHashMap<>();

    node.fieldNames()
        .forEachRemaining(
            key -> {
              if (KNOWN_FIELDS.contains(key)) {
                return;
              }
              JsonNode fieldValue = node.get(key);
              if (fieldValue == null || fieldValue.isMissingNode()) {
                return;
              }
              metadata.put(key, fieldValue.deepCopy());
            });

    return Collections.unmodifiableMap(metadata);
  }

  private static final Set<String> KNOWN_FIELDS =
      Set.of(PARTICIPANT_KEY, RECOGNIZED_SUCCESS_COUNT, SHARE_RATIO, REMAINDER_POLICY);

  private static String stringValue(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static Long longValue(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.canConvertToLong()) {
      return value.longValue();
    }

    try {
      return Long.valueOf(value.asText());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("정산 계산 사유의 participant_key는 숫자여야 합니다.", exception);
    }
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
      throw new IllegalArgumentException("정산 계산 사유 파싱에 실패했습니다.", exception);
    }
  }
}
