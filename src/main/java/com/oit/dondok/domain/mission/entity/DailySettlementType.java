package com.oit.dondok.domain.mission.entity;

import java.time.LocalTime;
import lombok.Getter;

// 일일 정산 타입별 인증마감 시각 (A 09:00 / B 21:00 / C 23:59:59)
@Getter
public enum DailySettlementType {
  A(LocalTime.of(9, 0)),
  B(LocalTime.of(21, 0)),
  C(LocalTime.of(23, 59, 59));

  private final LocalTime certificationDeadline;

  DailySettlementType(LocalTime certificationDeadline) {
    this.certificationDeadline = certificationDeadline;
  }
}
