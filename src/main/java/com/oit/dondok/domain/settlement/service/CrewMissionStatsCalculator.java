package com.oit.dondok.domain.settlement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

// 정산 결과 화면용 크루 단위 통계: 크루 전체 성공률.
@Component
public class CrewMissionStatsCalculator {

  private static final int SUCCESS_RATE_SCALE = 4;

  // 크루 전체 성공률 = 전체 인정 성공 수 / (참여자 수 x 미션 진행일 수). string decimal(scale 4). 분모 0이면 "0".
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
