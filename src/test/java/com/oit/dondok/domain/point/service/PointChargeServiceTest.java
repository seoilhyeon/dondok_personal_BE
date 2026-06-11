package com.oit.dondok.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.point.dto.request.PointChargeRequest;
import com.oit.dondok.domain.point.dto.response.PointChargeResponse;
import com.oit.dondok.domain.point.entity.PointCharge;
import com.oit.dondok.domain.point.entity.PointChargeStatus;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.port.PaymentConfirmClient;
import com.oit.dondok.domain.point.port.PaymentConfirmRequest;
import com.oit.dondok.domain.point.port.PaymentConfirmResult;
import com.oit.dondok.domain.point.repository.PointChargeRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class PointChargeServiceTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  private static final Long MEMBER_ID = 100L;

  @Mock private MemberRepository memberRepository;
  @Mock private PointChargeRepository pointChargeRepository;
  @Mock private PaymentConfirmClient paymentConfirmClient;
  @Mock private PointLedgerService pointLedgerService;

  private PointChargeService pointChargeService;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    pointChargeService =
        new PointChargeService(
            memberRepository,
            pointChargeRepository,
            paymentConfirmClient,
            pointLedgerService,
            new NoopTransactionManager());
  }

  @Test
  void chargeConfirmsTossAndLinksLedgerOnFirstSuccess() {
    Member member = member(MEMBER_ID, MEMBER_UUID);
    AtomicReference<PointCharge> pendingRef = new AtomicReference<>();
    PointHistory history = chargeHistory(member, 1L, 10_000L, 25_000L, "charge:payment-key");
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentIdForUpdate("payment-key"))
        .willReturn(Optional.empty())
        .willAnswer(invocation -> Optional.of(pendingRef.get()));
    given(pointChargeRepository.save(any(PointCharge.class)))
        .willAnswer(
            invocation -> {
              PointCharge saved = invocation.getArgument(0);
              pendingRef.set(saved);
              return saved;
            });
    given(
            paymentConfirmClient.confirm(
                new PaymentConfirmRequest("payment-key", "order-id", 10_000L)))
        .willReturn(new PaymentConfirmResult("payment-key", "order-id", 10_000L, "KRW", "DONE"));
    given(pointLedgerService.charge(member, 10_000L, "payment-key")).willReturn(history);

    PointChargeResult result =
        pointChargeService.charge(
            MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L));

    assertThat(result.created()).isTrue();
    PointChargeResponse response = result.response();
    assertThat(response.pointHistoryId()).isEqualTo(1L);
    assertThat(response.memberUuid()).isEqualTo(MEMBER_UUID);
    assertThat(response.amount()).isEqualTo(10_000L);
    assertThat(response.balanceAfter()).isEqualTo(25_000L);
    assertThat(response.transactionType()).isEqualTo(PointTransactionType.POINT_CHARGE);
    PointCharge pending = pendingRef.get();
    assertThat(pending.getStatus()).isEqualTo(PointChargeStatus.COMPLETED);
    assertThat(pending.getPointHistory()).isEqualTo(history);
  }

  @Test
  void linkedDuplicateReturnsExistingResultWithoutCallingToss() {
    Member member = member(MEMBER_ID, MEMBER_UUID);
    PointHistory history = chargeHistory(member, 1L, 10_000L, 25_000L, "charge:payment-key");
    PointCharge completed = PointCharge.createPending(member, "payment-key", "order-id", 10_000L);
    completed.complete(history);
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentIdForUpdate("payment-key"))
        .willReturn(Optional.of(completed));

    PointChargeResult result =
        pointChargeService.charge(
            MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L));

    assertThat(result.created()).isFalse();
    assertThat(result.response().pointHistoryId()).isEqualTo(1L);
    then(paymentConfirmClient).should(never()).confirm(org.mockito.ArgumentMatchers.any());
    then(pointLedgerService)
        .should(never())
        .charge(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void linkedDuplicateWithDifferentOrderConflicts() {
    Member member = member(MEMBER_ID, MEMBER_UUID);
    PointHistory history = chargeHistory(member, 1L, 10_000L, 25_000L, "charge:payment-key");
    PointCharge completed = PointCharge.createPending(member, "payment-key", "order-id", 10_000L);
    completed.complete(history);
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentIdForUpdate("payment-key"))
        .willReturn(Optional.of(completed));

    assertThatThrownBy(
            () ->
                pointChargeService.charge(
                    MEMBER_UUID, new PointChargeRequest("payment-key", "other-order", 10_000L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.IDEMPOTENCY_CONFLICT);
  }

  @Test
  void unlinkedDuplicateOwnedByDifferentMemberConflictsWithoutCallingToss() {
    Member member = member(MEMBER_ID, MEMBER_UUID);
    Member otherMember = member(200L, UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c902"));
    PointCharge failed = PointCharge.createPending(otherMember, "payment-key", "order-id", 10_000L);
    failed.fail("PAYMENT_CONFIRM_FAILED", "failed");
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentIdForUpdate("payment-key"))
        .willReturn(Optional.of(failed));

    assertThatThrownBy(
            () ->
                pointChargeService.charge(
                    MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.IDEMPOTENCY_CONFLICT);
    then(paymentConfirmClient).should(never()).confirm(org.mockito.ArgumentMatchers.any());
    then(pointLedgerService)
        .should(never())
        .charge(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void concurrentDuplicatePaymentIdInsertReturnsExistingResultWithoutCallingToss() {
    Member member = member(MEMBER_ID, MEMBER_UUID);
    PointHistory history = chargeHistory(member, 1L, 10_000L, 25_000L, "charge:payment-key");
    PointCharge completed = PointCharge.createPending(member, "payment-key", "order-id", 10_000L);
    completed.complete(history);
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentIdForUpdate("payment-key"))
        .willReturn(Optional.empty(), Optional.of(completed));
    given(pointChargeRepository.save(any(PointCharge.class)))
        .willThrow(new DataIntegrityViolationException("uk_point_charge_payment_id"));

    PointChargeResult result =
        pointChargeService.charge(
            MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L));

    assertThat(result.created()).isFalse();
    assertThat(result.response().pointHistoryId()).isEqualTo(1L);
    then(paymentConfirmClient).should(never()).confirm(org.mockito.ArgumentMatchers.any());
    then(pointLedgerService)
        .should(never())
        .charge(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void orderIdUniqueViolationMapsToIdempotencyConflict() {
    Member member = member(MEMBER_ID, MEMBER_UUID);
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentIdForUpdate("payment-key"))
        .willReturn(Optional.empty(), Optional.empty());
    given(pointChargeRepository.save(any(PointCharge.class)))
        .willThrow(new DataIntegrityViolationException("uk_point_charge_order_id"));

    assertThatThrownBy(
            () ->
                pointChargeService.charge(
                    MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.IDEMPOTENCY_CONFLICT);
    then(paymentConfirmClient).should(never()).confirm(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void unlinkedSameMemberWithDifferentCanonicalInputConflictsWithoutCallingToss() {
    Member member = member(MEMBER_ID, MEMBER_UUID);
    PointCharge failed = PointCharge.createPending(member, "payment-key", "wrong-order", 9_000L);
    failed.fail("PAYMENT_CONFIRM_FAILED", "failed");
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentIdForUpdate("payment-key"))
        .willReturn(Optional.of(failed));

    assertThatThrownBy(
            () ->
                pointChargeService.charge(
                    MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.IDEMPOTENCY_CONFLICT);
    then(paymentConfirmClient).should(never()).confirm(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void duplicateAfterFirstSuccessReturnsSameLedgerLinkWithoutSecondTossOrLedgerMutation() {
    Member member = member(MEMBER_ID, MEMBER_UUID);
    AtomicReference<PointCharge> pendingRef = new AtomicReference<>();
    PointHistory history = chargeHistory(member, 1L, 10_000L, 25_000L, "charge:payment-key");
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentIdForUpdate("payment-key"))
        .willReturn(Optional.empty())
        .willAnswer(invocation -> Optional.of(pendingRef.get()))
        .willAnswer(invocation -> Optional.of(pendingRef.get()));
    given(pointChargeRepository.save(any(PointCharge.class)))
        .willAnswer(
            invocation -> {
              PointCharge saved = invocation.getArgument(0);
              pendingRef.set(saved);
              return saved;
            });
    given(
            paymentConfirmClient.confirm(
                new PaymentConfirmRequest("payment-key", "order-id", 10_000L)))
        .willReturn(new PaymentConfirmResult("payment-key", "order-id", 10_000L, "KRW", "DONE"));
    given(pointLedgerService.charge(member, 10_000L, "payment-key")).willReturn(history);

    PointChargeResult first =
        pointChargeService.charge(
            MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L));
    PointChargeResult duplicate =
        pointChargeService.charge(
            MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L));

    assertThat(first.created()).isTrue();
    assertThat(duplicate.created()).isFalse();
    assertThat(duplicate.response().pointHistoryId()).isEqualTo(first.response().pointHistoryId());
    assertThat(pendingRef.get().getPointHistory()).isEqualTo(history);
    verify(paymentConfirmClient, times(1))
        .confirm(new PaymentConfirmRequest("payment-key", "order-id", 10_000L));
    verify(pointLedgerService, times(1)).charge(member, 10_000L, "payment-key");
  }

  @Test
  void tossMismatchCancelsPaymentRecordsFailureAndDoesNotMutateLedger() {
    assertConfirmMismatchDoesNotMutateLedger(
        new PaymentConfirmResult("payment-key", "order-id", 9_000L, "KRW", "DONE"));
  }

  @Test
  void tossPaymentKeyMismatchCancelsPaymentAndDoesNotMutateLedger() {
    assertConfirmMismatchDoesNotMutateLedger(
        new PaymentConfirmResult("other-payment-key", "order-id", 10_000L, "KRW", "DONE"));
  }

  @Test
  void tossOrderIdMismatchCancelsPaymentAndDoesNotMutateLedger() {
    assertConfirmMismatchDoesNotMutateLedger(
        new PaymentConfirmResult("payment-key", "other-order-id", 10_000L, "KRW", "DONE"));
  }

  @Test
  void tossCurrencyMismatchCancelsPaymentAndDoesNotMutateLedger() {
    assertConfirmMismatchDoesNotMutateLedger(
        new PaymentConfirmResult("payment-key", "order-id", 10_000L, "USD", "DONE"));
  }

  @Test
  void tossStatusMismatchCancelsPaymentAndDoesNotMutateLedger() {
    assertConfirmMismatchDoesNotMutateLedger(
        new PaymentConfirmResult("payment-key", "order-id", 10_000L, "KRW", "CANCELED"));
  }

  private void assertConfirmMismatchDoesNotMutateLedger(PaymentConfirmResult confirmResult) {
    Member member = member(MEMBER_ID, MEMBER_UUID);
    AtomicReference<PointCharge> pendingRef = new AtomicReference<>();
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentIdForUpdate("payment-key"))
        .willReturn(Optional.empty())
        .willAnswer(invocation -> Optional.of(pendingRef.get()));
    given(pointChargeRepository.save(any(PointCharge.class)))
        .willAnswer(
            invocation -> {
              PointCharge saved = invocation.getArgument(0);
              pendingRef.set(saved);
              return saved;
            });
    given(
            paymentConfirmClient.confirm(
                new PaymentConfirmRequest("payment-key", "order-id", 10_000L)))
        .willReturn(confirmResult);

    assertThatThrownBy(
            () ->
                pointChargeService.charge(
                    MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.PAYMENT_CONFIRM_MISMATCH);
    PointCharge pending = pendingRef.get();
    assertThat(pending.getStatus()).isEqualTo(PointChargeStatus.CONFIRM_FAILED);
    verify(paymentConfirmClient).cancel("payment-key", "Point charge confirmation mismatch");
    then(pointLedgerService)
        .should(never())
        .charge(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void directServiceCallRejectsInvalidStepAmountBeforeCallingToss() {
    assertThatThrownBy(
            () ->
                pointChargeService.charge(
                    MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 1_500L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_AMOUNT);
    then(paymentConfirmClient).should(never()).confirm(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void directServiceCallRejectsBlankPaymentIdAsInvalidInputBeforeCallingToss() {
    assertThatThrownBy(
            () ->
                pointChargeService.charge(
                    MEMBER_UUID, new PointChargeRequest(" ", "order-id", 10_000L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT);
    then(paymentConfirmClient).should(never()).confirm(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void staleInFlightConfirmDoesNotCreditWhenCanonicalFieldsChangedBeforeLink() {
    Member member = member(MEMBER_ID, MEMBER_UUID);
    PointCharge stale = PointCharge.createPending(member, "payment-key", "order-id", 10_000L);
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentIdForUpdate("payment-key"))
        .willReturn(Optional.of(stale), Optional.of(stale));
    given(
            paymentConfirmClient.confirm(
                new PaymentConfirmRequest("payment-key", "order-id", 10_000L)))
        .willAnswer(
            invocation -> {
              ReflectionTestUtils.setField(stale, "orderId", "corrected-order");
              ReflectionTestUtils.setField(stale, "amount", 20_000L);
              return new PaymentConfirmResult("payment-key", "order-id", 10_000L, "KRW", "DONE");
            });

    assertThatThrownBy(
            () ->
                pointChargeService.charge(
                    MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.PAYMENT_CONFIRM_STALE);
    then(pointLedgerService)
        .should(never())
        .charge(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void permanentLedgerFailureAfterTossDoneCancelsPaymentAndRecordsFailure() {
    Member member = member(MEMBER_ID, MEMBER_UUID);
    AtomicReference<PointCharge> pendingRef = new AtomicReference<>();
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentIdForUpdate("payment-key"))
        .willReturn(Optional.empty())
        .willAnswer(invocation -> Optional.of(pendingRef.get()))
        .willAnswer(invocation -> Optional.of(pendingRef.get()));
    given(pointChargeRepository.save(any(PointCharge.class)))
        .willAnswer(
            invocation -> {
              PointCharge saved = invocation.getArgument(0);
              pendingRef.set(saved);
              return saved;
            });
    given(
            paymentConfirmClient.confirm(
                new PaymentConfirmRequest("payment-key", "order-id", 10_000L)))
        .willReturn(new PaymentConfirmResult("payment-key", "order-id", 10_000L, "KRW", "DONE"));
    given(pointLedgerService.charge(member, 10_000L, "payment-key"))
        .willThrow(new CustomException(PointErrorCode.POINT_ACCOUNT_NOT_FOUND));

    assertThatThrownBy(
            () ->
                pointChargeService.charge(
                    MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.POINT_ACCOUNT_NOT_FOUND);
    PointCharge pending = pendingRef.get();
    assertThat(pending.getStatus()).isEqualTo(PointChargeStatus.CONFIRM_FAILED);
    assertThat(pending.getFailureCode()).isEqualTo("POINT_ACCOUNT_NOT_FOUND");
    verify(paymentConfirmClient)
        .cancel("payment-key", "Point charge ledger completion failed: POINT_ACCOUNT_NOT_FOUND");
  }

  @Test
  void transientLedgerFailureAfterTossDoneKeepsPendingForRetryWithoutCancel() {
    Member member = member(MEMBER_ID, MEMBER_UUID);
    AtomicReference<PointCharge> pendingRef = new AtomicReference<>();
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentIdForUpdate("payment-key"))
        .willReturn(Optional.empty())
        .willAnswer(invocation -> Optional.of(pendingRef.get()));
    given(pointChargeRepository.save(any(PointCharge.class)))
        .willAnswer(
            invocation -> {
              PointCharge saved = invocation.getArgument(0);
              pendingRef.set(saved);
              return saved;
            });
    given(
            paymentConfirmClient.confirm(
                new PaymentConfirmRequest("payment-key", "order-id", 10_000L)))
        .willReturn(new PaymentConfirmResult("payment-key", "order-id", 10_000L, "KRW", "DONE"));
    given(pointLedgerService.charge(member, 10_000L, "payment-key"))
        .willThrow(new IllegalStateException("database temporarily unavailable"));

    assertThatThrownBy(
            () ->
                pointChargeService.charge(
                    MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(pendingRef.get().getStatus()).isEqualTo(PointChargeStatus.PENDING_CONFIRM);
    then(paymentConfirmClient).should(never()).cancel(any(), any());
  }

  private static Member member(Long id, UUID uuid) {
    Member member = Member.create("member-" + id + "@example.com", "password-hash", "member" + id);
    ReflectionTestUtils.setField(member, "id", id);
    ReflectionTestUtils.setField(member, "uuid", uuid);
    return member;
  }

  private static PointHistory chargeHistory(
      Member member, Long id, long amount, long availableAfter, String key) {
    PointHistory history =
        PointHistory.create(
            member,
            amount,
            availableAfter,
            0L,
            0L,
            PointTransactionType.POINT_CHARGE,
            PointReferenceType.POINT_CHARGE,
            0L,
            key);
    ReflectionTestUtils.setField(history, "id", id);
    ReflectionTestUtils.setField(history, "createdAt", LocalDateTime.of(2026, 6, 10, 16, 20, 5));
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
