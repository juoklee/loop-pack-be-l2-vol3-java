package com.loopers.application.event;

import com.loopers.domain.event.PaymentCompletedEvent;
import com.loopers.domain.event.PaymentFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class PaymentEventListener {

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[이벤트] 결제 완료 - paymentId={}, orderId={}, memberId={}, amount={}",
            event.paymentId(), event.orderId(), event.memberId(), event.amount());
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("[이벤트] 결제 실패 - paymentId={}, orderId={}, memberId={}, reason={}",
            event.paymentId(), event.orderId(), event.memberId(), event.reason());
    }
}
