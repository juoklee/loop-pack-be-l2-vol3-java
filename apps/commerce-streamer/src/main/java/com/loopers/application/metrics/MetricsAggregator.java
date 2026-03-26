package com.loopers.application.metrics;

import com.loopers.domain.idempotency.EventHandled;
import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class MetricsAggregator {

    private static final int MAX_RETRIES = 20;

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;
    private final TransactionTemplate transactionTemplate;

    public void handleLikeToggled(String eventId, Long productId, boolean liked) {
        executeWithRetry(eventId, () -> transactionTemplate.executeWithoutResult(status -> {
            if (isDuplicate(eventId)) return;

            ProductMetrics metrics = getOrCreate(productId);

            if (liked) {
                metrics.incrementLikeCount();
            } else {
                metrics.decrementLikeCount();
            }

            markHandled(eventId, "LIKE_TOGGLED");
            log.info("Like 집계 완료: productId={}, liked={}, likeCount={}", productId, liked, metrics.getLikeCount());
        }));
    }

    public void handleOrderCreated(String eventId, List<OrderItemInfo> items) {
        executeWithRetry(eventId, () -> transactionTemplate.executeWithoutResult(status -> {
            if (isDuplicate(eventId)) return;

            for (OrderItemInfo item : items) {
                ProductMetrics metrics = getOrCreate(item.productId());
                metrics.incrementOrderCount();
                metrics.addSalesAmount(item.price() * item.quantity());
            }

            markHandled(eventId, "ORDER_CREATED");
            log.info("주문 집계 완료: eventId={}, 상품 {}건", eventId, items.size());
        }));
    }

    public void handlePaymentCompleted(String eventId, Long orderId) {
        executeWithRetry(eventId, () -> transactionTemplate.executeWithoutResult(status -> {
            if (isDuplicate(eventId)) return;

            markHandled(eventId, "PAYMENT_COMPLETED");
            log.info("결제 완료 이벤트 처리: eventId={}, orderId={}", eventId, orderId);
        }));
    }

    private void executeWithRetry(String eventId, Runnable action) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                action.run();
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_RETRIES) {
                    log.error("낙관적 락 충돌 최대 재시도 초과: eventId={}", eventId);
                    throw e;
                }
                log.warn("낙관적 락 충돌, 재시도 {}/{}: eventId={}", attempt, MAX_RETRIES, eventId);
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(10, 50 * attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private boolean isDuplicate(String eventId) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            log.info("중복 이벤트 무시: eventId={}", eventId);
            return true;
        }
        return false;
    }

    private ProductMetrics getOrCreate(Long productId) {
        return productMetricsRepository.findByProductId(productId)
            .orElseGet(() -> productMetricsRepository.save(ProductMetrics.create(productId)));
    }

    private void markHandled(String eventId, String eventType) {
        eventHandledRepository.save(EventHandled.of(eventId, eventType));
    }

    public record OrderItemInfo(Long productId, long price, int quantity) {}
}
