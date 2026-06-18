package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CrewMissionStatsCalculatorTest {

  private final CrewMissionStatsCalculator calculator = new CrewMissionStatsCalculator();

  // DAILY: 양끝 포함 전체 일수
  @Test
  void missionDaysDailyCountsAllDaysInclusive() {
    LocalDate start = LocalDate.of(2026, 6, 1);
    LocalDate end = start.plusDays(29); // 30일

    assertThat(calculator.missionDays(start, end, MissionFrequencyType.DAILY, Set.of()))
        .isEqualTo(30);
  }

  // 요일 지정: 28일(=4주)이면 시작 요일과 무관하게 각 요일 4번 → 월화수 = 12
  @Test
  void missionDaysSpecificDaysCountsScheduledWeekdays() {
    LocalDate start = LocalDate.of(2026, 6, 1);
    LocalDate end = start.plusDays(27);

    int days =
        calculator.missionDays(
            start,
            end,
            MissionFrequencyType.SPECIFIC_DAYS,
            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY));

    assertThat(days).isEqualTo(12);
  }

  @Test
  void missionDaysReturnsZeroWhenEndBeforeStart() {
    LocalDate start = LocalDate.of(2026, 6, 10);

    assertThat(
            calculator.missionDays(start, start.minusDays(1), MissionFrequencyType.DAILY, Set.of()))
        .isZero();
  }

  @Test
  void missionDaysReturnsZeroWhenSpecificDaysScheduleEmpty() {
    LocalDate start = LocalDate.of(2026, 6, 1);

    assertThat(
            calculator.missionDays(
                start, start.plusDays(6), MissionFrequencyType.SPECIFIC_DAYS, Set.of()))
        .isZero();
  }

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
