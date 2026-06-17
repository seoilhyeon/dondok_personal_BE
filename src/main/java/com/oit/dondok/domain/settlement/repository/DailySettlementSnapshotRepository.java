package com.oit.dondok.domain.settlement.repository;

import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySettlementSnapshotRepository
    extends JpaRepository<DailySettlementSnapshot, Long> {

  boolean existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
      Long crewId,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      DailySettlementPhase phase);

  boolean existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhaseAndStatus(
      Long crewId,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      DailySettlementPhase phase,
      DailySettlementStatus status);

  boolean
      existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhaseAndStatusAndRetryCountGreaterThanEqual(
          Long crewId,
          LocalDate missionDate,
          DailySettlementType dailySettlementType,
          DailySettlementPhase phase,
          DailySettlementStatus status,
          int retryCount);

  Optional<DailySettlementSnapshot> findByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
      Long crewId,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      DailySettlementPhase phase);

  List<DailySettlementSnapshot> findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndMissionDateIn(
      Long crewId,
      DailySettlementType dailySettlementType,
      DailySettlementPhase phase,
      DailySettlementStatus status,
      Collection<LocalDate> missionDates);

  List<DailySettlementSnapshot>
      findByCrewIdAndDailySettlementTypeAndPhaseAndStatusAndRetryCountGreaterThanEqualAndMissionDateIn(
          Long crewId,
          DailySettlementType dailySettlementType,
          DailySettlementPhase phase,
          DailySettlementStatus status,
          int retryCount,
          Collection<LocalDate> missionDates);
}
