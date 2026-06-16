package com.oit.dondok.domain.mission.repository;

import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MissionRuleRepositoryCustom {

  List<MissionRule> findRulesForDailySettlement(
      DailySettlementType dailySettlementType,
      LocalDateTime missionDateStartInclusive,
      LocalDateTime missionDateEndExclusive,
      int dayOfWeek,
      Collection<CrewStatus> crewStatuses);
}
