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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
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
  private static final int MAX_RECOVERY_ATTEMPTS = 12;

  private final PointChargeRepository pointChargeRepository;
  private final PaymentConfirmClient paymentConfirmClient;
  private final PaymentLookupClient paymentLookupClient;
  private final PointLedgerService pointLedgerService;
  private final TransactionTemplate transactionTemplate;

  public PointChargeRecoveryService(
      PointChargeRepository pointChargeRepository,
      PaymentConfirmClient paymentConfirmClient,
      PaymentLookupClient paymentLookupClient,
      PointLedgerService pointLedgerService,
      PlatformTransactionManager transactionManager) {
    this.pointChargeRepository = pointChargeRepository;
    this.paymentConfirmClient = paymentConfirmClient;
    this.paymentLookupClient = paymentLookupClient;
    this.pointLedgerService = pointLedgerService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
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
      recordRetryAttempt(chargeId, now);
      return;
    }

    if (!DONE.equals(lookupResult.status())) {
      log.info(
          "포인트 충전 복구 대상 결제가 승인 완료 상태가 아닙니다. pointChargeId={}, paymentId={}, status={}",
          chargeId,
          snapshot.paymentId(),
          lookupResult.status());
      recordRetryAttempt(chargeId, now);
      return;
    }

    if (!snapshot.matches(lookupResult)) {
      failMismatch(chargeId);
      return;
    }

    try {
      inTransaction(() -> completeIfStillRecoverable(chargeId, lookupResult));
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
    LocalDateTime leaseUntil = now.plusMinutes(RECOVERY_GRACE_MINUTES);
    return inTransaction(
        () ->
            pointChargeRepository
                .findByIdForUpdate(chargeId)
                .filter(charge -> canClaimRecovery(charge, now))
                .map(
                    charge -> {
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

  private void completeIfStillRecoverable(Long chargeId, PaymentLookupResult lookupResult) {
    pointChargeRepository
        .findByIdForUpdate(chargeId)
        .ifPresent(
            charge -> {
              if (charge.isLinked() || charge.getStatus() != PointChargeStatus.PENDING_CONFIRM) {
                return;
              }
              if (!PointChargeSnapshot.from(charge).matches(lookupResult)) {
                charge.fail(MISMATCH_FAILURE_CODE, "결제 조회 결과가 충전 요청과 일치하지 않습니다.");
                return;
              }
              PointHistory history =
                  pointLedgerService.charge(
                      charge.getMember(), charge.getAmount(), charge.getPaymentId());
              charge.complete(history);
            });
  }

  private void failMismatch(Long chargeId) {
    inTransaction(
        () ->
            pointChargeRepository
                .findByIdForUpdate(chargeId)
                .ifPresent(
                    charge -> {
                      if (!charge.isLinked()
                          && charge.getStatus() == PointChargeStatus.PENDING_CONFIRM) {
                        charge.fail(MISMATCH_FAILURE_CODE, "결제 조회 결과가 충전 요청과 일치하지 않습니다.");
                      }
                    }));
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
      recordRetryAttempt(chargeId, now);
      return;
    }

    inTransaction(
        () ->
            pointChargeRepository
                .findByIdForUpdate(chargeId)
                .ifPresent(
                    charge -> {
                      if (!charge.isLinked()
                          && charge.getStatus() == PointChargeStatus.PENDING_CONFIRM) {
                        charge.fail(failureCode, failure.getMessage());
                      }
                    }));
  }

  private void recordRetryAttempt(Long chargeId, LocalDateTime now) {
    LocalDateTime nextRecoveryAt = now.plusMinutes(RECOVERY_GRACE_MINUTES);
    inTransaction(
        () ->
            pointChargeRepository
                .findByIdForUpdate(chargeId)
                .ifPresent(
                    charge -> {
                      if (!charge.isLinked()
                          && charge.getStatus() == PointChargeStatus.PENDING_CONFIRM
                          && charge.getRecoveryAttemptCount() < MAX_RECOVERY_ATTEMPTS) {
                        charge.recordRecoveryAttempt(nextRecoveryAt);
                      }
                    }));
  }

  private <T> T inTransaction(Supplier<T> supplier) {
    return transactionTemplate.execute(status -> supplier.get());
  }

  private void inTransaction(Runnable runnable) {
    transactionTemplate.executeWithoutResult(status -> runnable.run());
  }

  private record PointChargeSnapshot(
      PointChargeStatus status, boolean linked, String paymentId, String orderId, Long amount) {

    private static PointChargeSnapshot from(PointCharge charge) {
      return new PointChargeSnapshot(
          charge.getStatus(),
          charge.isLinked(),
          charge.getPaymentId(),
          charge.getOrderId(),
          charge.getAmount());
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
