package com.oit.dondok.domain.settlement.repository;

import com.oit.dondok.domain.settlement.entity.DailySettlementParticipantSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySettlementParticipantSnapshotRepository
    extends JpaRepository<DailySettlementParticipantSnapshot, Long> {}
