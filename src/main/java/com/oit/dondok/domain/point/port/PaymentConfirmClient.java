package com.oit.dondok.domain.point.port;

public interface PaymentConfirmClient {

  PaymentConfirmResult confirm(PaymentConfirmRequest request);

  void cancel(String paymentId, String reason);
}
