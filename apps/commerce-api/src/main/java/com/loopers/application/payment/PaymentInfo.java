package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;

import java.time.ZonedDateTime;

public record PaymentInfo(
    Long id,
    Long orderId,
    Long memberId,
    String cardType,
    String cardNo,
    Long amount,
    String status,
    String transactionKey,
    String failReason,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
            payment.getId(),
            payment.getOrderId(),
            payment.getMemberId(),
            payment.getCardType(),
            payment.getCardNo(),
            payment.getAmount(),
            payment.getStatus().name(),
            payment.getTransactionKey(),
            payment.getFailReason(),
            payment.getCreatedAt(),
            payment.getUpdatedAt()
        );
    }
}
