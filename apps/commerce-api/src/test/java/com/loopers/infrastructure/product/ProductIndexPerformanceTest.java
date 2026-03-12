package com.loopers.infrastructure.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "bulk-data"})
@DisplayName("상품 인덱스 성능 테스트")
class ProductIndexPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexPerformanceTest.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Map<String, Object> explain(String query) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("EXPLAIN " + query);
        return result.get(0);
    }

    private long measureQueryTime(String query) {
        long start = System.nanoTime();
        jdbcTemplate.queryForList(query);
        return (System.nanoTime() - start) / 1_000_000;
    }

    private void logExplainResult(String label, Map<String, Object> explainResult, long queryTimeMs) {
        log.info("=== {} ===", label);
        log.info("  type: {}", explainResult.get("type"));
        log.info("  key: {}", explainResult.get("key"));
        log.info("  rows: {}", explainResult.get("rows"));
        log.info("  Extra: {}", explainResult.get("Extra"));
        log.info("  실행 시간: {}ms", queryTimeMs);
    }

    private void dropProductIndexes() {
        String[] indexes = {
                "idx_product_brand_like",
                "idx_product_brand_price",
                "idx_product_like_count",
                "idx_product_created_at"
        };
        for (String idx : indexes) {
            try {
                jdbcTemplate.execute("DROP INDEX " + idx + " ON product");
            } catch (Exception ignored) {
            }
        }
    }

    private void createProductIndexes() {
        jdbcTemplate.execute("CREATE INDEX idx_product_brand_like ON product (brand_id, like_count DESC)");
        jdbcTemplate.execute("CREATE INDEX idx_product_brand_price ON product (brand_id, price)");
        jdbcTemplate.execute("CREATE INDEX idx_product_like_count ON product (like_count DESC)");
        jdbcTemplate.execute("CREATE INDEX idx_product_created_at ON product (created_at DESC)");
    }

    @DisplayName("상품 조회 인덱스 성능 비교")
    @Nested
    class ProductQueryIndexComparison {

        static final String Q1_BRAND_LIKES = "SELECT * FROM product WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20";
        static final String Q2_BRAND_PRICE = "SELECT * FROM product WHERE brand_id = 1 AND deleted_at IS NULL ORDER BY price ASC LIMIT 20";
        static final String Q3_GLOBAL_LIKES = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20";
        static final String Q4_GLOBAL_LATEST = "SELECT * FROM product WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20";

        @DisplayName("Q1: 브랜드 필터 + 좋아요순 - 인덱스 전/후 비교")
        @Test
        @Transactional
        void compareQ1_brandFilterWithLikesSort() {
            dropProductIndexes();

            Map<String, Object> beforeExplain = explain(Q1_BRAND_LIKES);
            long beforeTime = measureQueryTime(Q1_BRAND_LIKES);
            logExplainResult("Q1 Before (인덱스 없음)", beforeExplain, beforeTime);

            createProductIndexes();

            Map<String, Object> afterExplain = explain(Q1_BRAND_LIKES);
            long afterTime = measureQueryTime(Q1_BRAND_LIKES);
            logExplainResult("Q1 After (인덱스 적용)", afterExplain, afterTime);

            assertThat(afterExplain.get("key")).isNotNull();
            assertThat(String.valueOf(afterExplain.get("Extra"))).doesNotContain("Using filesort");

            log.info("Q1 성능 개선: {}ms → {}ms", beforeTime, afterTime);
        }

        @DisplayName("Q2: 브랜드 필터 + 가격순 - 인덱스 전/후 비교")
        @Test
        @Transactional
        void compareQ2_brandFilterWithPriceSort() {
            dropProductIndexes();

            Map<String, Object> beforeExplain = explain(Q2_BRAND_PRICE);
            long beforeTime = measureQueryTime(Q2_BRAND_PRICE);
            logExplainResult("Q2 Before (인덱스 없음)", beforeExplain, beforeTime);

            createProductIndexes();

            Map<String, Object> afterExplain = explain(Q2_BRAND_PRICE);
            long afterTime = measureQueryTime(Q2_BRAND_PRICE);
            logExplainResult("Q2 After (인덱스 적용)", afterExplain, afterTime);

            assertThat(afterExplain.get("key")).isNotNull();
            assertThat(String.valueOf(afterExplain.get("Extra"))).doesNotContain("Using filesort");

            log.info("Q2 성능 개선: {}ms → {}ms", beforeTime, afterTime);
        }

        @DisplayName("Q3: 전체 좋아요순 - 인덱스 전/후 비교")
        @Test
        @Transactional
        void compareQ3_globalLikesSort() {
            dropProductIndexes();

            Map<String, Object> beforeExplain = explain(Q3_GLOBAL_LIKES);
            long beforeTime = measureQueryTime(Q3_GLOBAL_LIKES);
            logExplainResult("Q3 Before (인덱스 없음)", beforeExplain, beforeTime);

            createProductIndexes();

            Map<String, Object> afterExplain = explain(Q3_GLOBAL_LIKES);
            long afterTime = measureQueryTime(Q3_GLOBAL_LIKES);
            logExplainResult("Q3 After (인덱스 적용)", afterExplain, afterTime);

            assertThat(afterExplain.get("key")).isNotNull();

            log.info("Q3 성능 개선: {}ms → {}ms", beforeTime, afterTime);
        }

        @DisplayName("Q4: 전체 최신순 - 인덱스 전/후 비교")
        @Test
        @Transactional
        void compareQ4_globalLatestSort() {
            dropProductIndexes();

            Map<String, Object> beforeExplain = explain(Q4_GLOBAL_LATEST);
            long beforeTime = measureQueryTime(Q4_GLOBAL_LATEST);
            logExplainResult("Q4 Before (인덱스 없음)", beforeExplain, beforeTime);

            createProductIndexes();

            Map<String, Object> afterExplain = explain(Q4_GLOBAL_LATEST);
            long afterTime = measureQueryTime(Q4_GLOBAL_LATEST);
            logExplainResult("Q4 After (인덱스 적용)", afterExplain, afterTime);

            assertThat(afterExplain.get("key")).isNotNull();

            log.info("Q4 성능 개선: {}ms → {}ms", beforeTime, afterTime);
        }
    }
}
