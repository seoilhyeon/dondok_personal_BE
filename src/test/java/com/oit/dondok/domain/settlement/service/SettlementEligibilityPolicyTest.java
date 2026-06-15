package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementEligibilityPolicyTest {

  private final SettlementEligibilityPolicy policy = new SettlementEligibilityPolicy();

  @Mock private Crew crew;
  @Mock private MissionRule missionRule;

  @Test
  void activeCrewIsEligibleAfterAutoCertificationPlusTwentyFourHours() {
    given(crew.getStatus()).willReturn(CrewStatus.ACTIVE);
    given(crew.getEndAt()).willReturn(LocalDateTime.of(2026, 6, 10, 23, 59));
    given(missionRule.getDailySettlementType()).willReturn(DailySettlementType.B);

    assertThat(
            policy.isCompletedCrewEligible(crew, missionRule, LocalDateTime.of(2026, 6, 12, 0, 0)))
        .isTrue();
  }

  @Test
  void activeCrewIsNotEligibleBeforeGraceWindowEnds() {
    given(crew.getStatus()).willReturn(CrewStatus.ACTIVE);
    given(crew.getEndAt()).willReturn(LocalDateTime.of(2026, 6, 10, 23, 59));
    given(missionRule.getDailySettlementType()).willReturn(DailySettlementType.C);

    assertThat(
            policy.isCompletedCrewEligible(
                crew, missionRule, LocalDateTime.of(2026, 6, 12, 11, 59)))
        .isFalse();
  }

  @Test
  void closedCrewIsEligibleForBackfill() {
    given(crew.getStatus()).willReturn(CrewStatus.CLOSED);

    assertThat(policy.isCompletedCrewEligible(crew, missionRule, LocalDateTime.now())).isTrue();
  }

  @Test
  void cancelledCrewIsNotEligible() {
    given(crew.getStatus()).willReturn(CrewStatus.CANCELLED);

    assertThat(policy.isCompletedCrewEligible(crew, missionRule, LocalDateTime.now())).isFalse();
  }
}
