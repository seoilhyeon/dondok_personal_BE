package com.oit.dondok.domain.point.repository;

import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

  Optional<PointHistory> findByIdempotencyKey(String idempotencyKey);

  long countByReferenceTypeAndReferenceIdAndTransactionType(
      PointReferenceType referenceType, Long referenceId, PointTransactionType transactionType);
}
