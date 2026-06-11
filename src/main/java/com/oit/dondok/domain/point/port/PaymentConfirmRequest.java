package com.oit.dondok.domain.point.port;

public record PaymentConfirmRequest(String paymentId, String orderId, Long amount) {}
