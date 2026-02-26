package com.loopers.interfaces.api.order;

import com.loopers.application.PagedInfo;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderSummaryInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping("/api/v1/orders")
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        HttpServletRequest request,
        @RequestBody OrderV1Dto.CreateOrderRequest body
    ) {
        String loginId = getAuthenticatedLoginId(request);
        List<OrderFacade.OrderItemRequest> itemRequests = body.items().stream()
            .map(item -> new OrderFacade.OrderItemRequest(item.productId(), item.quantity()))
            .toList();
        OrderInfo info = orderFacade.createOrder(loginId, body.addressId(), itemRequests);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping("/api/v1/orders")
    public ApiResponse<OrderV1Dto.OrderListResponse> getMyOrders(
        HttpServletRequest request,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startAt,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endAt,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        String loginId = getAuthenticatedLoginId(request);
        PagedInfo<OrderSummaryInfo> result = orderFacade.getMyOrders(loginId, startAt, endAt, page, size);
        return ApiResponse.success(OrderV1Dto.OrderListResponse.from(result));
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        HttpServletRequest request,
        @PathVariable Long orderId
    ) {
        String loginId = getAuthenticatedLoginId(request);
        OrderInfo info = orderFacade.getOrder(loginId, orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @PostMapping("/api/v1/orders/{orderId}/cancel")
    public ApiResponse<Object> cancelOrder(
        HttpServletRequest request,
        @PathVariable Long orderId
    ) {
        String loginId = getAuthenticatedLoginId(request);
        orderFacade.cancelOrder(loginId, orderId);
        return ApiResponse.success();
    }

    @PutMapping("/api/v1/orders/{orderId}/shipping-address")
    public ApiResponse<OrderV1Dto.OrderResponse> updateShippingAddress(
        HttpServletRequest request,
        @PathVariable Long orderId,
        @RequestBody OrderV1Dto.UpdateShippingAddressRequest body
    ) {
        String loginId = getAuthenticatedLoginId(request);
        OrderInfo info = orderFacade.updateShippingAddress(
            loginId, orderId,
            body.recipientName(), body.recipientPhone(),
            body.zipCode(), body.address1(), body.address2()
        );
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    private String getAuthenticatedLoginId(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedLoginId");
        if (!(attribute instanceof String loginId)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증된 회원 정보가 없습니다.");
        }
        return loginId;
    }
}
