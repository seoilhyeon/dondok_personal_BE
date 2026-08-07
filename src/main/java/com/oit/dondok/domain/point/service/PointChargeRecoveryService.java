package com.oit.dondok.domain.point.service;

import com.oit.dondok.domain.point.entity.PointCharge;
import com.oit.dondok.domain.point.entity.PointChargeStatus;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.port.PaymentConfirmClient;
import com.oit.dondok.domain.point.port.PaymentLookupClient;
import com.oit.dondok.domain.point.port.PaymentLookupResult;
import com.oit.dondok.domain.point.repository.PointChargeRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class PointChargeRecoveryService {

  private static final String KRW = "KRW";
  private static final String DONE = "DONE";
  private static final String MISMATCH_FAILURE_CODE = "PAYMENT_LOOKUP_MISMATCH";
  private static final int RECOVERY_BATCH_SIZE = 50;
  private static final int RECOVERY_GRACE_MINUTES = 5;
  private static final int RECOVERY_LEASE_MINUTES = 5;
  private static final int RECOVERY_RETRY_INTERVAL_MINUTES = 5;
  private static final int MAX_RECOVERY_ATTEMPTS = 12;

  private final PointChargeRepository pointChargeRepository;
  private final PaymentConfirmClient paymentConfirmClient;
  private final PaymentLookupClient paymentLookupClient;
  private final PointLedgerService pointLedgerService;
  private final TransactionTemplate transactionTemplate;
  private final MeterRegistry meterRegistry;

  public PointChargeRecoveryService(
      PointChargeRepository pointChargeRepository,
      PaymentConfirmClient paymentConfirmClient,
      PaymentLookupClient paymentLookupClient,
      PointLedgerService pointLedgerService,
      PlatformTransactionManager transactionManager) {
    this(
        pointChargeRepository,
        paymentConfirmClient,
        paymentLookupClient,
        pointLedgerService,
        transactionManager,
        null);
  }

  @Autowired
  public PointChargeRecoveryService(
      PointChargeRepository pointChargeRepository,
      PaymentConfirmClient paymentConfirmClient,
      PaymentLookupClient paymentLookupClient,
      PointLedgerService pointLedgerService,
      PlatformTransactionManager transactionManager,
      MeterRegistry meterRegistry) {
    this.pointChargeRepository = pointChargeRepository;
    this.paymentConfirmClient = paymentConfirmClient;
    this.paymentLookupClient = paymentLookupClient;
    this.pointLedgerService = pointLedgerService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.meterRegistry = meterRegistry;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void runRecoveryBatch() {
    runRecoveryBatch(LocalDateTime.now());
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void runRecoveryBatch(LocalDateTime now) {
    LocalDateTime createdBefore = now.minusMinutes(RECOVERY_GRACE_MINUTES);
    Pageable limit = PageRequest.of(0, RECOVERY_BATCH_SIZE);

    Long lastSeenId = 0L;
    while (true) {
      List<Long> targetIds =
          pointChargeRepository.findRecoveryTargetIdsAfterId(
              PointChargeStatus.PENDING_CONFIRM,
              createdBefore,
              lastSeenId,
              MAX_RECOVERY_ATTEMPTS,
              now,
              limit);
      if (targetIds.isEmpty()) {
        return;
      }

      for (Long targetId : targetIds) {
        recoverOne(targetId, now);
      }
      lastSeenId = targetIds.get(targetIds.size() - 1);
    }
  }

  private void recoverOne(Long chargeId, LocalDateTime now) {
    PointChargeSnapshot snapshot = claimRecoveryTarget(chargeId, now);
    if (snapshot == null) {
      return;
    }

    PaymentLookupResult lookupResult;
    try {
      lookupResult = paymentLookupClient.lookup(snapshot.paymentId());
    } catch (RuntimeException e) {
      log.warn(
          "포인트 충전 복구 결제 조회에 실패했습니다. pointChargeId={}, paymentId={}",
          chargeId,
          snapshot.paymentId(),
          e);
      if (recordRetryAttempt(chargeId, now))
        recordRecovery("retried", "lookup", "lookup_exception");
      return;
    }

    if (lookupResult == null || !DONE.equals(lookupResult.status())) {
      log.info(
          "포인트 충전 복구 대상 결제가 승인 완료 상태가 아닙니다. pointChargeId={}, paymentId={}, status={}",
          chargeId,
          snapshot.paymentId(),
          lookupResult == null ? null : lookupResult.status());
      if (recordRetryAttempt(chargeId, now)) recordRecovery("retried", "lookup", "not_done");
      return;
    }

    if (!snapshot.matches(lookupResult)) {
      if (failMismatch(chargeId)) recordRecovery("failed", "lookup", "mismatch");
      return;
    }

    try {
      RecoveryOutcome outcome =
          inTransaction(() -> completeIfStillRecoverable(chargeId, lookupResult));
      if (outcome != null)
        recordRecovery(outcome.outcome(), outcome.phase(), outcome.failureCode());
    } catch (CustomException e) {
      compensateFailedRecovery(chargeId, snapshot.paymentId(), e, now);
    } catch (RuntimeException e) {
      compensateFailedRecovery(
          chargeId,
          snapshot.paymentId(),
          new CustomException(GlobalErrorCode.SERVER_ERROR, e),
          now);
    }
  }

  private PointChargeSnapshot claimRecoveryTarget(Long chargeId, LocalDateTime now) {
    LocalDateTime leaseUntil = now.plusMinutes(RECOVERY_LEASE_MINUTES);
    return inTransaction(
        () ->
            pointChargeRepository
                .findByIdForUpdate(chargeId)
                .filter(charge -> canClaimRecovery(charge, now))
                .map(
                    charge -> {
                      // recoveryAttemptCount는 lease 횟수가 아니라 실패 후 재시도 횟수로 관리한다.
                      charge.reserveRecovery(leaseUntil);
                      return PointChargeSnapshot.from(charge);
                    })
                .orElse(null));
  }

  private boolean canClaimRecovery(PointCharge charge, LocalDateTime now) {
    return !charge.isLinked()
        && charge.getStatus() == PointChargeStatus.PENDING_CONFIRM
        && charge.getRecoveryAttemptCount() < MAX_RECOVERY_ATTEMPTS
        && (charge.getNextRecoveryAt() == null || !charge.getNextRecoveryAt().isAfter(now));
  }

  private RecoveryOutcome completeIfStillRecoverable(
      Long chargeId, PaymentLookupResult lookupResult) {
    return pointChargeRepository
        .findByIdForUpdate(chargeId)
        .map(
            charge -> {
              if (charge.isLinked() || charge.getStatus() != PointChargeStatus.PENDING_CONFIRM)
                return null;
              if (!PointChargeSnapshot.from(charge).matches(lookupResult)) {
                charge.fail(MISMATCH_FAILURE_CODE, "결제 조회 결과가 충전 요청과 일치하지 않습니다.");
                return new RecoveryOutcome("failed", "complete", "mismatch");
              }
              PointHistory history =
                  pointLedgerService.charge(
                      charge.getMember(), charge.getAmount(), charge.getPaymentId());
              charge.complete(history);
              return new RecoveryOutcome("recovered", "complete", "none");
            })
        .orElse(null);
  }

  private boolean failMismatch(Long chargeId) {
    return inTransaction(
        () ->
            pointChargeRepository
                .findByIdForUpdate(chargeId)
                .map(
                    charge -> {
                      if (charge.isLinked()
                          || charge.getStatus() != PointChargeStatus.PENDING_CONFIRM) return false;
                      charge.fail(MISMATCH_FAILURE_CODE, "결제 조회 결과가 충전 요청과 일치하지 않습니다.");
                      return true;
                    })
                .orElse(false));
  }

  private void compensateFailedRecovery(
      Long chargeId, String paymentId, CustomException failure, LocalDateTime now) {
    String failureCode = failure.getErrorCode().getCode();
    try {
      paymentConfirmClient.cancel(paymentId, "Point charge recovery failed: " + failureCode);
    } catch (RuntimeException cancelFailure) {
      log.error(
          "포인트 충전 복구 실패 후 결제 취소에 실패했습니다. pointChargeId={}, paymentId={}, failureCode={}",
          chargeId,
          paymentId,
          failureCode,
          cancelFailure);
      if (recordRetryAttempt(chargeId, now)) recordRecovery("retried", "complete", "cancel_error");
      return;
    }

    boolean failed =
        inTransaction(
            () ->
                pointChargeRepository
                    .findByIdForUpdate(chargeId)
                    .map(
                        charge -> {
                          if (charge.isLinked()
                              || charge.getStatus() != PointChargeStatus.PENDING_CONFIRM)
                            return false;
                          charge.fail(failureCode, failure.getMessage());
                          return true;
                        })
                    .orElse(false));
    if (failed) recordRecovery("failed", "complete", "ledger_error");
  }

  private boolean recordRetryAttempt(Long chargeId, LocalDateTime now) {
    LocalDateTime nextRecoveryAt = now.plusMinutes(RECOVERY_RETRY_INTERVAL_MINUTES);
    return inTransaction(
        () ->
            pointChargeRepository
                .findByIdForUpdate(chargeId)
                .map(
                    charge -> {
                      if (charge.isLinked()
                          || charge.getStatus() != PointChargeStatus.PENDING_CONFIRM
                          || charge.getRecoveryAttemptCount() >= MAX_RECOVERY_ATTEMPTS)
                        return false;
                      charge.recordRecoveryAttempt(nextRecoveryAt);
                      return true;
                    })
                .orElse(false));
  }

  private void recordRecovery(String outcome, String phase, String failureCode) {
    if (meterRegistry != null)
      Counter.builder("dondok.point.charge.recovery")
          .tags("outcome", outcome, "phase", phase, "failure_code", failureCode)
          .register(meterRegistry)
          .increment();
  }

  private <T> T inTransaction(Supplier<T> supplier) {
    return transactionTemplate.execute(status -> supplier.get());
  }

  private void inTransaction(Runnable runnable) {
    transactionTemplate.executeWithoutResult(status -> runnable.run());
  }

  private record RecoveryOutcome(String outcome, String phase, String failureCode) {}

  private record PointChargeSnapshot(String paymentId, String orderId, Long amount) {

    private static PointChargeSnapshot from(PointCharge charge) {
      return new PointChargeSnapshot(
          charge.getPaymentId(), charge.getOrderId(), charge.getAmount());
    }

    private boolean matches(PaymentLookupResult result) {
      return result != null
          && Objects.equals(paymentId, result.paymentId())
          && Objects.equals(orderId, result.orderId())
          && Objects.equals(amount, result.totalAmount())
          && KRW.equals(result.currency())
          && DONE.equals(result.status());
    }
  }
}
