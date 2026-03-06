package com.loopers.application.order;

import com.loopers.application.PagedInfo;
import com.loopers.domain.PageResult;
import com.loopers.domain.address.Address;
import com.loopers.domain.address.AddressService;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final MemberService memberService;
    private final ProductService productService;
    private final OrderService orderService;
    private final AddressService addressService;
    private final CouponService couponService;

    @Transactional
    public OrderInfo createOrder(String loginId, Long addressId, Long memberCouponId,
                                 List<OrderItemRequest> itemRequests) {
        Long memberId = getMemberId(loginId);

        // 1. 배송지 검증 + 스냅샷
        Address address = addressService.getAddress(addressId, memberId);

        // 2. 주문 항목 검증 + 중복 상품 합산 (도메인 규칙)
        List<OrderService.OrderItemRequest> serviceRequests = itemRequests.stream()
            .map(r -> new OrderService.OrderItemRequest(r.productId(), r.quantity()))
            .toList();
        Map<Long, Integer> mergedItems = orderService.mergeOrderItems(serviceRequests);

        // 3. 상품 검증 + 재고 차감 + originalAmount 계산
        long originalAmount = 0L;
        List<OrderService.OrderItemCommand> commands = new java.util.ArrayList<>();

        List<Map.Entry<Long, Integer>> sortedEntries = mergedItems.entrySet().stream()
            .sorted(Comparator.comparingLong(Map.Entry::getKey))
            .toList();

        for (Map.Entry<Long, Integer> entry : sortedEntries) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();

            Product product = productService.getProductForUpdate(productId);
            product.validateOrderQuantity(quantity);
            product.decreaseStock(quantity);

            originalAmount += product.getPrice() * quantity;
            commands.add(new OrderService.OrderItemCommand(
                productId, product.getName(), product.getPrice(), quantity
            ));
        }

        // 4. 쿠폰 적용
        Order order;
        if (memberCouponId != null) {
            CouponService.CouponApplyResult couponResult = couponService.useCoupon(memberCouponId, memberId, originalAmount);
            long totalAmount = originalAmount - couponResult.discountAmount();

            order = orderService.createOrder(
                memberId, address.getRecipientName(), address.getRecipientPhone(),
                address.getZipCode(), address.getAddress1(), address.getAddress2(),
                totalAmount, couponResult.memberCouponId(), originalAmount, couponResult.discountAmount()
            );
        } else {
            order = orderService.createOrder(
                memberId, address.getRecipientName(), address.getRecipientPhone(),
                address.getZipCode(), address.getAddress1(), address.getAddress2(), originalAmount
            );
        }

        // 5. 주문 항목 생성
        List<OrderItem> items = orderService.createOrderItems(order.getId(), commands);

        return OrderInfo.of(order, items);
    }

    @Transactional
    public void cancelOrder(String loginId, Long orderId) {
        Long memberId = getMemberId(loginId);

        // 소유권 검증 + 취소 상태 체크 + 상태 전이 (도메인 규칙)
        OrderService.CancelOrderResult result = orderService.cancelOrder(orderId, memberId);

        // 재고 복원 (cross-domain orchestration) — productId 순 정렬로 데드락 방지
        List<OrderItem> sortedItems = result.items().stream()
            .sorted(Comparator.comparingLong(OrderItem::getProductId))
            .toList();

        for (OrderItem item : sortedItems) {
            Product product = productService.getProductForUpdate(item.getProductId());
            product.increaseStock(item.getQuantity());
        }

        // 쿠폰 복원
        if (result.order().getMemberCouponId() != null) {
            couponService.restoreCoupon(result.order().getMemberCouponId());
        }
    }

    public OrderInfo updateShippingAddress(String loginId, Long orderId,
                                           String recipientName, String recipientPhone,
                                           String zipCode, String address1, String address2) {
        Long memberId = getMemberId(loginId);
        Order order = orderService.updateShippingAddress(orderId, memberId,
            recipientName, recipientPhone, zipCode, address1, address2);

        List<OrderItem> items = orderService.getOrderItems(orderId);
        return OrderInfo.of(order, items);
    }

    public OrderInfo getOrder(String loginId, Long orderId) {
        Long memberId = getMemberId(loginId);
        Order order = orderService.getOrderForMember(orderId, memberId);
        List<OrderItem> items = orderService.getOrderItems(orderId);
        return OrderInfo.of(order, items);
    }

    public PagedInfo<OrderSummaryInfo> getMyOrders(String loginId, LocalDate startAt, LocalDate endAt,
                                                    int page, int size) {
        Long memberId = getMemberId(loginId);
        PageResult<Order> result = orderService.getMyOrders(memberId, startAt, endAt, page, size);
        return toPagedSummary(result);
    }

    public OrderInfo getOrderForAdmin(Long orderId) {
        Order order = orderService.getOrder(orderId);
        List<OrderItem> items = orderService.getOrderItems(orderId);
        return OrderInfo.of(order, items);
    }

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

    private Long getMemberId(String loginId) {
        Member member = memberService.getMemberByLoginId(loginId);
        return member.getId();
    }

    public record OrderItemRequest(Long productId, int quantity) {}
}
