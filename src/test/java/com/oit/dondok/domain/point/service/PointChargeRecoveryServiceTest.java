package com.oit.dondok.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointCharge;
import com.oit.dondok.domain.point.entity.PointChargeStatus;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.port.PaymentConfirmClient;
import com.oit.dondok.domain.point.port.PaymentLookupClient;
import com.oit.dondok.domain.point.port.PaymentLookupResult;
import com.oit.dondok.domain.point.repository.PointChargeRepository;
import com.oit.dondok.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class PointChargeRecoveryServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 18, 12, 0);
  private static final int MAX_RECOVERY_ATTEMPTS = 12;

  @Mock private PointChargeRepository pointChargeRepository;
  @Mock private PaymentConfirmClient paymentConfirmClient;
  @Mock private PaymentLookupClient paymentLookupClient;
  @Mock private PointLedgerService pointLedgerService;

  private PointChargeRecoveryService recoveryService;

  @BeforeEach
  void setUp() {
    recoveryService =
        new PointChargeRecoveryService(
            pointChargeRepository,
            paymentConfirmClient,
            paymentLookupClient,
            pointLedgerService,
            new NoopTransactionManager());
  }

  @Test
  void donePaymentLookupCompletesPendingChargeWithLedgerHistory() {
    Member member = member();
    PointCharge charge = PointCharge.createPending(member, "payment-key", "order-id", 10_000L);
    PointHistory history = chargeHistory(member, 1L, 10_000L, 25_000L, "charge:payment-key");
    givenRecoveryTargetIds(1L);
    given(paymentLookupClient.lookup("payment-key"))
        .willReturn(new PaymentLookupResult("payment-key", "order-id", 10_000L, "KRW", "DONE"));
    given(pointChargeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(charge));
    given(pointLedgerService.charge(member, 10_000L, "payment-key")).willReturn(history);

    recoveryService.runRecoveryBatch(NOW);

    assertThat(charge.getStatus()).isEqualTo(PointChargeStatus.COMPLETED);
    assertThat(charge.getPointHistory()).isEqualTo(history);
  }

  @Test
  void nonDonePaymentLookupDoesNotMutateLedgerOrCharge() {
    Member member = member();
    PointCharge charge = PointCharge.createPending(member, "payment-key", "order-id", 10_000L);
    givenRecoveryTargetIds(1L);
    given(pointChargeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(charge));
    given(paymentLookupClient.lookup("payment-key"))
        .willReturn(new PaymentLookupResult("payment-key", "order-id", 10_000L, "KRW", "CANCELED"));

    recoveryService.runRecoveryBatch(NOW);

    assertThat(charge.getStatus()).isEqualTo(PointChargeStatus.PENDING_CONFIRM);
    assertThat(charge.getPointHistory()).isNull();
    assertThat(charge.getRecoveryAttemptCount()).isEqualTo(1);
    assertThat(charge.getNextRecoveryAt()).isEqualTo(NOW.plusMinutes(5));
    then(pointLedgerService).should(never()).charge(member, 10_000L, "payment-key");
  }

  @Test
  void lookupFailureKeepsChargePendingForNextRecoveryBatch() {
    Member member = member();
    PointCharge charge = PointCharge.createPending(member, "payment-key", "order-id", 10_000L);
    givenRecoveryTargetIds(1L);
    given(pointChargeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(charge));
    given(paymentLookupClient.lookup("payment-key"))
        .willThrow(new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED));

    recoveryService.runRecoveryBatch(NOW);

    assertThat(charge.getStatus()).isEqualTo(PointChargeStatus.PENDING_CONFIRM);
    assertThat(charge.getPointHistory()).isNull();
    assertThat(charge.getRecoveryAttemptCount()).isEqualTo(1);
    assertThat(charge.getNextRecoveryAt()).isEqualTo(NOW.plusMinutes(5));
    then(pointLedgerService).should(never()).charge(member, 10_000L, "payment-key");
  }

  @Test
  void nullLookupResultKeepsChargePendingForNextRecoveryBatch() {
    Member member = member();
    PointCharge charge = PointCharge.createPending(member, "payment-key", "order-id", 10_000L);
    givenRecoveryTargetIds(1L);
    given(pointChargeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(charge));
    given(paymentLookupClient.lookup("payment-key")).willReturn(null);

    recoveryService.runRecoveryBatch(NOW);

    assertThat(charge.getStatus()).isEqualTo(PointChargeStatus.PENDING_CONFIRM);
    assertThat(charge.getPointHistory()).isNull();
    assertThat(charge.getRecoveryAttemptCount()).isEqualTo(1);
    assertThat(charge.getNextRecoveryAt()).isEqualTo(NOW.plusMinutes(5));
    then(pointLedgerService).should(never()).charge(member, 10_000L, "payment-key");
  }

  @Test
  void canonicalMismatchMarksChargeFailedWithoutLedgerMutation() {
    Member member = member();
    PointCharge charge = PointCharge.createPending(member, "payment-key", "order-id", 10_000L);
    givenRecoveryTargetIds(1L);
    given(paymentLookupClient.lookup("payment-key"))
        .willReturn(new PaymentLookupResult("payment-key", "other-order", 10_000L, "KRW", "DONE"));
    given(pointChargeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(charge));

    recoveryService.runRecoveryBatch(NOW);

    assertThat(charge.getStatus()).isEqualTo(PointChargeStatus.CONFIRM_FAILED);
    assertThat(charge.getFailureCode()).isEqualTo("PAYMENT_LOOKUP_MISMATCH");
    assertThat(charge.getPointHistory()).isNull();
    then(pointLedgerService).should(never()).charge(member, 10_000L, "payment-key");
  }

  @Test
  void permanentLedgerFailureCancelsPaymentAndRecordsFailure() {
    Member member = member();
    PointCharge charge = PointCharge.createPending(member, "payment-key", "order-id", 10_000L);
    givenRecoveryTargetIds(1L);
    given(paymentLookupClient.lookup("payment-key"))
        .willReturn(new PaymentLookupResult("payment-key", "order-id", 10_000L, "KRW", "DONE"));
    given(pointChargeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(charge));
    given(pointLedgerService.charge(member, 10_000L, "payment-key"))
        .willThrow(new CustomException(PointErrorCode.POINT_ACCOUNT_NOT_FOUND));

    recoveryService.runRecoveryBatch(NOW);

    assertThat(charge.getStatus()).isEqualTo(PointChargeStatus.CONFIRM_FAILED);
    assertThat(charge.getFailureCode()).isEqualTo("POINT_ACCOUNT_NOT_FOUND");
    then(paymentConfirmClient)
        .should()
        .cancel("payment-key", "Point charge recovery failed: POINT_ACCOUNT_NOT_FOUND");
  }

  @Test
  void unexpectedLedgerFailureIsCompensatedAndDoesNotStopNextTarget() {
    Member member = member();
    PointCharge failedCharge =
        PointCharge.createPending(member, "failed-payment-key", "failed-order-id", 10_000L);
    PointCharge nextCharge =
        PointCharge.createPending(member, "next-payment-key", "next-order-id", 20_000L);
    PointHistory history = chargeHistory(member, 2L, 20_000L, 30_000L, "charge:next-payment-key");
    given(
            pointChargeRepository.findRecoveryTargetIdsAfterId(
                PointChargeStatus.PENDING_CONFIRM,
                NOW.minusMinutes(5),
                0L,
                MAX_RECOVERY_ATTEMPTS,
                NOW,
                Pageable.ofSize(50)))
        .willReturn(List.of(1L, 2L));
    given(
            pointChargeRepository.findRecoveryTargetIdsAfterId(
                PointChargeStatus.PENDING_CONFIRM,
                NOW.minusMinutes(5),
                2L,
                MAX_RECOVERY_ATTEMPTS,
                NOW,
                Pageable.ofSize(50)))
        .willReturn(List.of());
    given(paymentLookupClient.lookup("failed-payment-key"))
        .willReturn(
            new PaymentLookupResult(
                "failed-payment-key", "failed-order-id", 10_000L, "KRW", "DONE"));
    given(paymentLookupClient.lookup("next-payment-key"))
        .willReturn(
            new PaymentLookupResult("next-payment-key", "next-order-id", 20_000L, "KRW", "DONE"));
    given(pointChargeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(failedCharge));
    given(pointChargeRepository.findByIdForUpdate(2L)).willReturn(Optional.of(nextCharge));
    given(pointLedgerService.charge(member, 10_000L, "failed-payment-key"))
        .willThrow(new RuntimeException("database timeout"));
    given(pointLedgerService.charge(member, 20_000L, "next-payment-key")).willReturn(history);

    recoveryService.runRecoveryBatch(NOW);

    assertThat(failedCharge.getStatus()).isEqualTo(PointChargeStatus.CONFIRM_FAILED);
    assertThat(failedCharge.getFailureCode()).isEqualTo("SERVER_ERROR");
    assertThat(nextCharge.getStatus()).isEqualTo(PointChargeStatus.COMPLETED);
    assertThat(nextCharge.getPointHistory()).isEqualTo(history);
    then(paymentConfirmClient)
        .should()
        .cancel("failed-payment-key", "Point charge recovery failed: SERVER_ERROR");
  }

  @Test
  void cancelFailureKeepsChargePendingForNextRecoveryBatch() {
    Member member = member();
    PointCharge charge = PointCharge.createPending(member, "payment-key", "order-id", 10_000L);
    givenRecoveryTargetIds(1L);
    given(paymentLookupClient.lookup("payment-key"))
        .willReturn(new PaymentLookupResult("payment-key", "order-id", 10_000L, "KRW", "DONE"));
    given(pointChargeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(charge));
    given(pointLedgerService.charge(member, 10_000L, "payment-key"))
        .willThrow(new CustomException(PointErrorCode.POINT_ACCOUNT_NOT_FOUND));
    willThrow(new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED))
        .given(paymentConfirmClient)
        .cancel("payment-key", "Point charge recovery failed: POINT_ACCOUNT_NOT_FOUND");

    recoveryService.runRecoveryBatch(NOW);

    assertThat(charge.getStatus()).isEqualTo(PointChargeStatus.PENDING_CONFIRM);
    assertThat(charge.getPointHistory()).isNull();
    assertThat(charge.getRecoveryAttemptCount()).isEqualTo(1);
    assertThat(charge.getNextRecoveryAt()).isEqualTo(NOW.plusMinutes(5));
  }

  @Test
  void cursorScanContinuesAfterStuckEarlierTargets() {
    Member member = member();
    PointCharge pendingCharge =
        PointCharge.createPending(member, "pending-payment-key", "pending-order-id", 10_000L);
    PointCharge doneCharge =
        PointCharge.createPending(member, "done-payment-key", "done-order-id", 20_000L);
    PointHistory history = chargeHistory(member, 2L, 20_000L, 30_000L, "charge:done-payment-key");
    given(
            pointChargeRepository.findRecoveryTargetIdsAfterId(
                PointChargeStatus.PENDING_CONFIRM,
                NOW.minusMinutes(5),
                0L,
                MAX_RECOVERY_ATTEMPTS,
                NOW,
                Pageable.ofSize(50)))
        .willReturn(List.of(1L));
    given(
            pointChargeRepository.findRecoveryTargetIdsAfterId(
                PointChargeStatus.PENDING_CONFIRM,
                NOW.minusMinutes(5),
                1L,
                MAX_RECOVERY_ATTEMPTS,
                NOW,
                Pageable.ofSize(50)))
        .willReturn(List.of(2L));
    given(
            pointChargeRepository.findRecoveryTargetIdsAfterId(
                PointChargeStatus.PENDING_CONFIRM,
                NOW.minusMinutes(5),
                2L,
                MAX_RECOVERY_ATTEMPTS,
                NOW,
                Pageable.ofSize(50)))
        .willReturn(List.of());
    given(paymentLookupClient.lookup("pending-payment-key"))
        .willReturn(
            new PaymentLookupResult(
                "pending-payment-key", "pending-order-id", 10_000L, "KRW", "READY"));
    given(paymentLookupClient.lookup("done-payment-key"))
        .willReturn(
            new PaymentLookupResult("done-payment-key", "done-order-id", 20_000L, "KRW", "DONE"));
    given(pointChargeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingCharge));
    given(pointChargeRepository.findByIdForUpdate(2L)).willReturn(Optional.of(doneCharge));
    given(pointLedgerService.charge(member, 20_000L, "done-payment-key")).willReturn(history);

    recoveryService.runRecoveryBatch(NOW);

    assertThat(pendingCharge.getStatus()).isEqualTo(PointChargeStatus.PENDING_CONFIRM);
    assertThat(doneCharge.getStatus()).isEqualTo(PointChargeStatus.COMPLETED);
    assertThat(doneCharge.getPointHistory()).isEqualTo(history);
  }

  @Test
  void claimSkipsAlreadyLeasedChargeBeforePaymentLookup() {
    Member member = member();
    PointCharge charge = PointCharge.createPending(member, "payment-key", "order-id", 10_000L);
    charge.reserveRecovery(NOW.plusMinutes(5));
    givenRecoveryTargetIds(1L);
    given(pointChargeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(charge));

    recoveryService.runRecoveryBatch(NOW);

    assertThat(charge.getStatus()).isEqualTo(PointChargeStatus.PENDING_CONFIRM);
    assertThat(charge.getPointHistory()).isNull();
    then(paymentLookupClient).should(never()).lookup("payment-key");
    then(pointLedgerService).should(never()).charge(member, 10_000L, "payment-key");
  }

  private static Member member() {
    Member member =
        Member.create(
            "charge-recovery-member@example.com", "password-hash", "chargeRecoveryMember");
    org.springframework.test.util.ReflectionTestUtils.setField(member, "id", 100L);
    org.springframework.test.util.ReflectionTestUtils.setField(
        member, "uuid", UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901"));
    return member;
  }

  private void givenRecoveryTargetIds(Long... targetIds) {
    List<Long> ids = List.of(targetIds);
    given(
            pointChargeRepository.findRecoveryTargetIdsAfterId(
                PointChargeStatus.PENDING_CONFIRM,
                NOW.minusMinutes(5),
                0L,
                MAX_RECOVERY_ATTEMPTS,
                NOW,
                Pageable.ofSize(50)))
        .willReturn(ids);
    if (!ids.isEmpty()) {
      given(
              pointChargeRepository.findRecoveryTargetIdsAfterId(
                  PointChargeStatus.PENDING_CONFIRM,
                  NOW.minusMinutes(5),
                  ids.get(ids.size() - 1),
                  MAX_RECOVERY_ATTEMPTS,
                  NOW,
                  Pageable.ofSize(50)))
          .willReturn(List.of());
    }
  }

  private static PointHistory chargeHistory(
      Member member, Long id, Long amount, Long availableAfter, String idempotencyKey) {
    PointHistory history =
        PointHistory.create(
            member,
            amount,
            availableAfter,
            0L,
            0L,
            com.oit.dondok.domain.point.entity.PointTransactionType.POINT_CHARGE,
            com.oit.dondok.domain.point.entity.PointReferenceType.POINT_CHARGE,
            0L,
            idempotencyKey);
    org.springframework.test.util.ReflectionTestUtils.setField(history, "id", id);
    return history;
  }

  private static class NoopTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {}

    @Override
    public void rollback(TransactionStatus status) {}
  }
}
