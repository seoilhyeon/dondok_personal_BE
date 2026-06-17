package com.oit.dondok.domain.mission.repository;

import com.oit.dondok.domain.mission.entity.MissionRule;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionRuleRepository
    extends JpaRepository<MissionRule, Long>, MissionRuleRepositoryCustom {

  Optional<MissionRule> findByCrewId(Long crewId);

  @EntityGraph(attributePaths = {"crew", "crew.hostMember"})
  Optional<MissionRule> findWithCrewByCrewId(Long crewId);
}
