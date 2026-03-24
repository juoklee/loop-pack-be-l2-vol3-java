package com.loopers.domain.event;

public record OrderCancelledEvent(
    Long orderId,
    Long memberId
) {}
