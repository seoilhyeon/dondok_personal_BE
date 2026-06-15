package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementBatchService {

  private static final ZoneId BATCH_ZONE = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter RUN_KEY_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  // BATCH-001 owns normal execution and retryable caught failures only.
  // Stale RUNNING/terminal FAILED recovery is intentionally deferred to BATCH-004.
  private static final List<SettlementStatus> RUNNABLE_STATUSES =
      List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT);

  private final CrewRepository crewRepository;
  private final SettlementRepository settlementRepository;
  private final SettlementBatchProcessor settlementBatchProcessor;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void runFinalSettlementBatch() {
    LocalDateTime now = LocalDateTime.now(BATCH_ZONE);
    runFinalSettlementBatch(now, "settlement-" + RUN_KEY_FORMATTER.format(now));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void runFinalSettlementBatch(LocalDateTime now, String batchRunKey) {
    prepareCompletedCrewCandidates(now, batchRunKey);
    runPendingSettlements(now, batchRunKey);
  }

  private void prepareCompletedCrewCandidates(LocalDateTime now, String batchRunKey) {
    List<Long> activeCandidateIds =
        crewRepository.findByStatusAndEndAtLessThanEqual(CrewStatus.ACTIVE, now).stream()
            .map(Crew::getId)
            .toList();
    List<Long> closedCandidateIds =
        crewRepository.findClosedWithoutSettlement().stream().map(Crew::getId).toList();

    for (Long crewId : activeCandidateIds) {
      prepareOneCandidate(crewId, batchRunKey, now);
    }
    for (Long crewId : closedCandidateIds) {
      prepareOneCandidate(crewId, batchRunKey, now);
    }
  }

  private void prepareOneCandidate(Long crewId, String batchRunKey, LocalDateTime now) {
    try {
      settlementBatchProcessor.prepareCompletedCrewSettlementCandidate(crewId, batchRunKey, now);
    } catch (RuntimeException exception) {
      log.error("[settlement-batch] prepare failed. crewId={}", crewId, exception);
    }
  }

  private void runPendingSettlements(LocalDateTime now, String batchRunKey) {
    List<Long> settlementIds =
        settlementRepository
            .findByStatusInAndRetryCountLessThanOrderByIdAsc(
                RUNNABLE_STATUSES, Settlement.MAX_RETRY_COUNT)
            .stream()
            .map(Settlement::getId)
            .toList();
    for (Long settlementId : settlementIds) {
      runOneSettlement(settlementId, batchRunKey, now);
    }
  }

  private void runOneSettlement(Long settlementId, String batchRunKey, LocalDateTime now) {
    if (!settlementBatchProcessor.claimSettlement(settlementId, batchRunKey, now)) {
      return;
    }

    try {
      List<Long> settlementItemIds = settlementBatchProcessor.ensureSettlementItems(settlementId);
      for (Long settlementItemId : settlementItemIds) {
        settlementBatchProcessor.refundOneSettlementItem(settlementItemId);
      }
      settlementBatchProcessor.verifyAndMarkSucceeded(settlementId, LocalDateTime.now(BATCH_ZONE));
    } catch (SettlementBatchRunFailure failure) {
      settlementBatchProcessor.markRunFailure(
          settlementId,
          failure.getFailureCode(),
          failure.getMessage(),
          LocalDateTime.now(BATCH_ZONE));
    } catch (RuntimeException exception) {
      settlementBatchProcessor.markRunFailure(
          settlementId,
          SettlementFailureCode.UNKNOWN,
          exception.getMessage(),
          LocalDateTime.now(BATCH_ZONE));
    }
  }
}
