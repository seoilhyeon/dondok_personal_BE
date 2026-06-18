package com.oit.dondok.infra.payment;

import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.port.PaymentConfirmClient;
import com.oit.dondok.domain.point.port.PaymentConfirmRequest;
import com.oit.dondok.domain.point.port.PaymentConfirmResult;
import com.oit.dondok.domain.point.port.PaymentLookupClient;
import com.oit.dondok.domain.point.port.PaymentLookupResult;
import com.oit.dondok.global.exception.CustomException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("test")
@Component
public class StubPaymentClient implements PaymentConfirmClient, PaymentLookupClient {

  @Override
  public PaymentConfirmResult confirm(PaymentConfirmRequest request) {
    throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED);
  }

  @Override
  public void cancel(String paymentId, String reason) {
    throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED);
  }

  @Override
  public PaymentLookupResult lookup(String paymentId) {
    throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED);
  }
}
