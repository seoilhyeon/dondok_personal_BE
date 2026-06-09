package com.oit.dondok.domain.mission.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class DailySettlementTypeTest {

  // 인증마감 시각 정책(A 09:00 / B 21:00 / C 23:59:59)이 매핑과 일치하는지 고정한다.
  @Test
  void certificationDeadlinesMatchPolicy() {
    assertThat(DailySettlementType.A.getCertificationDeadline()).isEqualTo(LocalTime.of(9, 0));
    assertThat(DailySettlementType.B.getCertificationDeadline()).isEqualTo(LocalTime.of(21, 0));
    assertThat(DailySettlementType.C.getCertificationDeadline())
        .isEqualTo(LocalTime.of(23, 59, 59));
  }

  // A 타입은 인증 날짜 당일 12시에 자동 인증 대상이 된다.
  @Test
  void typeAAutoCertificationAtIsSameDayNoon() {
    LocalDate missionDate = LocalDate.of(2026, 6, 10);

    assertThat(DailySettlementType.A.autoCertificationAt(missionDate))
        .isEqualTo(LocalDateTime.of(2026, 6, 10, 12, 0));
  }

  // B 타입은 인증 날짜 다음날 00시에 자동 인증 대상이 된다.
  @Test
  void typeBAutoCertificationAtIsNextDayStart() {
    LocalDate missionDate = LocalDate.of(2026, 6, 10);

    assertThat(DailySettlementType.B.autoCertificationAt(missionDate))
        .isEqualTo(LocalDateTime.of(2026, 6, 11, 0, 0));
  }

  // C 타입은 인증 날짜 다음날 12시에 자동 인증 대상이 된다.
  @Test
  void typeCAutoCertificationAtIsNextDayNoon() {
    LocalDate missionDate = LocalDate.of(2026, 6, 10);

    assertThat(DailySettlementType.C.autoCertificationAt(missionDate))
        .isEqualTo(LocalDateTime.of(2026, 6, 11, 12, 0));
  }
}
