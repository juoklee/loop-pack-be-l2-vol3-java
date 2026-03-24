package com.loopers.domain.event;

import java.util.List;

public record OrderCreatedEvent(
    Long orderId,
    Long memberId,
    long totalAmount,
    List<OrderItemSnapshot> items
) {

    public record OrderItemSnapshot(Long productId, String productName, long price, int quantity) {}
}
