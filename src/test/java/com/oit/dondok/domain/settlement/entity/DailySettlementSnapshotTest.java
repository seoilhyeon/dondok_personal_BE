package com.oit.dondok.domain.settlement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DailySettlementSnapshotTest {

  @Test
  void provisionalThrowsInvalidInputWhenAggregateValueIsNegative() {
    Crew crew = org.mockito.Mockito.mock(Crew.class);

    assertThatThrownBy(
            () ->
                DailySettlementSnapshot.provisional(
                    crew,
                    LocalDate.of(2026, 6, 15),
                    DailySettlementType.A,
                    MissionFrequencyType.DAILY,
                    "batch-key",
                    LocalDateTime.of(2026, 6, 15, 12, 0),
                    -1,
                    0,
                    0L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .satisfies(errorCode -> assertThat(errorCode).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }
}
