package com.loopers.domain.event;

public record PaymentCompletedEvent(
    Long paymentId,
    Long orderId,
    Long memberId,
    long amount
) {}
