package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class PaymentCallbackV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping("/api/internal/v1/payments/callback")
    public ApiResponse<PaymentV1Dto.PaymentResponse> handleCallback(
        @RequestBody PaymentV1Dto.CallbackRequest body
    ) {
        PaymentInfo info = paymentFacade.processCallback(
            body.transactionKey(), body.status(), body.reason()
        );
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }
}
