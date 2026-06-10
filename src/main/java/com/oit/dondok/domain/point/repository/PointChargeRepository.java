package com.oit.dondok.domain.point.repository;

import com.oit.dondok.domain.point.entity.PointCharge;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointChargeRepository extends JpaRepository<PointCharge, Long> {

  Optional<PointCharge> findByPaymentId(String paymentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select charge from PointCharge charge where charge.paymentId = :paymentId")
  Optional<PointCharge> findByPaymentIdForUpdate(@Param("paymentId") String paymentId);
}
