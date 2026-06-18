package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.stereotype.Component;

// 정산 결과 화면용 크루 단위 통계: 미션 진행일 수, 크루 전체 성공률.
@Component
public class CrewMissionStatsCalculator {

  private static final int SUCCESS_RATE_SCALE = 4;

  // 기간 내 실제 미션 진행일 수.
  // DAILY는 전체 일수, 요일 지정은 스케줄 요일에 해당하는 날 수.
  public int missionDays(
      LocalDate startDate,
      LocalDate endDate,
      MissionFrequencyType frequencyType,
      Set<DayOfWeek> scheduleDays) {
    if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
      return 0;
    }
    if (frequencyType != MissionFrequencyType.SPECIFIC_DAYS) {
      return (int) (ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }
    if (scheduleDays.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      if (scheduleDays.contains(date.getDayOfWeek())) {
        count++;
      }
    }
    return count;
  }

  // 크루 전체 성공률 = 전체 인정 성공 수 / (참여자 수 x 미션 진행일 수).
  public String crewSuccessRate(
      long totalRecognizedSuccess, int participantCount, int missionDays) {
    long denominator = (long) participantCount * missionDays;
    if (denominator <= 0L) {
      return "0";
    }
    return BigDecimal.valueOf(totalRecognizedSuccess)
        .divide(BigDecimal.valueOf(denominator), SUCCESS_RATE_SCALE, RoundingMode.HALF_UP)
        .toPlainString();
  }
}
