package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.MissionRule;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementEligibilityPolicy {

  private final FinalSettlementReadinessService finalSettlementReadinessService;

  public boolean isCompletedCrewEligible(Crew crew, MissionRule missionRule, LocalDateTime now) {
    // Crew close 배치가 병렬 실행되거나 지연될 수 있어 ACTIVE/CLOSED 모두 허용한다.
    if (crew.getStatus() != CrewStatus.CLOSED && crew.getStatus() != CrewStatus.ACTIVE) {
      return false;
    }
    return finalSettlementReadinessService.existsReadyFinalSettlementSnapshot(
        crew, missionRule, now);
  }
}
