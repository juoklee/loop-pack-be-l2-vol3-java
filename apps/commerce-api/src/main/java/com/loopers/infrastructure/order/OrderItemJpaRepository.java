package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemJpaRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findAllByOrderIdAndDeletedAtIsNull(Long orderId);
    List<OrderItem> findAllByOrderIdInAndDeletedAtIsNull(List<Long> orderIds);
}
