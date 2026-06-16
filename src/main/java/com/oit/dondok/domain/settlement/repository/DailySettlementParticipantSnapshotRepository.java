package com.oit.dondok.domain.settlement.repository;

import com.oit.dondok.domain.settlement.entity.DailySettlementParticipantSnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySettlementParticipantSnapshotRepository
    extends JpaRepository<DailySettlementParticipantSnapshot, Long> {

  List<DailySettlementParticipantSnapshot> findByDailySettlementSnapshotIdIn(
      Collection<Long> dailySettlementSnapshotIds);
}
