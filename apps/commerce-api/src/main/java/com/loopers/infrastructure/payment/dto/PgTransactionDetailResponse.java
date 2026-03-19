package com.loopers.infrastructure.payment.dto;

public record PgTransactionDetailResponse(
    String transactionKey,
    String orderId,
    String cardType,
    String cardNo,
    Long amount,
    String status,
    String reason
) {}
