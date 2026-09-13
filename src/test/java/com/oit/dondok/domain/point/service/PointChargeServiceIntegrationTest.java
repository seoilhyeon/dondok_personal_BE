package com.oit.dondok.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oit.dondok.IntegrationTest;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.point.dto.request.PointChargeRequest;
import com.oit.dondok.domain.point.entity.PointCharge;
import com.oit.dondok.domain.point.entity.PointChargeStatus;
import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.port.PaymentConfirmRequest;
import com.oit.dondok.domain.point.repository.PointChargeRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.infra.payment.TossPaymentsConfirmClient;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@TestPropertySource(
    properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
class PointChargeServiceIntegrationTest {

  @Autowired private MemberRepository memberRepository;
  @Autowired private PointChargeRepository pointChargeRepository;
  @Autowired private PointChargeService pointChargeService;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockBean private TossPaymentsConfirmClient tossPaymentsConfirmClient;

  @Test
  void exhaustedRecoveryChargeAllowsSameCanonicalRetryAndPreservesPendingState() {
    String paymentId = "pending-retry-payment";
    String orderId = "pending-retry-order";
    long amount = 10_000L;
    int exhaustedRecoveryAttempts = 12;
    LocalDateTime nextRecoveryAt = LocalDateTime.of(2026, 6, 18, 12, 5);
    UUID memberUuid =
        persistPendingCharge(paymentId, orderId, amount, exhaustedRecoveryAttempts, nextRecoveryAt);
    CustomException pending = new CustomException(PointErrorCode.PAYMENT_CONFIRM_PENDING);
    PaymentConfirmRequest confirmRequest = new PaymentConfirmRequest(paymentId, orderId, amount);
    when(tossPaymentsConfirmClient.confirm(confirmRequest)).thenThrow(pending);

    assertThatThrownBy(
            () ->
                pointChargeService.charge(
                    memberUuid, new PointChargeRequest(paymentId, orderId, amount)))
        .isSameAs(pending);

    PointCharge persisted = findCharge(paymentId);
    assertThat(persisted.getStatus()).isEqualTo(PointChargeStatus.PENDING_CONFIRM);
    assertThat(persisted.getRecoveryAttemptCount()).isEqualTo(exhaustedRecoveryAttempts);
    assertThat(persisted.getNextRecoveryAt()).isEqualTo(nextRecoveryAt);
    assertThat(persisted.getFailureCode()).isNull();
    assertThat(persisted.getFailureMessage()).isNull();
    verify(tossPaymentsConfirmClient).confirm(confirmRequest);
    verify(tossPaymentsConfirmClient, never()).cancel(any(), any());
  }

  private UUID persistPendingCharge(
      String paymentId,
      String orderId,
      long amount,
      int recoveryAttemptCount,
      LocalDateTime nextRecoveryAt) {
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              Member member =
                  memberRepository.save(
                      Member.create(
                          "pending-retry@example.com", "password", "pending-retry-member"));
              PointCharge charge = PointCharge.createPending(member, paymentId, orderId, amount);
              for (int attempt = 0; attempt < recoveryAttemptCount; attempt++) {
                charge.recordRecoveryAttempt(nextRecoveryAt);
              }
              pointChargeRepository.save(charge);
              return member.getUuid();
            });
  }

  private PointCharge findCharge(String paymentId) {
    return new TransactionTemplate(transactionManager)
        .execute(status -> pointChargeRepository.findByPaymentId(paymentId).orElseThrow());
  }
}
