package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentReader paymentReader;

    public Payment createPayment(Long memberId, Long orderId, String cardType, String cardNo, Long amount) {
        paymentReader.findActiveByOrderId(orderId)
            .ifPresent(p -> {
                throw new CoreException(ErrorType.BAD_REQUEST, "이미 진행 중인 결제가 존재합니다.");
            });
        Payment payment = Payment.create(memberId, orderId, cardType, cardNo, amount);
        return paymentRepository.save(payment);
    }

    public Payment getPayment(Long paymentId) {
        return paymentReader.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."));
    }

    public Payment getPaymentByTransactionKey(String transactionKey) {
        return paymentReader.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."));
    }

    public List<Payment> getPaymentsByOrderId(Long orderId) {
        return paymentReader.findAllByOrderId(orderId);
    }

    public List<Payment> getStuckProcessing(int thresholdSeconds, int limit) {
        return paymentReader.findStuckProcessing(thresholdSeconds, limit);
    }

    public List<Payment> getStuckRequested(int thresholdSeconds, int limit) {
        return paymentReader.findStuckRequested(thresholdSeconds, limit);
    }

    public List<Payment> getTimedOut(int limit) {
        return paymentReader.findTimedOut(limit);
    }
}
