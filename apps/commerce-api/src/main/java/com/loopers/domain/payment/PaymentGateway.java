package com.loopers.domain.payment;

import java.util.List;

public interface PaymentGateway {
    PaymentGatewayResponse requestPayment(Long memberId, String orderId,
                                           String cardType, String cardNo,
                                           Long amount);

    PaymentGatewayResponse getPaymentStatus(Long memberId, String transactionKey);

    List<PaymentGatewayResponse> getPaymentsByOrderId(Long memberId, String orderId);
}
