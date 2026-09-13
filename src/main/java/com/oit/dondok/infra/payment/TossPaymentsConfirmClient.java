package com.oit.dondok.infra.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Profile("!test & !load-test")
@Component
public class TossPaymentsConfirmClient implements PaymentConfirmClient, PaymentLookupClient {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final TossPaymentsProperties properties;
  private final RestClient restClient;

  @Autowired
  public TossPaymentsConfirmClient(TossPaymentsProperties properties) {
    this(
        properties,
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
            .build());
  }

  TossPaymentsConfirmClient(TossPaymentsProperties properties, RestClient restClient) {
    this.properties = properties;
    this.restClient = restClient;
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

      if (!isComplete(response)) {
        throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_PENDING);
      }
      return new PaymentConfirmResult(
          response.paymentKey(),
          response.orderId(),
          response.totalAmount(),
          response.currency(),
          response.status());
    } catch (RestClientResponseException e) {
      throw classifyResponseException(e);
    } catch (RestClientException e) {
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_PENDING, e);
    }
  }

  private static boolean isComplete(TossConfirmResponse response) {
    return response != null
        && isNotBlank(response.paymentKey())
        && isNotBlank(response.orderId())
        && response.totalAmount() != null
        && isNotBlank(response.currency())
        && isNotBlank(response.status());
  }

  private static boolean isNotBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static CustomException classifyResponseException(RestClientResponseException exception) {
    if (!exception.getStatusCode().is4xxClientError()) {
      return new CustomException(PointErrorCode.PAYMENT_CONFIRM_PENDING, exception);
    }

    String code = readErrorCode(exception.getResponseBodyAsString());
    if (!TossConfirmTerminalErrorCodes.contains(code)) {
      return new CustomException(PointErrorCode.PAYMENT_CONFIRM_PENDING, exception);
    }
    return new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED, exception);
  }

  private static String readErrorCode(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return null;
    }

    try {
      JsonNode root = OBJECT_MAPPER.readTree(responseBody);
      if (root == null || !root.isObject()) {
        return null;
      }
      JsonNode code = root.get("code");
      return code != null && code.isTextual() && !code.asText().isBlank() ? code.asText() : null;
    } catch (JsonProcessingException e) {
      return null;
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
