package com.loopers.interfaces.api.order;

import com.loopers.application.PagedInfo;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderSummaryInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller {

    private final OrderFacade orderFacade;

    @GetMapping
    public ApiResponse<OrderV1Dto.OrderListResponse> getOrders(
        @RequestParam(required = false) Long memberId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PagedInfo<OrderSummaryInfo> result = orderFacade.getOrdersForAdmin(memberId, page, size);
        return ApiResponse.success(OrderV1Dto.OrderListResponse.from(result));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(@PathVariable Long orderId) {
        OrderInfo info = orderFacade.getOrderForAdmin(orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
