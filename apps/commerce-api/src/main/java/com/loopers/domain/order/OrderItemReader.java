package com.loopers.domain.order;

import java.util.List;

public interface OrderItemReader {
    List<OrderItem> findAllByOrderId(Long orderId);
    List<OrderItem> findAllByOrderIds(List<Long> orderIds);
}
