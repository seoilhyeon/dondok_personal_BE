package com.oit.dondok.domain.mission.repository;

import com.oit.dondok.domain.mission.entity.ModerationDecisionType;
import com.oit.dondok.domain.mission.entity.ModerationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationHistoryRepository extends JpaRepository<ModerationHistory, Long> {

  boolean existsByMissionLogIdAndDecisionType(
      Long missionLogId, ModerationDecisionType decisionType);
}
