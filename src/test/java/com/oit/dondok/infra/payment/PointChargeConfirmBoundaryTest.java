package com.oit.dondok.infra.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.point.dto.request.PointChargeRequest;
import com.oit.dondok.domain.point.entity.PointCharge;
import com.oit.dondok.domain.point.entity.PointChargeStatus;
import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.repository.PointChargeRepository;
import com.oit.dondok.domain.point.service.PointChargeService;
import com.oit.dondok.domain.point.service.PointLedgerService;
import com.oit.dondok.global.exception.CustomException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class PointChargeConfirmBoundaryTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  @Mock private MemberRepository memberRepository;
  @Mock private PointChargeRepository pointChargeRepository;
  @Mock private PointLedgerService pointLedgerService;

  private MockRestServiceServer server;
  private PointChargeService service;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://toss.test");
    server = MockRestServiceServer.bindTo(builder).build();
    TossPaymentsConfirmClient client =
        new TossPaymentsConfirmClient(
            new TossPaymentsProperties(
                "https://toss.test", "test-secret", Duration.ofSeconds(1), Duration.ofSeconds(1)),
            builder.build());
    service =
        new PointChargeService(
            memberRepository,
            pointChargeRepository,
            client,
            pointLedgerService,
            new NoopTransactionManager());
  }

  @Test
  void incompleteTossResponseKeepsChargePendingWithoutCancellationOrLedgerMutation() {
    Member member = member();
    AtomicReference<PointCharge> savedCharge = new AtomicReference<>();
    given(memberRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.of(member));
    given(pointChargeRepository.findByPaymentId("payment-key")).willReturn(Optional.empty());
    given(pointChargeRepository.save(any(PointCharge.class)))
        .willAnswer(
            invocation -> {
              PointCharge charge = invocation.getArgument(0);
              savedCharge.set(charge);
              return charge;
            });
    server
        .expect(once(), requestTo("https://toss.test/v1/payments/confirm"))
        .andRespond(
            withSuccess(
                "{\"paymentKey\":\"payment-key\",\"orderId\":\"order-id\","
                    + "\"totalAmount\":10000,\"currency\":\"KRW\"}",
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(
            () ->
                service.charge(
                    MEMBER_UUID, new PointChargeRequest("payment-key", "order-id", 10_000L)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.PAYMENT_CONFIRM_PENDING);

    assertThat(savedCharge.get().getStatus()).isEqualTo(PointChargeStatus.PENDING_CONFIRM);
    assertThat(savedCharge.get().getFailureCode()).isNull();
    then(pointChargeRepository).should(never()).findByPaymentIdForUpdate(any());
    then(pointLedgerService).shouldHaveNoInteractions();
    server.verify();
  }

  private static Member member() {
    Member member = Member.create("member@example.com", "password-hash", "member");
    ReflectionTestUtils.setField(member, "id", 100L);
    ReflectionTestUtils.setField(member, "uuid", MEMBER_UUID);
    return member;
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
