package com.oit.dondok.infra.loadtest;

import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.port.PaymentConfirmClient;
import com.oit.dondok.domain.point.port.PaymentConfirmRequest;
import com.oit.dondok.domain.point.port.PaymentConfirmResult;
import com.oit.dondok.domain.point.port.PaymentLookupClient;
import com.oit.dondok.domain.point.port.PaymentLookupResult;
import com.oit.dondok.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Deterministic local double; no payment-network calls are possible in this profile. */
@Component
@Profile("load-test & !prod")
@RequiredArgsConstructor
public class LoadTestPaymentConfirmClient implements PaymentConfirmClient, PaymentLookupClient {
  private static final String FAILURE_PAYMENT_ID = "load-test-payment-fail";
  private static final String COMPLETION_MISMATCH_PAYMENT_ID = "completion-mismatch";

  private final LoadTestRecoveryCompletionMismatchHook recoveryCompletionMismatchHook;

  @Override
  public PaymentConfirmResult confirm(PaymentConfirmRequest request) {
    if (FAILURE_PAYMENT_ID.equals(request.paymentId())) {
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED);
    }
    return new PaymentConfirmResult(
        request.paymentId(), request.orderId(), request.amount(), "KRW", "DONE");
  }

  @Override
  public void cancel(String paymentId, String reason) {
    if (paymentId.contains("cancel-error")) {
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED);
    }
  }

  @Override
  public PaymentLookupResult lookup(String paymentId) {
    if (paymentId.contains("lookup-exception")) {
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED);
    }
    if (paymentId.contains("not-done")) {
      return new PaymentLookupResult(paymentId, orderId(paymentId), 10_000L, "KRW", "CANCELED");
    }
    if (paymentId.contains(COMPLETION_MISMATCH_PAYMENT_ID)) {
      recoveryCompletionMismatchHook.changeOrderAfterLookup(paymentId);
    } else if (paymentId.contains("mismatch")) {
      return new PaymentLookupResult(paymentId, orderId(paymentId), 9_000L, "KRW", "DONE");
    }
    return new PaymentLookupResult(paymentId, orderId(paymentId), 10_000L, "KRW", "DONE");
  }

  private String orderId(String paymentId) {
    return "load-test-order-" + paymentId.substring("load-test-".length());
  }
}
