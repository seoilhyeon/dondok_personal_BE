package com.oit.dondok.domain.mission.entity;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;

// 일일 정산 타입별 인증마감 시각 (A 09:00 / B 21:00 / C 23:59:59)
@Getter
public enum DailySettlementType {
  A(LocalTime.of(9, 0)),
  B(LocalTime.of(21, 0)),
  C(LocalTime.of(23, 59, 59));

  private final LocalTime certificationDeadline;
  private static final Duration HOST_REVIEW_GRACE_DURATION = Duration.ofHours(72);

  DailySettlementType(LocalTime certificationDeadline) {
    this.certificationDeadline = certificationDeadline;
  }

  // 크루 타입별 일일 자동 인증 처리 가능 시각을 계산한다.
  public LocalDateTime autoCertificationAt(LocalDate missionDate) {
    return switch (this) {
      case A -> missionDate.atTime(12, 0);
      case B -> missionDate.plusDays(1).atStartOfDay();
      case C -> missionDate.plusDays(1).atTime(12, 0);
    };
  }

  public LocalDateTime hostReviewableUntil(LocalDate missionDate) {
    return autoCertificationAt(missionDate).plus(HOST_REVIEW_GRACE_DURATION);
  }
}
