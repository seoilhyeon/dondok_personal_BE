package com.oit.dondok.domain.settlement.repository;

import com.oit.dondok.domain.settlement.entity.SettlementItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

  @EntityGraph(attributePaths = {"crewParticipant", "member", "pointHistory"})
  List<SettlementItem> findBySettlementIdOrderByIdAsc(Long settlementId);

  @EntityGraph(attributePaths = {"crewParticipant", "pointHistory"})
  Optional<SettlementItem> findBySettlementIdAndCrewParticipantId(
      Long settlementId, Long crewParticipantId);

  long countBySettlementId(Long settlementId);

  long countBySettlementIdAndPointHistoryIsNotNull(Long settlementId);
}
