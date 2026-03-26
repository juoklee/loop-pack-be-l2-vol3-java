package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MetricsAggregatorConcurrencyTest {

    @Autowired private MetricsAggregator metricsAggregator;
    @Autowired private ProductMetricsRepository productMetricsRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("100명이 동시에 같은 상품 좋아요 → likeCount가 정확히 100이어야 한다")
    void concurrentLikeToggled_exactCount() throws InterruptedException {
        // given
        Long productId = 1L;
        int requestCount = 100;

        // ProductMetrics를 미리 생성하여 getOrCreate 경합 방지
        productMetricsRepository.save(ProductMetrics.create(productId));

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when — 100명이 동시에 좋아요
        for (int i = 0; i < requestCount; i++) {
            String eventId = UUID.randomUUID().toString();
            executor.submit(() -> {
                try {
                    metricsAggregator.handleLikeToggled(eventId, productId, true);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        ProductMetrics metrics = productMetricsRepository.findByProductId(productId).orElseThrow();
        assertThat(metrics.getLikeCount()).isEqualTo(requestCount);
        assertThat(successCount.get()).isEqualTo(requestCount);
        assertThat(failCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("50건 주문이 동시에 집계 → orderCount와 salesAmount가 정확해야 한다")
    void concurrentOrderCreated_exactAggregation() throws InterruptedException {
        // given
        Long productId = 1L;
        int orderCount = 50;
        long pricePerItem = 10000L;
        int quantityPerOrder = 2;

        productMetricsRepository.save(ProductMetrics.create(productId));

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(orderCount);
        AtomicInteger successCnt = new AtomicInteger(0);
        AtomicInteger failCnt = new AtomicInteger(0);

        // when — 50건 주문 동시 집계
        for (int i = 0; i < orderCount; i++) {
            String eventId = UUID.randomUUID().toString();
            executor.submit(() -> {
                try {
                    metricsAggregator.handleOrderCreated(eventId,
                        List.of(new MetricsAggregator.OrderItemInfo(productId, pricePerItem, quantityPerOrder)));
                    successCnt.incrementAndGet();
                } catch (Exception e) {
                    failCnt.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        ProductMetrics metrics = productMetricsRepository.findByProductId(productId).orElseThrow();
        assertThat(metrics.getOrderCount()).isEqualTo(orderCount);
        assertThat(metrics.getSalesAmount()).isEqualTo(pricePerItem * quantityPerOrder * orderCount);
        assertThat(successCnt.get()).isEqualTo(orderCount);
        assertThat(failCnt.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("같은 eventId로 중복 처리해도 1번만 집계된다 (멱등)")
    void duplicateEvent_onlyOneCounted() {
        // given
        Long productId = 1L;
        productMetricsRepository.save(ProductMetrics.create(productId));
        String eventId = UUID.randomUUID().toString();

        // when — 같은 eventId로 3번 호출
        metricsAggregator.handleLikeToggled(eventId, productId, true);
        metricsAggregator.handleLikeToggled(eventId, productId, true);
        metricsAggregator.handleLikeToggled(eventId, productId, true);

        // then — 1번만 반영
        ProductMetrics metrics = productMetricsRepository.findByProductId(productId).orElseThrow();
        assertThat(metrics.getLikeCount()).isEqualTo(1);
    }
}
