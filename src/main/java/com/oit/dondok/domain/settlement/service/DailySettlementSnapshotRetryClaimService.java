package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class DailySettlementSnapshotRetryClaimService {

  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean claim(
      Long snapshotId, String batchRunKey, LocalDateTime now, LocalDateTime retryingStaleBefore) {
    return dailySettlementSnapshotRepository.claimRetryTarget(
            snapshotId,
            DailySettlementStatus.FAILED,
            DailySettlementStatus.RETRYING,
            DailySettlementSnapshot.MAX_RETRY_COUNT,
            now,
            retryingStaleBefore,
            batchRunKey,
            now)
        == 1;
  }
}
