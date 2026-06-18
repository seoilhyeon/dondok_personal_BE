package com.oit.dondok.infra.payment;

import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.port.PaymentConfirmClient;
import com.oit.dondok.domain.point.port.PaymentConfirmRequest;
import com.oit.dondok.domain.point.port.PaymentConfirmResult;
import com.oit.dondok.domain.point.port.PaymentLookupClient;
import com.oit.dondok.domain.point.port.PaymentLookupResult;
import com.oit.dondok.global.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TossPaymentsConfirmClient implements PaymentConfirmClient, PaymentLookupClient {

  private final TossPaymentsProperties properties;
  private final RestClient restClient;

  public TossPaymentsConfirmClient(TossPaymentsProperties properties) {
    this.properties = properties;
    this.restClient =
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
            .build();
  }

  @Override
  public PaymentConfirmResult confirm(PaymentConfirmRequest request) {
    if (properties.secretKey() == null || properties.secretKey().isBlank()) {
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED);
    }

    try {
      TossConfirmResponse response =
          restClient
              .post()
              .uri("/v1/payments/confirm")
              .header(HttpHeaders.AUTHORIZATION, basicAuthorization(properties.secretKey()))
              .header("Idempotency-Key", idempotencyKey(request))
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  new TossConfirmRequest(request.paymentId(), request.orderId(), request.amount()))
              .retrieve()
              .body(TossConfirmResponse.class);

      if (response == null) {
        throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED);
      }
      return new PaymentConfirmResult(
          response.paymentKey(),
          response.orderId(),
          response.totalAmount(),
          response.currency(),
          response.status());
    } catch (CustomException e) {
      throw e;
    } catch (RestClientException e) {
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED, e);
    }
  }

  @Override
  public void cancel(String paymentId, String reason) {
    if (properties.secretKey() == null || properties.secretKey().isBlank()) {
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED);
    }

    try {
      restClient
          .post()
          .uri("/v1/payments/{paymentKey}/cancel", paymentId)
          .header(HttpHeaders.AUTHORIZATION, basicAuthorization(properties.secretKey()))
          .header("Idempotency-Key", sha256("cancel:%s:%s".formatted(paymentId, reason)))
          .contentType(MediaType.APPLICATION_JSON)
          .body(new TossCancelRequest(reason))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED, e);
    }
  }

  @Override
  public PaymentLookupResult lookup(String paymentId) {
    if (properties.secretKey() == null || properties.secretKey().isBlank()) {
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED);
    }

    try {
      TossConfirmResponse response =
          restClient
              .get()
              .uri("/v1/payments/{paymentKey}", paymentId)
              .header(HttpHeaders.AUTHORIZATION, basicAuthorization(properties.secretKey()))
              .retrieve()
              .body(TossConfirmResponse.class);

      if (response == null) {
        throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED);
      }
      return new PaymentLookupResult(
          response.paymentKey(),
          response.orderId(),
          response.totalAmount(),
          response.currency(),
          response.status());
    } catch (CustomException e) {
      throw e;
    } catch (RestClientException e) {
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED, e);
    }
  }

  private static SimpleClientHttpRequestFactory requestFactory(
      Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  private static String basicAuthorization(String secretKey) {
    String token =
        Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    return "Basic " + token;
  }

  private static String idempotencyKey(PaymentConfirmRequest request) {
    return sha256("%s:%s:%d".formatted(request.paymentId(), request.orderId(), request.amount()));
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private record TossConfirmRequest(String paymentKey, String orderId, Long amount) {}

  private record TossCancelRequest(String cancelReason) {}

  private record TossConfirmResponse(
      String paymentKey, String orderId, Long totalAmount, String currency, String status) {}
}
