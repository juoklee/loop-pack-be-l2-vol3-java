package com.loopers.infrastructure.payment.dto;

public record PgPaymentResponse(
    String transactionKey,
    String status,
    String reason
) {}
