package com.oit.dondok.domain.settlement.repository;

import com.oit.dondok.domain.settlement.entity.SettlementItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

  @EntityGraph(attributePaths = {"crewParticipant", "pointHistory"})
  List<SettlementItem> findBySettlementIdOrderByIdAsc(Long settlementId);
}
