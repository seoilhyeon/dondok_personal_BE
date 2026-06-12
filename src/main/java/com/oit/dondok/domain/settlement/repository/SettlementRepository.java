package com.oit.dondok.domain.settlement.repository;

import com.oit.dondok.domain.settlement.entity.Settlement;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

  @Override
  @EntityGraph(attributePaths = {"crew", "crew.hostMember"})
  Optional<Settlement> findById(Long id);

  Optional<Settlement> findByCrewId(Long crewId);
}
