package com.oit.dondok.domain.point.port;

public record PaymentLookupResult(
    String paymentId, String orderId, Long totalAmount, String currency, String status) {}
