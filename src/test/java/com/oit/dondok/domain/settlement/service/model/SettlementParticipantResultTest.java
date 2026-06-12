package com.oit.dondok.domain.settlement.service.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SettlementParticipantResultTest {

  @Test
  void throwsWhenRequiredCalculationFieldsAreMissing() {
    SettlementParticipantInput participant =
        new SettlementParticipantInput("p1", true, 100L, 5, 3, 4, 2);

    assertThatThrownBy(() -> SettlementParticipantResult.builder(participant).build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("shareRatio");

    assertThatThrownBy(
            () ->
                SettlementParticipantResult.builder(participant)
                    .shareRatio(new BigDecimal("0.0"))
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("baseRefundAmount");
  }
}
