package com.loopers.application.order;

import com.loopers.domain.order.OrderItem;

public record OrderItemInfo(
    Long id,
    Long productId,
    String productName,
    Long productPrice,
    int quantity,
    Long subtotal
) {
    public static OrderItemInfo from(OrderItem item) {
        return new OrderItemInfo(
            item.getId(),
            item.getProductId(),
            item.getProductName(),
            item.getProductPrice(),
            item.getQuantity(),
            item.getSubtotal()
        );
    }
}
