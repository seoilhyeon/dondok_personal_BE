package com.oit.dondok.domain.settlement.repository;

import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailySettlementSnapshotRepository
    extends JpaRepository<DailySettlementSnapshot, Long> {

  boolean existsByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
      Long crewId,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      DailySettlementPhase phase);

  Optional<DailySettlementSnapshot> findByCrewIdAndMissionDateAndDailySettlementTypeAndPhase(
      Long crewId,
      LocalDate missionDate,
      DailySettlementType dailySettlementType,
      DailySettlementPhase phase);

  @Query(
      "select s.id from DailySettlementSnapshot s "
          + "where s.retryCount < :retryCount "
          + "and ((s.status = :failedStatus and s.frozenAt < :failedBefore) "
          + "or (s.status = :retryingStatus and s.frozenAt < :retryingStaleBefore)) "
          + "order by s.id asc")
  List<Long> findRetryTargetIds(
      @Param("failedStatus") DailySettlementStatus failedStatus,
      @Param("retryingStatus") DailySettlementStatus retryingStatus,
      @Param("retryCount") int retryCount,
      @Param("failedBefore") LocalDateTime failedBefore,
      @Param("retryingStaleBefore") LocalDateTime retryingStaleBefore,
      Pageable pageable);

  @EntityGraph(attributePaths = {"crew"})
  @Query("select s from DailySettlementSnapshot s where s.id = :id")
  Optional<DailySettlementSnapshot> findWithCrewById(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from DailySettlementSnapshot s "
          + "where s.id = :id "
          + "and s.status = :status "
          + "and s.batchRunKey = :batchRunKey")
  Optional<DailySettlementSnapshot> findRetryOwnerForUpdate(
      @Param("id") Long id,
      @Param("status") DailySettlementStatus status,
      @Param("batchRunKey") String batchRunKey);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update DailySettlementSnapshot s "
          + "set s.status = :retryingStatus, s.batchRunKey = :batchRunKey, s.frozenAt = :frozenAt "
          + "where s.id = :id "
          + "and s.retryCount < :maxRetryCount "
          + "and ((s.status = :failedStatus and s.frozenAt < :failedBefore) "
          + "or (s.status = :retryingStatus and s.frozenAt < :retryingStaleBefore))")
  int claimRetryTarget(
      @Param("id") Long id,
      @Param("failedStatus") DailySettlementStatus failedStatus,
      @Param("retryingStatus") DailySettlementStatus retryingStatus,
      @Param("maxRetryCount") int maxRetryCount,
      @Param("failedBefore") LocalDateTime failedBefore,
      @Param("retryingStaleBefore") LocalDateTime retryingStaleBefore,
      @Param("batchRunKey") String batchRunKey,
      @Param("frozenAt") LocalDateTime frozenAt);

  List<DailySettlementSnapshot> findByCrewIdAndDailySettlementTypeAndPhaseAndMissionDateIn(
      Long crewId,
      DailySettlementType dailySettlementType,
      DailySettlementPhase phase,
      Collection<LocalDate> missionDates);

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
