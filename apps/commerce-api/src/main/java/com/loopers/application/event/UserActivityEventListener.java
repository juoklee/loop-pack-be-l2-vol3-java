package com.loopers.application.event;

import com.loopers.domain.event.ProductViewedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserActivityEventListener {

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductViewed(ProductViewedEvent event) {
        log.info("[이벤트] 상품 조회 - memberId={}, productId={}",
            event.memberId(), event.productId());
    }
}
