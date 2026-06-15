package com.oit.dondok.domain.settlement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import org.junit.jupiter.api.Test;

class SettlementRuleContextSnapshotTest {

  @Test
  void parseShouldReturnEnumValuesForValidPayload() {
    SettlementRuleContextSnapshot snapshot =
        SettlementRuleContextSnapshot.parse(
            "{\"daily_settlement_type\":\"B\",\"frequency_type\":\"DAILY\"}");

    assertThat(snapshot.dailySettlementType()).isEqualTo(DailySettlementType.B);
    assertThat(snapshot.frequencyType()).isEqualTo(MissionFrequencyType.DAILY);
  }

  @Test
  void parseShouldRejectUnknownEnumValue() {
    assertThatThrownBy(
            () ->
                SettlementRuleContextSnapshot.parse(
                    "{\"daily_settlement_type\":\"B\",\"frequency_type\":\"WEEK\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("frequencyType");
  }

  @Test
  void parseShouldRejectLegacyDailySettlementTypeValue() {
    assertThatThrownBy(
            () ->
                SettlementRuleContextSnapshot.parse(
                    "{\"daily_settlement_type\":\"WEEKLY\",\"frequency_type\":\"DAILY\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dailySettlementType");
  }

  @Test
  void parseShouldRejectMissingValuesWithClearException() {
    assertThatThrownBy(
            () -> SettlementRuleContextSnapshot.parse("{\"daily_settlement_type\":\"B\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("frequencyType");
  }

  @Test
  void parseFromShouldRequireEnums() {
    SettlementRuleContextSnapshot snapshot =
        SettlementRuleContextSnapshot.from(
            DailySettlementType.B, MissionFrequencyType.SPECIFIC_DAYS);

    assertThat(snapshot.toJson()).contains("\"daily_settlement_type\":\"B\"");
    assertThat(snapshot.toJson()).contains("\"frequency_type\":\"SPECIFIC_DAYS\"");
  }

  @Test
  void shouldSerializeAndParseRuleContextSnapshot() {
    SettlementRuleContextSnapshot snapshot =
        SettlementRuleContextSnapshot.from(DailySettlementType.B, MissionFrequencyType.DAILY);

    String json = snapshot.toJson();
    SettlementRuleContextSnapshot parsed = SettlementRuleContextSnapshot.parse(json);

    assertThat(parsed.dailySettlementType()).isEqualTo(DailySettlementType.B);
    assertThat(parsed.frequencyType()).isEqualTo(MissionFrequencyType.DAILY);
  }
}
