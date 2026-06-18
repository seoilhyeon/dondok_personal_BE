package com.oit.dondok.domain.point.port;

public interface PaymentLookupClient {

  PaymentLookupResult lookup(String paymentId);
}
