package com.loopers.domain.payment;

public record PaymentGatewayResponse(
    String transactionKey,
    String orderId,
    String status,
    String reason
) {
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }
}
