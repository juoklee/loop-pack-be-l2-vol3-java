package com.loopers.infrastructure.payment.dto;

public record PgPaymentRequest(
    String orderId,
    String cardType,
    String cardNo,
    Long amount,
    String callbackUrl
) {}
