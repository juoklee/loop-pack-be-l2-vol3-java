package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping("/api/v1/payments")
    public ApiResponse<PaymentV1Dto.PaymentResponse> createPayment(
        HttpServletRequest request,
        @RequestBody PaymentV1Dto.CreatePaymentRequest body
    ) {
        String loginId = AuthUtils.getAuthenticatedLoginId(request);
        PaymentInfo info = paymentFacade.createPayment(loginId, body.orderId(), body.cardType(), body.cardNo());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @PostMapping("/api/v1/payments/{paymentId}/pay")
    public ApiResponse<PaymentV1Dto.PaymentResponse> executePayment(
        HttpServletRequest request,
        @PathVariable Long paymentId
    ) {
        String loginId = AuthUtils.getAuthenticatedLoginId(request);
        PaymentInfo info = paymentFacade.executePayment(loginId, paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @GetMapping("/api/v1/payments/{paymentId}")
    public ApiResponse<PaymentV1Dto.PaymentResponse> getPayment(
        HttpServletRequest request,
        @PathVariable Long paymentId
    ) {
        String loginId = AuthUtils.getAuthenticatedLoginId(request);
        PaymentInfo info = paymentFacade.getPayment(loginId, paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @GetMapping("/api/v1/payments/orders/{orderId}")
    public ApiResponse<PaymentV1Dto.PaymentListResponse> getPaymentsByOrderId(
        HttpServletRequest request,
        @PathVariable Long orderId
    ) {
        String loginId = AuthUtils.getAuthenticatedLoginId(request);
        List<PaymentInfo> infos = paymentFacade.getPaymentsByOrderId(loginId, orderId);
        return ApiResponse.success(PaymentV1Dto.PaymentListResponse.from(infos));
    }
}
