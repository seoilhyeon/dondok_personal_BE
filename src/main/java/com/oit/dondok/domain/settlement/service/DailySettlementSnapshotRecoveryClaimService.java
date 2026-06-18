package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.settlement.entity.DailySettlementPhase;
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
class DailySettlementSnapshotRecoveryClaimService {

  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean claimExhaustedFinalized(Long snapshotId, String batchRunKey, LocalDateTime now) {
    return dailySettlementSnapshotRepository.claimRecoveryTarget(
            snapshotId,
            DailySettlementPhase.FINALIZED,
            DailySettlementStatus.FAILED,
            DailySettlementStatus.RETRYING,
            DailySettlementSnapshot.MAX_RETRY_COUNT,
            batchRunKey,
            now)
        == 1;
  }
}
