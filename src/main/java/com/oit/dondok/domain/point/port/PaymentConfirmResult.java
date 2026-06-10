package com.oit.dondok.domain.point.port;

public record PaymentConfirmResult(
    String paymentId, String orderId, Long totalAmount, String currency, String status) {}
