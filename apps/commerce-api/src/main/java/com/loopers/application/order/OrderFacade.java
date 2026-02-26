package com.loopers.application.order;

import com.loopers.application.PagedInfo;
import com.loopers.domain.PageResult;
import com.loopers.domain.address.Address;
import com.loopers.domain.address.AddressReader;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final MemberService memberService;
    private final ProductService productService;
    private final OrderService orderService;
    private final AddressReader addressReader;

    @Transactional
    public OrderInfo createOrder(String loginId, Long addressId, List<OrderItemRequest> itemRequests) {
        Long memberId = getMemberId(loginId);

        // 1. 배송지 검증 + 스냅샷
        Address address = addressReader.findByIdAndMemberId(addressId, memberId)
            .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "존재하지 않는 배송지입니다."));

        // 2. 요청 검증
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        // 3. 중복 상품 합산
        Map<Long, Integer> mergedItems = mergeItems(itemRequests);

        // 4. 상품 검증 + 재고 차감 + totalAmount 계산
        long totalAmount = 0L;
        List<OrderService.OrderItemCommand> commands = new java.util.ArrayList<>();

        for (Map.Entry<Long, Integer> entry : mergedItems.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();

            Product product = productService.getProduct(productId);

            if (quantity > product.getMaxOrderQuantity()) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "상품 '" + product.getName() + "'의 최대 주문 수량(" + product.getMaxOrderQuantity() + ")을 초과했습니다.");
            }

            product.decreaseStock(quantity);

            totalAmount += product.getPrice() * quantity;
            commands.add(new OrderService.OrderItemCommand(
                productId, product.getName(), product.getPrice(), quantity
            ));
        }

        // 5. 주문 생성
        Order order = orderService.createOrder(
            memberId, address.getRecipientName(), address.getRecipientPhone(),
            address.getZipCode(), address.getAddress1(), address.getAddress2(), totalAmount
        );

        // 6. 주문 항목 생성
        List<OrderItem> items = orderService.createOrderItems(order.getId(), commands);

        return OrderInfo.of(order, items);
    }

    @Transactional
    public void cancelOrder(String loginId, Long orderId) {
        Long memberId = getMemberId(loginId);
        Order order = orderService.getOrderForMember(orderId, memberId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }

        order.cancel();

        List<OrderItem> items = orderService.getOrderItems(orderId);
        for (OrderItem item : items) {
            Product product = productService.getProduct(item.getProductId());
            product.increaseStock(item.getQuantity());
        }
    }

    @Transactional
    public OrderInfo updateShippingAddress(String loginId, Long orderId,
                                           String recipientName, String recipientPhone,
                                           String zipCode, String address1, String address2) {
        Long memberId = getMemberId(loginId);
        Order order = orderService.getOrderForMember(orderId, memberId);

        order.updateShippingAddress(recipientName, recipientPhone, zipCode, address1, address2);

        List<OrderItem> items = orderService.getOrderItems(orderId);
        return OrderInfo.of(order, items);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(String loginId, Long orderId) {
        Long memberId = getMemberId(loginId);
        Order order = orderService.getOrderForMember(orderId, memberId);
        List<OrderItem> items = orderService.getOrderItems(orderId);
        return OrderInfo.of(order, items);
    }

    @Transactional(readOnly = true)
    public PagedInfo<OrderSummaryInfo> getMyOrders(String loginId, LocalDate startAt, LocalDate endAt,
                                                    int page, int size) {
        Long memberId = getMemberId(loginId);
        PageResult<Order> result = orderService.getMyOrders(memberId, startAt, endAt, page, size);
        return toPagedSummary(result);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrderForAdmin(Long orderId) {
        Order order = orderService.getOrder(orderId);
        List<OrderItem> items = orderService.getOrderItems(orderId);
        return OrderInfo.of(order, items);
    }

    @Transactional(readOnly = true)
    public PagedInfo<OrderSummaryInfo> getOrdersForAdmin(Long memberId, int page, int size) {
        PageResult<Order> result = orderService.getOrders(memberId, page, size);
        return toPagedSummary(result);
    }

    private PagedInfo<OrderSummaryInfo> toPagedSummary(PageResult<Order> result) {
        List<Long> orderIds = result.content().stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemMap = orderService.getOrderItemsByOrderIds(orderIds).stream()
            .collect(Collectors.groupingBy(OrderItem::getOrderId));

        List<OrderSummaryInfo> summaries = result.content().stream()
            .map(order -> OrderSummaryInfo.of(order, itemMap.getOrDefault(order.getId(), List.of())))
            .toList();

        return new PagedInfo<>(summaries, result.totalElements(), result.totalPages(), result.page(), result.size());
    }

    private Map<Long, Integer> mergeItems(List<OrderItemRequest> requests) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemRequest request : requests) {
            if (request.quantity() <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 0보다 커야 합니다.");
            }
            merged.merge(request.productId(), request.quantity(), Integer::sum);
        }
        return merged;
    }

    private Long getMemberId(String loginId) {
        Member member = memberService.getMemberByLoginId(loginId);
        return member.getId();
    }

    public record OrderItemRequest(Long productId, int quantity) {}
}
