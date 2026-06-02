package com.oit.dondok.domain.mission.entity;

import static org.assertj.core.api.Assertions.assertThat;

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
}
