package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeTargetType;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("좋아요 쓰기 성능 비교: 비정규화 vs MV")
class LikeWritePerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(LikeWritePerformanceTest.class);

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("비정규화 방식: likes INSERT + product UPDATE (row lock 경합)")
    @Test
    void denormalization_concurrentLikes() throws InterruptedException {
        // Arrange
        Long brandId = insertBrand();
        Long productId = insertProduct(brandId);
        int threadCount = 50;

        // Warm-up
        runConcurrentLikes(productId, 10, true);
        cleanupLikes();
        resetProductLikeCount(productId);

        // Act - 측정
        long[] results = new long[5];
        for (int i = 0; i < 5; i++) {
            cleanupLikes();
            resetProductLikeCount(productId);
            results[i] = runConcurrentLikes(productId, threadCount, true);
        }

        double avg = average(results);
        double med = median(results);
        log.info("=== 비정규화 방식 (likes INSERT + product UPDATE) ===");
        log.info("동시 요청: {}건, 평균: {}ms, 중앙값: {}ms", threadCount, String.format("%.1f", avg), String.format("%.1f", med));
    }

    @DisplayName("MV 방식: likes INSERT만 (product UPDATE 없음)")
    @Test
    void mv_concurrentLikes() throws InterruptedException {
        // Arrange
        Long brandId = insertBrand();
        Long productId = insertProduct(brandId);
        int threadCount = 50;

        // Warm-up
        runConcurrentLikes(productId, 10, false);
        cleanupLikes();

        // Act - 측정
        long[] results = new long[5];
        for (int i = 0; i < 5; i++) {
            cleanupLikes();
            results[i] = runConcurrentLikes(productId, threadCount, false);
        }

        double avg = average(results);
        double med = median(results);
        log.info("=== MV 방식 (likes INSERT만) ===");
        log.info("동시 요청: {}건, 평균: {}ms, 중앙값: {}ms", threadCount, String.format("%.1f", avg), String.format("%.1f", med));
    }

    @DisplayName("비정규화 vs MV 종합 비교")
    @Test
    void comparison() throws InterruptedException {
        Long brandId = insertBrand();
        Long productId = insertProduct(brandId);
        int threadCount = 50;
        int iterations = 5;

        // Warm-up
        runConcurrentLikes(productId, 10, true);
        cleanupLikes();
        resetProductLikeCount(productId);
        runConcurrentLikes(productId, 10, false);
        cleanupLikes();

        // 비정규화 측정
        long[] denormResults = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            cleanupLikes();
            resetProductLikeCount(productId);
            denormResults[i] = runConcurrentLikes(productId, threadCount, true);
        }

        // MV 측정
        long[] mvResults = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            cleanupLikes();
            mvResults[i] = runConcurrentLikes(productId, threadCount, false);
        }

        double denormAvg = average(denormResults);
        double denormMed = median(denormResults);
        double mvAvg = average(mvResults);
        double mvMed = median(mvResults);
        double improvement = ((denormMed - mvMed) / denormMed) * 100;

        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║     좋아요 쓰기 성능 비교 (동시 {}건)               ║", threadCount);
        log.info("╠══════════════════════════════════════════════════════╣");
        log.info("║  비정규화  │  평균: {}ms  │  중앙값: {}ms  ║",
            String.format("%7.1f", denormAvg), String.format("%7.1f", denormMed));
        log.info("║  MV 방식   │  평균: {}ms  │  중앙값: {}ms  ║",
            String.format("%7.1f", mvAvg), String.format("%7.1f", mvMed));
        log.info("║  개선율    │  {}%                          ║",
            String.format("%5.1f", improvement));
        log.info("╚══════════════════════════════════════════════════════╝");

        // MV 방식이 더 빠르거나 동등해야 함
        assertThat(mvMed).isLessThanOrEqualTo(denormMed * 1.2);
    }

    private long runConcurrentLikes(Long productId, int threadCount, boolean withProductUpdate) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final long memberId = i + 1;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();

                    Like like = Like.create(memberId, LikeTargetType.PRODUCT, productId);
                    likeJpaRepository.save(like);

                    if (withProductUpdate) {
                        productJpaRepository.increaseLikeCount(productId);
                    }

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 무시 (중복 등)
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        long startTime = System.currentTimeMillis();
        start.countDown();
        done.await();
        long elapsed = System.currentTimeMillis() - startTime;

        executor.shutdown();
        return elapsed;
    }

    private Long insertBrand() {
        jdbcTemplate.update("INSERT INTO brand (name, description, like_count, created_at, updated_at) VALUES (?, ?, 0, NOW(), NOW())",
            "TestBrand", "테스트 브랜드");
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long insertProduct(Long brandId) {
        jdbcTemplate.update("INSERT INTO product (brand_id, name, description, price, stock_quantity, max_order_quantity, like_count, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, 0, NOW(), NOW())", brandId, "테스트 상품", "설명", 10000, 100, 5);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void cleanupLikes() {
        jdbcTemplate.update("DELETE FROM likes");
    }

    private void resetProductLikeCount(Long productId) {
        jdbcTemplate.update("UPDATE product SET like_count = 0 WHERE id = ?", productId);
    }

    private double average(long[] values) {
        long sum = 0;
        for (long v : values) sum += v;
        return (double) sum / values.length;
    }

    private double median(long[] values) {
        long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[mid - 1] + sorted[mid]) / 2.0 : sorted[mid];
    }
}
