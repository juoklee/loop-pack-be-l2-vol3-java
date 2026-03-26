package com.loopers.application.metrics;

import com.loopers.domain.idempotency.EventHandled;
import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class MetricsAggregator {

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void handleLikeToggled(String eventId, Long productId, boolean liked) {
        if (isDuplicate(eventId)) return;

        ProductMetrics metrics = getOrCreate(productId);

        if (liked) {
            metrics.incrementLikeCount();
        } else {
            metrics.decrementLikeCount();
        }

        markHandled(eventId, "LIKE_TOGGLED");
        log.info("Like 집계 완료: productId={}, liked={}, likeCount={}", productId, liked, metrics.getLikeCount());
    }

    @Transactional
    public void handleOrderCreated(String eventId, List<OrderItemInfo> items) {
        if (isDuplicate(eventId)) return;

        for (OrderItemInfo item : items) {
            ProductMetrics metrics = getOrCreate(item.productId());
            metrics.incrementOrderCount();
            metrics.addSalesAmount(item.price() * item.quantity());
        }

        markHandled(eventId, "ORDER_CREATED");
        log.info("주문 집계 완료: eventId={}, 상품 {}건", eventId, items.size());
    }

    @Transactional
    public void handlePaymentCompleted(String eventId, Long orderId) {
        if (isDuplicate(eventId)) return;

        markHandled(eventId, "PAYMENT_COMPLETED");
        log.info("결제 완료 이벤트 처리: eventId={}, orderId={}", eventId, orderId);
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
