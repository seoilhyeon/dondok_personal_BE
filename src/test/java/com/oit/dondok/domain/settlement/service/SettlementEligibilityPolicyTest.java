package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.MissionRule;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementEligibilityPolicyTest {

  @Mock private FinalSettlementReadinessService finalSettlementReadinessService;
  @Mock private Crew crew;
  @Mock private MissionRule missionRule;

  @InjectMocks private SettlementEligibilityPolicy policy;

  @Test
  void activeCrewIsEligibleWhenFinalizedDailySnapshotReadinessIsSatisfied() {
    LocalDateTime now = LocalDateTime.of(2026, 6, 12, 0, 0);
    given(crew.getStatus()).willReturn(CrewStatus.ACTIVE);
    given(
            finalSettlementReadinessService.existsReadyFinalSettlementSnapshot(
                crew, missionRule, now))
        .willReturn(true);

    assertThat(policy.isCompletedCrewEligible(crew, missionRule, now)).isTrue();
  }

  @Test
  void activeCrewIsNotEligibleBeforeFinalizedDailySnapshotReadinessIsSatisfied() {
    LocalDateTime now = LocalDateTime.of(2026, 6, 12, 0, 0);
    given(crew.getStatus()).willReturn(CrewStatus.ACTIVE);
    given(
            finalSettlementReadinessService.existsReadyFinalSettlementSnapshot(
                crew, missionRule, now))
        .willReturn(false);

    assertThat(policy.isCompletedCrewEligible(crew, missionRule, now)).isFalse();
  }

  @Test
  void closedCrewStillRequiresFinalizedDailySnapshotReadiness() {
    LocalDateTime now = LocalDateTime.of(2026, 6, 12, 0, 0);
    given(crew.getStatus()).willReturn(CrewStatus.CLOSED);
    given(
            finalSettlementReadinessService.existsReadyFinalSettlementSnapshot(
                crew, missionRule, now))
        .willReturn(false);

    assertThat(policy.isCompletedCrewEligible(crew, missionRule, now)).isFalse();
  }

  @Test
  void closedCrewIsEligibleWhenFinalizedDailySnapshotReadinessIsSatisfied() {
    LocalDateTime now = LocalDateTime.of(2026, 6, 12, 0, 0);
    given(crew.getStatus()).willReturn(CrewStatus.CLOSED);
    given(
            finalSettlementReadinessService.existsReadyFinalSettlementSnapshot(
                crew, missionRule, now))
        .willReturn(true);

    assertThat(policy.isCompletedCrewEligible(crew, missionRule, now)).isTrue();
  }

  @Test
  void cancelledCrewIsNotEligible() {
    given(crew.getStatus()).willReturn(CrewStatus.CANCELLED);

    assertThat(policy.isCompletedCrewEligible(crew, missionRule, LocalDateTime.now())).isFalse();
  }

  @Test
  void recruitingCrewIsNotEligible() {
    given(crew.getStatus()).willReturn(CrewStatus.RECRUITING);

    assertThat(policy.isCompletedCrewEligible(crew, missionRule, LocalDateTime.now())).isFalse();
  }
}
