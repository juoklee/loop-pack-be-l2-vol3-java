package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class PaymentV1Dto {

    public record CreatePaymentRequest(
        Long orderId,
        String cardType,
        String cardNo
    ) {}

    public record PaymentResponse(PaymentDto payment) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(PaymentDto.from(info));
        }
    }

    public record PaymentListResponse(List<PaymentDto> payments) {
        public static PaymentListResponse from(List<PaymentInfo> infos) {
            return new PaymentListResponse(
                infos.stream().map(PaymentDto::from).toList()
            );
        }
    }

    public record PaymentDto(
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
        public static PaymentDto from(PaymentInfo info) {
            return new PaymentDto(
                info.id(), info.orderId(), info.memberId(),
                info.cardType(), info.cardNo(), info.amount(),
                info.status(), info.transactionKey(), info.failReason(),
                info.createdAt(), info.updatedAt()
            );
        }
    }

    public record CallbackRequest(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
    ) {}
}
