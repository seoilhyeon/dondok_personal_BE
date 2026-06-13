package com.oit.dondok.domain.settlement.entity;

import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.settlement.entity.parser.SettlementRuleContextSnapshotJsonParser;
import java.util.Objects;

public record SettlementRuleContextSnapshot(String dailySettlementType, String frequencyType) {

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
    return SettlementRuleContextSnapshotJsonParser.parse(json);
  }

  public String toJson() {
    return SettlementRuleContextSnapshotJsonParser.toJson(this);
  }
}
