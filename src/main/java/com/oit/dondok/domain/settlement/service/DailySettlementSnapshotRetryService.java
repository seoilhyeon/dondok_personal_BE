package com.oit.dondok.domain.settlement.service;

import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementStatus;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySettlementSnapshotRetryService {

  private static final ZoneId BATCH_ZONE = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter RUN_KEY_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  private static final int RETRY_BATCH_SIZE = 50;
  private static final Duration RETRYING_LEASE_TIMEOUT = Duration.ofHours(1);

  private final AtomicBoolean retryRunning = new AtomicBoolean(false);

  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  private final DailySettlementSnapshotRetryClaimService dailySettlementSnapshotRetryClaimService;
  private final MissionRuleRepository missionRuleRepository;
  private final DailySettlementSnapshotCreationService dailySettlementSnapshotCreationService;
  private final DailySettlementSnapshotFailureRecordService
      dailySettlementSnapshotFailureRecordService;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void runRetrySnapshotBatch() {
    runRetrySnapshotBatch(LocalDateTime.now(BATCH_ZONE));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void runRetrySnapshotBatch(LocalDateTime now) {
    if (!retryRunning.compareAndSet(false, true)) {
      log.warn("이미 일일 정산 스냅샷 재시도 배치가 실행 중입니다. 이번 실행을 건너뜁니다.");
      return;
    }
    try {
      dailySettlementSnapshotRepository
          .findRetryTargetIds(
              DailySettlementStatus.FAILED,
              DailySettlementStatus.RETRYING,
              DailySettlementSnapshot.MAX_RETRY_COUNT,
              now,
              now.minus(RETRYING_LEASE_TIMEOUT),
              PageRequest.of(0, RETRY_BATCH_SIZE))
          .forEach(snapshotId -> retryOneSnapshot(snapshotId, now));
    } finally {
      retryRunning.set(false);
    }
  }

  private void retryOneSnapshot(Long snapshotId, LocalDateTime now) {
    String batchRunKey = batchRunKey(snapshotId, now);
    if (!tryClaim(snapshotId, batchRunKey, now)) {
      return;
    }
    DailySettlementSnapshot snapshot =
        dailySettlementSnapshotRepository.findWithCrewById(snapshotId).orElse(null);
    if (snapshot == null) {
      log.warn("일일 정산 스냅샷 재시도 claim 이후 대상을 찾을 수 없습니다. snapshotId={}", snapshotId);
      return;
    }
    Long crewId = snapshot.getCrew().getId();
    try {
      MissionRule missionRule = missionRuleRepository.findWithCrewByCrewId(crewId).orElse(null);
      if (missionRule == null) {
        tryRecordRetryFailure(snapshot, batchRunKey, now, "미션 규칙을 찾을 수 없습니다.");
        log.warn(
            "일일 정산 스냅샷 재시도 대상의 미션 규칙을 찾을 수 없습니다. snapshotId={}, crewId={}",
            snapshot.getId(),
            crewId);
        return;
      }
      if (missionRule.getDailySettlementType() != snapshot.getDailySettlementType()) {
        tryRecordRetryFailure(snapshot, batchRunKey, now, "스냅샷 정산 타입과 현재 미션 규칙 정산 타입이 일치하지 않습니다.");
        log.warn(
            "일일 정산 스냅샷 재시도 대상의 정산 타입이 미션 규칙과 일치하지 않습니다. snapshotId={}, crewId={}, snapshotType={}, actualType={}",
            snapshot.getId(),
            crewId,
            snapshot.getDailySettlementType(),
            missionRule.getDailySettlementType());
        return;
      }
      dailySettlementSnapshotCreationService.retrySnapshot(
          missionRule, snapshot.getMissionDate(), snapshot.getPhase(), batchRunKey, now);
    } catch (RuntimeException exception) {
      tryRecordRetryFailure(snapshot, batchRunKey, now, failureMessageOf(exception));
      log.warn(
          "일일 정산 스냅샷 재시도 중 예외가 발생해 대상을 건너뜁니다. snapshotId={}, crewId={}, missionDate={}, phase={}, reason={}",
          snapshot.getId(),
          crewId,
          snapshot.getMissionDate(),
          snapshot.getPhase(),
          failureMessageOf(exception),
          exception);
    }
  }

  private boolean tryClaim(Long snapshotId, String batchRunKey, LocalDateTime now) {
    try {
      return dailySettlementSnapshotRetryClaimService.claim(
          snapshotId, batchRunKey, now, now.minus(RETRYING_LEASE_TIMEOUT));
    } catch (RuntimeException exception) {
      log.warn(
          "일일 정산 스냅샷 재시도 claim 중 예외가 발생해 대상을 건너뜁니다. snapshotId={}, reason={}",
          snapshotId,
          failureMessageOf(exception),
          exception);
      return false;
    }
  }

  private void tryRecordRetryFailure(
      DailySettlementSnapshot snapshot,
      String batchRunKey,
      LocalDateTime now,
      String failureMessage) {
    try {
      dailySettlementSnapshotFailureRecordService.recordRetryFailure(
          snapshot.getId(), batchRunKey, now, failureMessage);
    } catch (RuntimeException recordingException) {
      log.warn(
          "일일 정산 스냅샷 재시도 실패 기록 중 예외가 발생했습니다. snapshotId={}, missionDate={}, phase={}, reason={}",
          snapshot.getId(),
          snapshot.getMissionDate(),
          snapshot.getPhase(),
          failureMessageOf(recordingException),
          recordingException);
    }
  }

  private String failureMessageOf(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return exception.getClass().getSimpleName();
    }
    return message;
  }

  private String batchRunKey(Long snapshotId, LocalDateTime now) {
    return "daily-settlement-snapshot-retry-" + snapshotId + "-" + RUN_KEY_FORMATTER.format(now);
  }
}
