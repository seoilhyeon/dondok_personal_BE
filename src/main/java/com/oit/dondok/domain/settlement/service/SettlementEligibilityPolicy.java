package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.MissionRule;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class SettlementEligibilityPolicy {

  private static final long FINAL_GRACE_HOURS = 24L;

  public boolean isCompletedCrewEligible(Crew crew, MissionRule missionRule, LocalDateTime now) {
    if (crew.getStatus() == CrewStatus.CLOSED) {
      return true;
    }
    return crew.getStatus() == CrewStatus.ACTIVE
        && !now.isBefore(finalSettlementEligibleAt(crew, missionRule));
  }

  public LocalDateTime finalSettlementEligibleAt(Crew crew, MissionRule missionRule) {
    return missionRule
        .getDailySettlementType()
        .autoCertificationAt(crew.getEndAt().toLocalDate())
        .plusHours(FINAL_GRACE_HOURS);
  }
}
