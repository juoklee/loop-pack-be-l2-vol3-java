package com.loopers.application.event;

import com.loopers.domain.event.OrderCancelledEvent;
import com.loopers.domain.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class OrderEventListener {

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[이벤트] 주문 생성 - orderId={}, memberId={}, totalAmount={}, itemCount={}",
            event.orderId(), event.memberId(), event.totalAmount(), event.items().size());
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("[이벤트] 주문 취소 - orderId={}, memberId={}",
            event.orderId(), event.memberId());
    }
}
