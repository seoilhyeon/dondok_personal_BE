package com.oit.dondok.domain.settlement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DailySettlementParticipantSnapshotTest {

  @Test
  void createThrowsInvalidInputWhenShareRatioIsNegative() {
    DailySettlementSnapshot dailySettlementSnapshot =
        org.mockito.Mockito.mock(DailySettlementSnapshot.class);
    CrewParticipant crewParticipant = org.mockito.Mockito.mock(CrewParticipant.class);
    org.mockito.Mockito.when(crewParticipant.getMember())
        .thenReturn(Member.create("member@test.com", "password", "member"));

    assertThatThrownBy(
            () ->
                DailySettlementParticipantSnapshot.create(
                    dailySettlementSnapshot, crewParticipant, 0, new BigDecimal("-0.000001"), 0L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .satisfies(errorCode -> assertThat(errorCode).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }
}
