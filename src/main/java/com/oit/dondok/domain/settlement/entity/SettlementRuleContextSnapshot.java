package com.oit.dondok.domain.settlement.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import java.util.Objects;

public record SettlementRuleContextSnapshot(
    @JsonProperty("daily_settlement_type") String dailySettlementType,
    @JsonProperty("frequency_type") String frequencyType) {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public SettlementRuleContextSnapshot {
    Objects.requireNonNull(dailySettlementType, "dailySettlementType is required");
    Objects.requireNonNull(frequencyType, "frequencyType is required");
  }

  public static SettlementRuleContextSnapshot from(
      DailySettlementType dailySettlementType, MissionFrequencyType frequencyType) {
    Objects.requireNonNull(dailySettlementType, "dailySettlementType");
    Objects.requireNonNull(frequencyType, "frequencyType");
    return new SettlementRuleContextSnapshot(dailySettlementType.name(), frequencyType.name());
  }

  public static SettlementRuleContextSnapshot parse(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("rule context snapshot json is required");
    }
    try {
      return OBJECT_MAPPER.readValue(json, SettlementRuleContextSnapshot.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to parse settlement rule context snapshot", e);
    }
  }

  public String toJson() {
    try {
      return OBJECT_MAPPER.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize settlement rule context snapshot", e);
    }
  }
}
