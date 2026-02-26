package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
    Long id,
    Long memberId,
    String recipientName,
    String recipientPhone,
    String zipCode,
    String address1,
    String address2,
    Long totalAmount,
    String status,
    List<OrderItemInfo> items,
    ZonedDateTime createdAt
) {
    public static OrderInfo of(Order order, List<OrderItem> items) {
        return new OrderInfo(
            order.getId(),
            order.getMemberId(),
            order.getRecipientName(),
            order.getRecipientPhone(),
            order.getZipCode(),
            order.getAddress1(),
            order.getAddress2(),
            order.getTotalAmount(),
            order.getStatus().name(),
            items.stream().map(OrderItemInfo::from).toList(),
            order.getCreatedAt()
        );
    }
}
