package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CrewMissionStatsCalculatorTest {

  private final CrewMissionStatsCalculator calculator = new CrewMissionStatsCalculator();

  // 7 / (2 × 5) = 0.7000
  @Test
  void crewSuccessRateDividesRecognizedByParticipantsTimesMissionDays() {
    assertThat(calculator.crewSuccessRate(7, 2, 5)).isEqualTo("0.7000");
  }

  // 13 / (2 × 7) = 0.928571... → HALF_UP scale 4
  @Test
  void crewSuccessRateRoundsHalfUpToScale4() {
    assertThat(calculator.crewSuccessRate(13, 2, 7)).isEqualTo("0.9286");
  }

  @Test
  void crewSuccessRateIsOneWhenEveryoneSucceededEveryDay() {
    assertThat(calculator.crewSuccessRate(10, 2, 5)).isEqualTo("1.0000");
  }

  @Test
  void crewSuccessRateReturnsZeroWhenDenominatorIsZero() {
    assertThat(calculator.crewSuccessRate(5, 0, 5)).isEqualTo("0");
    assertThat(calculator.crewSuccessRate(5, 3, 0)).isEqualTo("0");
  }
}
