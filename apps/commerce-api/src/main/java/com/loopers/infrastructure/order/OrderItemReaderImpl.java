package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItemReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderItemReaderImpl implements OrderItemReader {

    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public List<OrderItem> findAllByOrderId(Long orderId) {
        return orderItemJpaRepository.findAllByOrderIdAndDeletedAtIsNull(orderId);
    }

    @Override
    public List<OrderItem> findAllByOrderIds(List<Long> orderIds) {
        return orderItemJpaRepository.findAllByOrderIdInAndDeletedAtIsNull(orderIds);
    }
}
