package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayResponse;
import com.loopers.infrastructure.payment.dto.PgOrderResponse;
import com.loopers.infrastructure.payment.dto.PgPaymentRequest;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import com.loopers.infrastructure.payment.dto.PgTransactionDetailResponse;
import com.loopers.support.config.PgClientConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class SimulatorPaymentGateway implements PaymentGateway {

    private final PgPaymentClient pgPaymentClient;
    private final PgClientConfig pgClientConfig;

    @Override
    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "requestPaymentFallback")
    @Retry(name = "pgRetry")
    public PaymentGatewayResponse requestPayment(Long memberId, String orderId,
                                                  String cardType, String cardNo,
                                                  Long amount) {
        PgPaymentRequest request = new PgPaymentRequest(
            orderId, cardType, cardNo, amount, pgClientConfig.getCallbackUrl()
        );

        PgPaymentResponse response = pgPaymentClient.requestPayment(memberId, request);

        return new PaymentGatewayResponse(
            response.transactionKey(),
            orderId,
            response.status(),
            response.reason()
        );
    }

    @Override
    @Retry(name = "pgRetry")
    public PaymentGatewayResponse getPaymentStatus(Long memberId, String transactionKey) {
        PgTransactionDetailResponse response = pgPaymentClient.getTransaction(memberId, transactionKey);

        return new PaymentGatewayResponse(
            response.transactionKey(),
            response.orderId(),
            response.status(),
            response.reason()
        );
    }

    @Override
    @Retry(name = "pgRetry")
    public List<PaymentGatewayResponse> getPaymentsByOrderId(Long memberId, String orderId) {
        PgOrderResponse response = pgPaymentClient.getTransactionsByOrderId(memberId, orderId);

        if (response == null || response.transactions() == null) {
            return Collections.emptyList();
        }

        return response.transactions().stream()
            .map(tx -> new PaymentGatewayResponse(
                tx.transactionKey(),
                response.orderId(),
                tx.status(),
                tx.reason()
            ))
            .toList();
    }

    private PaymentGatewayResponse requestPaymentFallback(Long memberId, String orderId,
                                                           String cardType, String cardNo,
                                                           Long amount, Throwable t) {
        log.warn("PG 결제 요청 실패 (CircuitBreaker fallback). orderId={}, error={}", orderId, t.getMessage());
        return null;
    }
}
