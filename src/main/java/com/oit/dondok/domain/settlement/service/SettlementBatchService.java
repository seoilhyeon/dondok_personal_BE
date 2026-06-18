package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;
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
  private static final Duration RUNNING_ATTEMPT_TIMEOUT = Duration.ofHours(6);
  private static final List<SettlementStatus> FINAL_PREFLIGHT_TARGET_STATUS =
      List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT);
  private static final List<SettlementStatus> FINAL_RUNNABLE_STATUSES =
      List.of(SettlementStatus.PENDING);
  private static final List<SettlementStatus> RETRY_RUNNABLE_STATUSES =
      List.of(SettlementStatus.RETRY_WAIT);

  private final CrewRepository crewRepository;
  private final SettlementRepository settlementRepository;
  private final SettlementBatchProcessor settlementBatchProcessor;
  private final DailySettlementBackfillService dailySettlementBackfillService;
  private final DailySettlementSnapshotRecoveryService dailySettlementSnapshotRecoveryService;

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
    runFinalPendingSettlements(dailySettlementType, now, batchRunKey);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void runRetrySettlementBatch() {
    LocalDateTime now = LocalDateTime.now(BATCH_ZONE);
    runRetrySettlementBatch(now, "settlement-retry-" + RUN_KEY_FORMATTER.format(now));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void runRetrySettlementBatch(LocalDateTime now, String batchRunKey) {
    recoverTimedOutRunningSettlements(now);
    runRetryWaitSettlements(now, batchRunKey);
  }

  private void recoverTimedOutRunningSettlements(LocalDateTime now) {
    LocalDateTime timeoutCutoff = now.minus(RUNNING_ATTEMPT_TIMEOUT);
    List<Settlement> timedOutSettlements =
        settlementRepository.findByStatusAndRetryCountLessThanAndStartedAtBeforeOrderByIdAsc(
            SettlementStatus.RUNNING, Settlement.MAX_RETRY_COUNT, timeoutCutoff);
    for (Settlement settlement : timedOutSettlements) {
      recoverOneTimedOutRunningSettlement(settlement, timeoutCutoff, now);
    }
  }

  private void recoverOneTimedOutRunningSettlement(
      Settlement settlement, LocalDateTime timeoutCutoff, LocalDateTime now) {
    try {
      log.warn(
          "시간 초과된 최종 정산 실행을 재시도 대기 상태로 회수합니다. settlementId={}, retryCount={}, timeoutCutoff={}",
          settlement.getId(),
          settlement.getRetryCount(),
          timeoutCutoff);
      settlementBatchProcessor.markRunFailure(
          settlement.getId(),
          SettlementFailureCode.UNKNOWN,
          "최종 정산 실행 시간이 초과되어 재시도 대상으로 회수되었습니다.",
          now);
    } catch (RuntimeException exception) {
      log.warn(
          "시간 초과된 최종 정산 실행 회수에 실패했습니다. settlementId={}, reason={}",
          settlement.getId(),
          exception.getMessage(),
          exception);
    }
  }

  private void prepareCompletedCrewCandidates(
      DailySettlementType dailySettlementType, LocalDateTime now, String batchRunKey) {
    List<Long> activeCandidateIds =
        crewRepository.findByStatusAndEndAtLessThanEqual(CrewStatus.ACTIVE, now).stream()
            .map(Crew::getId)
            .toList();
    List<Long> closedCandidateIds =
        crewRepository.findClosedWithoutSettlement().stream().map(Crew::getId).toList();
    List<Long> candidateIds =
        Stream.concat(activeCandidateIds.stream(), closedCandidateIds.stream()).distinct().toList();
    List<Long> runnableSettlementCrewIds =
        settlementRepository.findCrewIdsByStatusInAndRetryCountLessThan(
            FINAL_PREFLIGHT_TARGET_STATUS, Settlement.MAX_RETRY_COUNT);
    List<Long> preflightCrewIds =
        Stream.concat(candidateIds.stream(), runnableSettlementCrewIds.stream())
            .distinct()
            .toList();

    dailySettlementBackfillService.backfillMissingFinalizedSnapshots(
        preflightCrewIds, dailySettlementType, batchRunKey, now);
    dailySettlementSnapshotRecoveryService.recoverExhaustedFinalizedSnapshots(
        preflightCrewIds, dailySettlementType, batchRunKey, now);

    for (Long crewId : candidateIds) {
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

  private void runFinalPendingSettlements(
      DailySettlementType dailySettlementType, LocalDateTime now, String batchRunKey) {
    List<Long> settlementIds =
        settlementRepository
            .findByStatusInAndRetryCountLessThanOrderByIdAsc(
                FINAL_RUNNABLE_STATUSES, Settlement.MAX_RETRY_COUNT)
            .stream()
            .filter(settlement -> matchesDailySettlementType(settlement, dailySettlementType))
            .map(Settlement::getId)
            .toList();
    runSettlements(settlementIds, batchRunKey, now);
  }

  private void runRetryWaitSettlements(LocalDateTime now, String batchRunKey) {
    List<Long> settlementIds =
        settlementRepository
            .findByStatusInAndRetryCountLessThanOrderByIdAsc(
                RETRY_RUNNABLE_STATUSES, Settlement.MAX_RETRY_COUNT)
            .stream()
            .map(Settlement::getId)
            .toList();
    runSettlements(settlementIds, batchRunKey, now);
  }

  private void runSettlements(List<Long> settlementIds, String batchRunKey, LocalDateTime now) {
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
