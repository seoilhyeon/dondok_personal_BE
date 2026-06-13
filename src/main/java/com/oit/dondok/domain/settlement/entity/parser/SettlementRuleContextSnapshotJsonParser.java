package com.oit.dondok.domain.settlement.entity.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.settlement.entity.SettlementRuleContextSnapshot;
import java.util.Objects;

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
        throw new IllegalArgumentException("failed to parse settlement rule context snapshot");
      }

      DailySettlementType dailySettlementType =
          parseEnum(
              valueOrNull(node, DAILY_SETTLEMENT_TYPE_KEY, "dailySettlementType"),
              "dailySettlementType",
              DailySettlementType.class);
      MissionFrequencyType frequencyType =
          parseEnum(
              valueOrNull(node, FREQUENCY_TYPE_KEY, "frequencyType"),
              "frequencyType",
              MissionFrequencyType.class);

      return new SettlementRuleContextSnapshot(dailySettlementType, frequencyType);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("failed to parse settlement rule context snapshot", e);
    }
  }

  public static String toJson(SettlementRuleContextSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot must not be null");

    try {
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put(DAILY_SETTLEMENT_TYPE_KEY, snapshot.dailySettlementType().name());
      node.put(FREQUENCY_TYPE_KEY, snapshot.frequencyType().name());
      return OBJECT_MAPPER.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("failed to serialize settlement rule context snapshot", e);
    }
  }

  private static String valueOrNull(JsonNode node, String firstKey, String secondKey) {
    JsonNode value = node.get(firstKey);
    if (value == null || value.isNull()) {
      value = node.get(secondKey);
    }
    return value == null || value.isNull() ? null : value.asText();
  }

  private static <E extends Enum<E>> E parseEnum(
      String value, String fieldName, Class<E> enumType) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("%s is required".formatted(fieldName));
    }
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid %s: %s".formatted(fieldName, value), e);
    }
  }
}
