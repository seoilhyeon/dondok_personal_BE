package com.oit.dondok.domain.settlement.repository;

import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

  @Override
  @EntityGraph(attributePaths = {"crew", "crew.hostMember"})
  Optional<Settlement> findById(Long id);

  Optional<Settlement> findByCrewId(Long crewId);

  List<Settlement> findByStatusInAndRetryCountLessThanOrderByIdAsc(
      Collection<SettlementStatus> statuses, int retryCount);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Settlement s
         set s.status = com.oit.dondok.domain.settlement.entity.SettlementStatus.RUNNING,
             s.version = s.version + 1,
             s.batchRunKey = :batchRunKey,
             s.startedAt = :startedAt,
             s.finishedAt = null,
             s.failureCode = null,
             s.failureMessage = null
       where s.id = :settlementId
         and s.retryCount < :maxRetryCount
         and s.status in :claimableStatuses
      """)
  int claimRunnable(
      @Param("settlementId") Long settlementId,
      @Param("batchRunKey") String batchRunKey,
      @Param("startedAt") LocalDateTime startedAt,
      @Param("claimableStatuses") Collection<SettlementStatus> claimableStatuses,
      @Param("maxRetryCount") int maxRetryCount);
}
