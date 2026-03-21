package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayResponse;
import com.loopers.domain.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.loopers.application.payment.PaymentFacade.formatOrderIdForPg;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentRecoveryScheduler {

    private static final int STUCK_THRESHOLD_SECONDS = 120;
    private static final int TIMEOUT_THRESHOLD_SECONDS = 300;
    private static final int BATCH_LIMIT = 10;

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final PaymentFacade paymentFacade;

    @Scheduled(fixedDelay = 30_000)
    public void recoverStuckPayments() {
        recoverStuckProcessing();
        recoverStuckRequested();
        recoverTimedOut();
    }

    private void recoverStuckProcessing() {
        List<Payment> stuckPayments = paymentService.getStuckProcessing(STUCK_THRESHOLD_SECONDS, BATCH_LIMIT);
        for (Payment payment : stuckPayments) {
            try {
                PaymentGatewayResponse pgResponse = paymentGateway.getPaymentStatus(
                    payment.getMemberId(), payment.getTransactionKey()
                );

                if (pgResponse != null && (pgResponse.isSuccess() || pgResponse.isFailed())) {
                    paymentFacade.processCallback(
                        payment.getTransactionKey(), pgResponse.status(), pgResponse.reason()
                    );
                    log.info("PROCESSING 결제 복구 완료. paymentId={}, status={}", payment.getId(), pgResponse.status());
                } else if (isExceededTimeout(payment)) {
                    paymentFacade.timeoutPayment(payment.getId());
                    log.info("PROCESSING 결제 타임아웃 처리. paymentId={}", payment.getId());
                }
            } catch (Exception e) {
                if (isExceededTimeout(payment)) {
                    try {
                        paymentFacade.timeoutPayment(payment.getId());
                        log.info("PROCESSING 결제 타임아웃 처리 (PG 조회 실패). paymentId={}", payment.getId());
                    } catch (Exception te) {
                        log.warn("PROCESSING 결제 타임아웃 처리 실패. paymentId={}, error={}", payment.getId(), te.getMessage());
                    }
                } else {
                    log.warn("PROCESSING 결제 복구 실패. paymentId={}, error={}", payment.getId(), e.getMessage());
                }
            }
        }
    }

    private boolean isExceededTimeout(Payment payment) {
        return payment.getUpdatedAt()
            .plusSeconds(TIMEOUT_THRESHOLD_SECONDS)
            .isBefore(ZonedDateTime.now());
    }

    private void recoverStuckRequested() {
        List<Payment> stuckPayments = paymentService.getStuckRequested(STUCK_THRESHOLD_SECONDS, BATCH_LIMIT);
        for (Payment payment : stuckPayments) {
            try {
                List<PaymentGatewayResponse> pgResponses = paymentGateway.getPaymentsByOrderId(
                    payment.getMemberId(), formatOrderIdForPg(payment.getOrderId())
                );

                if (pgResponses == null || pgResponses.isEmpty()) {
                    paymentFacade.processPaymentResponse(payment.getId(), null);
                    log.info("REQUESTED 결제 실패 처리 (PG 미도달). paymentId={}", payment.getId());
                } else {
                    PaymentGatewayResponse matched = pgResponses.stream()
                        .filter(r -> r.isSuccess() || r.isFailed() || r.isPending())
                        .findFirst()
                        .orElse(null);

                    if (matched != null) {
                        paymentFacade.processPaymentResponse(payment.getId(), matched);
                        log.info("REQUESTED 결제 복구 완료. paymentId={}, pgStatus={}", payment.getId(), matched.status());
                    }
                }
            } catch (Exception e) {
                log.warn("REQUESTED 결제 복구 실패. paymentId={}, error={}", payment.getId(), e.getMessage());
            }
        }
    }

    private void recoverTimedOut() {
        List<Payment> timedOutPayments = paymentService.getTimedOut(BATCH_LIMIT);
        for (Payment payment : timedOutPayments) {
            try {
                if (payment.getTransactionKey() == null) {
                    continue;
                }

                PaymentGatewayResponse pgResponse = paymentGateway.getPaymentStatus(
                    payment.getMemberId(), payment.getTransactionKey()
                );

                if (pgResponse != null && (pgResponse.isSuccess() || pgResponse.isFailed())) {
                    paymentFacade.processCallback(
                        payment.getTransactionKey(), pgResponse.status(), pgResponse.reason()
                    );
                    log.info("TIMEOUT 결제 복구 완료. paymentId={}, status={}", payment.getId(), pgResponse.status());
                }
            } catch (Exception e) {
                log.warn("TIMEOUT 결제 복구 실패. paymentId={}, error={}", payment.getId(), e.getMessage());
            }
        }
    }
}
