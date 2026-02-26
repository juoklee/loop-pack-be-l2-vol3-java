package com.loopers.interfaces.api.order;

import com.loopers.application.PagedInfo;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.application.order.OrderSummaryInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(
        Long addressId,
        List<OrderItemRequest> items
    ) {
        public record OrderItemRequest(Long productId, int quantity) {}
    }

    public record UpdateShippingAddressRequest(
        String recipientName,
        String recipientPhone,
        String zipCode,
        String address1,
        String address2
    ) {}

    public record OrderResponse(OrderDto order) {
        public static OrderResponse from(OrderInfo info) {
            List<OrderItemDto> items = info.items().stream()
                .map(OrderItemDto::from)
                .toList();
            return new OrderResponse(new OrderDto(
                info.id(), info.memberId(),
                info.recipientName(), info.recipientPhone(),
                info.zipCode(), info.address1(), info.address2(),
                info.totalAmount(), info.status(), items, info.createdAt()
            ));
        }
    }

    public record OrderDto(
        Long id,
        Long memberId,
        String recipientName,
        String recipientPhone,
        String zipCode,
        String address1,
        String address2,
        Long totalAmount,
        String status,
        List<OrderItemDto> items,
        ZonedDateTime createdAt
    ) {}

    public record OrderItemDto(
        Long id,
        Long productId,
        String productName,
        Long productPrice,
        int quantity,
        Long subtotal
    ) {
        public static OrderItemDto from(OrderItemInfo info) {
            return new OrderItemDto(
                info.id(), info.productId(), info.productName(),
                info.productPrice(), info.quantity(), info.subtotal()
            );
        }
    }

    public record OrderListResponse(List<OrderSummaryDto> orders, PageInfo page) {
        public static OrderListResponse from(PagedInfo<OrderSummaryInfo> result) {
            var dtos = result.content().stream()
                .map(OrderSummaryDto::from)
                .toList();
            return new OrderListResponse(
                dtos,
                new PageInfo(result.page(), result.size(), result.totalElements(), result.totalPages())
            );
        }
    }

    public record OrderSummaryDto(
        Long id,
        Long totalAmount,
        String status,
        int itemCount,
        String representativeProductName,
        ZonedDateTime createdAt
    ) {
        public static OrderSummaryDto from(OrderSummaryInfo info) {
            return new OrderSummaryDto(
                info.id(), info.totalAmount(), info.status(),
                info.itemCount(), info.representativeProductName(), info.createdAt()
            );
        }
    }

    public record PageInfo(int number, int size, long totalElements, int totalPages) {}
}
