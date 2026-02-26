package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderSummaryInfo(
    Long id,
    Long totalAmount,
    String status,
    int itemCount,
    String representativeProductName,
    ZonedDateTime createdAt
) {
    public static OrderSummaryInfo of(Order order, List<OrderItem> items) {
        String representativeName = items.isEmpty() ? "" : items.get(0).getProductName();
        return new OrderSummaryInfo(
            order.getId(),
            order.getTotalAmount(),
            order.getStatus().name(),
            items.size(),
            representativeName,
            order.getCreatedAt()
        );
    }
}
