package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
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
  public void runFinalSettlementBatch(DailySettlementType dailySettlementType) {
    LocalDateTime now = LocalDateTime.now(BATCH_ZONE);
    runFinalSettlementBatch(
        dailySettlementType,
        now,
        "settlement-" + dailySettlementType.name() + "-" + RUN_KEY_FORMATTER.format(now));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void runFinalSettlementBatch(
      DailySettlementType dailySettlementType, LocalDateTime now, String batchRunKey) {
    prepareCompletedCrewCandidates(dailySettlementType, now, batchRunKey);
    runPendingSettlements(dailySettlementType, now, batchRunKey);
  }

  private void prepareCompletedCrewCandidates(
      DailySettlementType dailySettlementType, LocalDateTime now, String batchRunKey) {
    List<Long> activeCandidateIds =
        crewRepository.findByStatusAndEndAtLessThanEqual(CrewStatus.ACTIVE, now).stream()
            .map(Crew::getId)
            .toList();
    List<Long> closedCandidateIds =
        crewRepository.findClosedWithoutSettlement().stream().map(Crew::getId).toList();

    for (Long crewId : activeCandidateIds) {
      prepareOneCandidate(crewId, dailySettlementType, batchRunKey, now);
    }
    for (Long crewId : closedCandidateIds) {
      prepareOneCandidate(crewId, dailySettlementType, batchRunKey, now);
    }
  }

  private void prepareOneCandidate(
      Long crewId, DailySettlementType dailySettlementType, String batchRunKey, LocalDateTime now) {
    try {
      settlementBatchProcessor.prepareCompletedCrewSettlementCandidate(
          crewId, dailySettlementType, batchRunKey, now);
    } catch (SettlementBatchRunFailure failure) {
      markCandidateFailureIfAvailable(crewId, failure.getFailureCode(), failure.getMessage());
    } catch (RuntimeException exception) {
      markCandidateFailureIfAvailable(
          crewId, SettlementFailureCode.UNKNOWN, String.valueOf(exception.getMessage()));
    }
  }

  private void markCandidateFailureIfAvailable(
      Long crewId, SettlementFailureCode failureCode, String failureMessage) {
    settlementRepository
        .findByCrewId(crewId)
        .ifPresent(
            settlement ->
                settlementBatchProcessor.markRunFailure(
                    settlement.getId(),
                    failureCode,
                    failureMessage,
                    LocalDateTime.now(BATCH_ZONE)));
  }

  private void runPendingSettlements(
      DailySettlementType dailySettlementType, LocalDateTime now, String batchRunKey) {
    List<Long> settlementIds =
        settlementRepository
            .findByStatusInAndRetryCountLessThanOrderByIdAsc(
                RUNNABLE_STATUSES, Settlement.MAX_RETRY_COUNT)
            .stream()
            .filter(settlement -> matchesDailySettlementType(settlement, dailySettlementType))
            .map(Settlement::getId)
            .toList();
    for (Long settlementId : settlementIds) {
      runOneSettlement(settlementId, batchRunKey, now);
    }
  }

  private boolean matchesDailySettlementType(
      Settlement settlement, DailySettlementType dailySettlementType) {
    return settlement.getRuleContextSnapshot().dailySettlementType() == dailySettlementType;
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
