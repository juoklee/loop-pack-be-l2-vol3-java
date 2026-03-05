package com.loopers.domain.order;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderReader orderReader;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemReader orderItemReader;

    @Transactional
    public Order createOrder(Long memberId, String recipientName, String recipientPhone,
                             String zipCode, String address1, String address2, Long totalAmount) {
        Order order = Order.create(memberId, recipientName, recipientPhone, zipCode, address1, address2, totalAmount);
        return orderRepository.save(order);
    }

    @Transactional
    public Order createOrder(Long memberId, String recipientName, String recipientPhone,
                             String zipCode, String address1, String address2, Long totalAmount,
                             Long memberCouponId, Long originalAmount, Long discountAmount) {
        Order order = Order.create(memberId, recipientName, recipientPhone, zipCode, address1, address2,
                                   totalAmount, memberCouponId, originalAmount, discountAmount);
        return orderRepository.save(order);
    }

    @Transactional
    public List<OrderItem> createOrderItems(Long orderId, List<OrderItemCommand> commands) {
        List<OrderItem> items = commands.stream()
            .map(cmd -> OrderItem.create(orderId, cmd.productId(), cmd.productName(), cmd.productPrice(), cmd.quantity()))
            .toList();
        return orderItemRepository.saveAll(items);
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderReader.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Order getOrderForMember(Long orderId, Long memberId) {
        return orderReader.findByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItemReader.findAllByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderItem> getOrderItemsByOrderIds(List<Long> orderIds) {
        return orderItemReader.findAllByOrderIds(orderIds);
    }

    @Transactional(readOnly = true)
    public PageResult<Order> getMyOrders(Long memberId, LocalDate startAt, LocalDate endAt, int page, int size) {
        return orderReader.findAllByMemberId(memberId, startAt, endAt, page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<Order> getOrders(Long memberId, int page, int size) {
        return orderReader.findAll(memberId, page, size);
    }

    public Map<Long, Integer> mergeOrderItems(List<OrderItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemRequest request : requests) {
            if (request.quantity() <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 0보다 커야 합니다.");
            }
            merged.merge(request.productId(), request.quantity(), Integer::sum);
        }
        return merged;
    }

    @Transactional
    public Order updateShippingAddress(Long orderId, Long memberId,
                                        String recipientName, String recipientPhone,
                                        String zipCode, String address1, String address2) {
        Order order = getOrderForMember(orderId, memberId);
        order.updateShippingAddress(recipientName, recipientPhone, zipCode, address1, address2);
        return order;
    }

    @Transactional
    public List<OrderItem> cancelOrder(Long orderId, Long memberId) {
        Order order = orderReader.findByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }

        order.cancel();

        return orderItemReader.findAllByOrderId(orderId);
    }

    public record OrderItemRequest(Long productId, int quantity) {}

    public record OrderItemCommand(Long productId, String productName, Long productPrice, int quantity) {}
}
