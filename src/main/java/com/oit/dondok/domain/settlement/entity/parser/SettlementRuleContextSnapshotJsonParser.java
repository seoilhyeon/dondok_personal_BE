package com.oit.dondok.domain.settlement.entity.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oit.dondok.domain.settlement.entity.SettlementRuleContextSnapshot;

public final class SettlementRuleContextSnapshotJsonParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String DAILY_SETTLEMENT_TYPE_KEY = "daily_settlement_type";
  private static final String FREQUENCY_TYPE_KEY = "frequency_type";

  private SettlementRuleContextSnapshotJsonParser() {}

  public static SettlementRuleContextSnapshot parse(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("rule context snapshot json is required");
    }

    try {
      JsonNode node = OBJECT_MAPPER.readTree(json);
      if (!node.isObject()) {
        throw new IllegalStateException("failed to parse settlement rule context snapshot");
      }

      return new SettlementRuleContextSnapshot(
          valueOrNull(node, DAILY_SETTLEMENT_TYPE_KEY, "dailySettlementType"),
          valueOrNull(node, FREQUENCY_TYPE_KEY, "frequencyType"));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to parse settlement rule context snapshot", e);
    }
  }

  public static String toJson(SettlementRuleContextSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }

    try {
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put(DAILY_SETTLEMENT_TYPE_KEY, snapshot.dailySettlementType());
      node.put(FREQUENCY_TYPE_KEY, snapshot.frequencyType());
      return OBJECT_MAPPER.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize settlement rule context snapshot", e);
    }
  }

  private static String valueOrNull(JsonNode node, String snakeCaseKey, String camelCaseKey) {
    JsonNode value = node.get(snakeCaseKey);
    if (value == null || value.isNull()) {
      value = node.get(camelCaseKey);
    }
    return value == null || value.isNull() ? null : value.asText();
  }
}
