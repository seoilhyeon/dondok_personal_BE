package com.oit.dondok.infra.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.port.PaymentConfirmRequest;
import com.oit.dondok.domain.point.port.PaymentConfirmResult;
import com.oit.dondok.global.exception.CustomException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

class TossPaymentsConfirmClientTest {

  private static final PaymentConfirmRequest REQUEST =
      new PaymentConfirmRequest("payment-key", "order-id", 10_000L);
  public static final String CONFIRM_URI = "https://toss.test/v1/payments/confirm";

  private MockRestServiceServer server;
  private TossPaymentsConfirmClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://toss.test");
    server = MockRestServiceServer.bindTo(builder).build();
    client =
        new TossPaymentsConfirmClient(
            new TossPaymentsProperties(
                "https://toss.test", "test-secret", Duration.ofSeconds(1), Duration.ofSeconds(1)),
            builder.build());
  }

  @Test
  void sendsDeterministicIdempotencyKeyForCanonicalRequests() {
    List<String> keys = new ArrayList<>();
    server
        .expect(times(5), requestTo(CONFIRM_URI))
        .andExpect(method(HttpMethod.POST))
        .andExpect(request -> keys.add(request.getHeaders().getFirst("Idempotency-Key")))
        .andRespond(withSuccess(completeResponse(), MediaType.APPLICATION_JSON));

    client.confirm(REQUEST);
    client.confirm(REQUEST);
    client.confirm(new PaymentConfirmRequest("another-payment-key", "order-id", 10_000L));
    client.confirm(new PaymentConfirmRequest("payment-key", "another-order-id", 10_000L));
    client.confirm(new PaymentConfirmRequest("payment-key", "order-id", 20_000L));

    assertThat(keys).hasSize(5).allSatisfy(key -> assertThat(key).isNotBlank());
    assertThat(keys.get(0)).isEqualTo(keys.get(1));
    assertThat(keys.subList(2, 5)).doesNotContain(keys.get(0)).doesNotHaveDuplicates();
    server.verify();
  }

  @Test
  void mapsExactProcessingCodeToPendingWithoutRetry() {
    server
        .expect(once(), requestTo(CONFIRM_URI))
        .andRespond(error(HttpStatus.CONFLICT, "{\"code\":\"IDEMPOTENT_REQUEST_PROCESSING\"}"));

    assertThat(errorFromConfirm()).isEqualTo(PointErrorCode.PAYMENT_CONFIRM_PENDING);
    server.verify();
  }

  @Test
  void mapsOtherNonBlankProviderCodeToTerminalFailure() {
    server
        .expect(once(), requestTo(CONFIRM_URI))
        .andRespond(error(HttpStatus.BAD_REQUEST, "{\"code\":\"INVALID_REQUEST\"}"));

    assertThat(errorFromConfirm()).isEqualTo(PointErrorCode.PAYMENT_CONFIRM_FAILED);
    server.verify();
  }

  @ParameterizedTest
  @MethodSource("ambiguousClientErrorBodies")
  void mapsAmbiguousClientErrorBodiesToPending(String body) {
    server.expect(once(), requestTo(CONFIRM_URI)).andRespond(error(HttpStatus.BAD_REQUEST, body));

    assertThat(errorFromConfirm()).isEqualTo(PointErrorCode.PAYMENT_CONFIRM_PENDING);
    server.verify();
  }

  @Test
  void mapsProviderServerErrorToPending() {
    server.expect(once(), requestTo(CONFIRM_URI)).andRespond(withServerError());

    assertThat(errorFromConfirm()).isEqualTo(PointErrorCode.PAYMENT_CONFIRM_PENDING);
    server.verify();
  }

  @Test
  void mapsTransportFailureToPending() {
    server
        .expect(once(), requestTo(CONFIRM_URI))
        .andRespond(
            request -> {
              throw new SocketTimeoutException("connection closed");
            });

    assertThat(errorFromConfirm()).isEqualTo(PointErrorCode.PAYMENT_CONFIRM_PENDING);
    server.verify();
  }

  @ParameterizedTest
  @MethodSource("incompleteSuccessBodies")
  void mapsIncompleteSuccessResponseToPending(String body) {
    server
        .expect(once(), requestTo(CONFIRM_URI))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThat(errorFromConfirm()).isEqualTo(PointErrorCode.PAYMENT_CONFIRM_PENDING);
    server.verify();
  }

  @Test
  void returnsCompleteResponseForServiceLevelCanonicalValidation() {
    server
        .expect(once(), requestTo(CONFIRM_URI))
        .andRespond(
            withSuccess(
                "{\"paymentKey\":\"different-payment-key\",\"orderId\":\"different-order-id\","
                    + "\"totalAmount\":1,\"currency\":\"KRW\",\"status\":\"DONE\"}",
                MediaType.APPLICATION_JSON));

    PaymentConfirmResult result = client.confirm(REQUEST);

    assertThat(result.paymentId()).isEqualTo("different-payment-key");
    assertThat(result.orderId()).isEqualTo("different-order-id");
    server.verify();
  }

  private PointErrorCode errorFromConfirm() {
    CustomException exception =
        catchThrowableOfType(() -> client.confirm(REQUEST), CustomException.class);
    assertThat(exception).isNotNull();
    return (PointErrorCode) exception.getErrorCode();
  }

  private static Stream<String> ambiguousClientErrorBodies() {
    return Stream.of(
        "{}",
        "{\"code\":null}",
        "{\"code\":\"\"}",
        "{\"code\":\"   \"}",
        "{\"code\":1}",
        "[]",
        "",
        "{");
  }

  private static Stream<String> incompleteSuccessBodies() {
    return Stream.of(
        "",
        "{",
        "[]",
        "{\"orderId\":\"order-id\",\"totalAmount\":10000,\"currency\":\"KRW\",\"status\":\"DONE\"}",
        "{\"paymentKey\":\"payment-key\",\"totalAmount\":10000,\"currency\":\"KRW\",\"status\":\"DONE\"}",
        "{\"paymentKey\":\"payment-key\",\"orderId\":\"order-id\",\"currency\":\"KRW\",\"status\":\"DONE\"}",
        "{\"paymentKey\":\"payment-key\",\"orderId\":\"order-id\",\"totalAmount\":10000,\"status\":\"DONE\"}",
        "{\"paymentKey\":\"payment-key\",\"orderId\":\"order-id\",\"totalAmount\":10000,\"currency\":\"KRW\"}",
        "{\"paymentKey\":null,\"orderId\":\"order-id\",\"totalAmount\":10000,\"currency\":\"KRW\",\"status\":\"DONE\"}",
        "{\"paymentKey\":\"payment-key\",\"orderId\":null,\"totalAmount\":10000,\"currency\":\"KRW\",\"status\":\"DONE\"}",
        "{\"paymentKey\":\"payment-key\",\"orderId\":\"order-id\",\"totalAmount\":null,\"currency\":\"KRW\",\"status\":\"DONE\"}",
        "{\"paymentKey\":\"payment-key\",\"orderId\":\"order-id\",\"totalAmount\":10000,\"currency\":null,\"status\":\"DONE\"}",
        "{\"paymentKey\":\"payment-key\",\"orderId\":\"order-id\",\"totalAmount\":10000,\"currency\":\"KRW\",\"status\":null}");
  }

  private static DefaultResponseCreator error(HttpStatus status, String body) {
    return withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body);
  }

  private static String completeResponse() {
    return "{\"paymentKey\":\"payment-key\",\"orderId\":\"order-id\",\"totalAmount\":10000,"
        + "\"currency\":\"KRW\",\"status\":\"DONE\"}";
  }
}
