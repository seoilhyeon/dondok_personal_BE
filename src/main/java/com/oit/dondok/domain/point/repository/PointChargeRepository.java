package com.oit.dondok.domain.point.repository;

import com.oit.dondok.domain.point.entity.PointCharge;
import com.oit.dondok.domain.point.entity.PointChargeStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointChargeRepository extends JpaRepository<PointCharge, Long> {

  Optional<PointCharge> findByPaymentId(String paymentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select charge from PointCharge charge where charge.paymentId = :paymentId")
  Optional<PointCharge> findByPaymentIdForUpdate(@Param("paymentId") String paymentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select charge from PointCharge charge where charge.id = :id")
  Optional<PointCharge> findByIdForUpdate(@Param("id") Long id);

  @Query(
      """
      select charge.id
      from PointCharge charge
      where charge.status = :status
        and charge.pointHistory is null
        and charge.createdAt <= :createdBefore
        and charge.id > :lastSeenId
        and charge.recoveryAttemptCount < :maxRecoveryAttempts
        and (charge.nextRecoveryAt is null or charge.nextRecoveryAt <= :now)
      order by charge.id asc
      """)
  List<Long> findRecoveryTargetIdsAfterId(
      @Param("status") PointChargeStatus status,
      @Param("createdBefore") LocalDateTime createdBefore,
      @Param("lastSeenId") Long lastSeenId,
      @Param("maxRecoveryAttempts") int maxRecoveryAttempts,
      @Param("now") LocalDateTime now,
      Pageable pageable);
}
